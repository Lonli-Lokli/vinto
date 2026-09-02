package game.vinto.client

import game.vinto.engine.projectView
import game.vinto.protocol.ClientMessage
import game.vinto.protocol.EventEntry
import game.vinto.protocol.ProtocolJson
import game.vinto.protocol.RoundResult
import game.vinto.protocol.ServerMessage
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.PlayerIdPayload
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The remote session against a scripted wire.
 *
 * The socket is a pair of channels and every message is built with the real [ProtocolJson]
 * — the same serializer the room's envelope builders use — so what these cases exercise is
 * the session's actual parsing, cursor-keeping, frame-building and reconnect behaviour, with
 * only the transport faked. The harness is [Wire], shared with `RemoteRoomLifecycleTest`;
 * the full server loop gets its turn in the two-client harness next door in `shared/room`.
 */
class RemoteSessionTest {

    @Test
    fun joiningVaultsTheTokenTheMomentItArrives() = runTest {
        val wire = Wire(this)

        wire.deliverJoined(view = null)
        wire.settle()

        assertEquals("tok-1", wire.vault.seatToken(WIRE_CODE), "the token is filed on arrival")
        assertEquals(0, wire.room.seat.value)
        assertEquals(ConnectionState.Connected, wire.room.connection.value)
        assertNotNull(wire.room.lobby.value, "the lobby rides on the join")
        assertNull(wire.room.session.value, "no game yet, so no session")

        val hello = ProtocolJson.decodeFromString(ClientMessage.serializer(), wire.socket.sent.first())
        assertIs<ClientMessage.Join>(hello)
        assertNull(hello.token, "a first join carries no token — the room mints one")

        wire.room.leave()
    }

    @Test
    fun aDealBecomesASessionAndEventsBecomeFrames() = runTest {
        val wire = Wire(this)
        wire.deliverJoined(view = null)
        wire.deliver(ServerMessage.Started(view = wire.dealtView, nextIndex = 0))
        wire.settle()

        val session = assertNotNull(wire.room.session.value, "a deal creates the session")
        assertEquals(wire.dealtView.viewerId, session.playerId)

        // Two bot moves arrive, each with its per-event view: two frames, in order.
        val other = wire.dealtView.players[1].id
        wire.deliver(
            ServerMessage.Events(
                events = listOf(
                    botEntry(0, other, wire.dealtView),
                    botEntry(1, other, wire.dealtView),
                ),
                nextIndex = 2,
                view = wire.dealtView,
            ),
        )
        wire.settle()

        assertEquals(2, session.cursor, "the cursor tracks the log")
        val batch = session.frames.replayCache.last()
        assertEquals(2, batch.size, "one frame per event — this is what the stage animates")
        assertTrue(
            session.events.replayCache.any { it is SessionEvent.BotsPlayed },
            "bot moves are announced as they are locally",
        )

        wire.room.leave()
    }

    @Test
    fun aDispatchResolvesAgainstTheWire() = runTest {
        val wire = Wire(this)
        wire.deliverJoined(view = wire.dealtView)
        wire.settle()
        val session = assertNotNull(wire.room.session.value)

        // Accepted: the echo of our own action coming back in events is the acceptance.
        val action = GameAction.DrawCard(PlayerIdPayload(session.playerId))
        val accepted = async { session.dispatch(action) }
        // `pump`, not `settle`: advancing idle time here would run the virtual clock
        // straight through the dispatch timeout before the wire gets to answer.
        wire.pump()
        wire.deliver(
            ServerMessage.Events(
                events = listOf(
                    EventEntry(0, 0, session.playerId, action, byBot = false, view = wire.dealtView),
                ),
                nextIndex = 1,
                view = wire.dealtView,
            ),
        )
        wire.pump()
        assertNull(accepted.await(), "the echo lands the dispatch")

        // Refused: the error that came instead is the reason handed back.
        val refused = async { session.dispatch(action) }
        wire.pump()
        wire.deliver(ServerMessage.Error("not your turn"))
        wire.pump()
        assertEquals("not your turn", refused.await())

        wire.room.leave()
    }

    @Test
    fun aDroppedSocketRejoinsWithTheTokenAndResyncs() = runTest {
        val second = ScriptedSocket()
        val wire = Wire(this, spareSockets = listOf(second))
        wire.deliverJoined(view = wire.dealtView)
        wire.settle()
        val session = assertNotNull(wire.room.session.value)
        val cursorBefore = session.cursor

        // The socket dies mid-game. The loop backs off, reconnects, and joins as the token.
        wire.socket.fail(RuntimeException("tunnel"))
        wire.settle()

        val rejoin = ProtocolJson.decodeFromString(ClientMessage.serializer(), second.sent.first())
        assertIs<ClientMessage.Join>(rejoin)
        assertEquals("tok-1", rejoin.token, "the vaulted token is what reclaims the seat")

        // The room answers joined; the session survives, so the client asks for what it
        // missed from its cursor.
        second.deliver(wire.joinedJson(view = wire.dealtView))
        wire.settle()
        val resync = ProtocolJson.decodeFromString(ClientMessage.serializer(), second.sent[1])
        assertIs<ClientMessage.Resync>(resync)
        assertEquals(cursorBefore, resync.sinceIndex)

        // And the sync lands the table on the present: cursor jumped, one catch-up frame.
        second.deliver(
            ProtocolJson.encodeToString(
                ServerMessage.serializer(),
                ServerMessage.Sync(events = emptyList(), nextIndex = 41, view = wire.dealtView),
            ),
        )
        wire.settle()
        assertEquals(41, session.cursor)
        assertEquals(1, session.frames.replayCache.last().size, "the catch-up is one frame")

        wire.room.leave()
    }

    @Test
    fun theRoundBoundaryCarriesTheStandings() = runTest {
        val wire = Wire(this)
        wire.deliverJoined(view = wire.dealtView)
        wire.settle()
        val session = assertNotNull(wire.room.session.value)

        val scored = projectView(
            wire.state.copy(phase = GamePhase.SCORING),
            wire.dealtView.viewerId,
        )
        val standings = listOf(
            RoundResult(1, wire.dealtView.players[1].id, mapOf("a" to 5), mapOf("a" to 3)),
        )
        wire.deliver(
            ServerMessage.BetweenRounds(view = scored, standings = standings, nextIndex = 9),
        )
        wire.settle()

        assertEquals(standings, wire.room.standings.value, "the score screen's source")
        assertEquals(GamePhase.SCORING, session.view.value.phase)
        assertTrue(session.isOver)

        wire.room.leave()
    }
}

package game.vinto.client

import game.vinto.engine.PlayerView
import game.vinto.engine.initializeGame
import game.vinto.engine.projectView
import game.vinto.protocol.ClientMessage
import game.vinto.protocol.EventEntry
import game.vinto.protocol.LobbySeat
import game.vinto.protocol.LobbyView
import game.vinto.protocol.ProtocolJson
import game.vinto.protocol.PublicRoom
import game.vinto.protocol.PublicSeat
import game.vinto.protocol.RoomPhase
import game.vinto.protocol.RoundResult
import game.vinto.protocol.ServerMessage
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.PlayerIdPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
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
 * The socket here is a pair of channels and every message is built with the real
 * [ProtocolJson] — the same serializer the room's envelope builders use — so what these
 * cases exercise is the session's actual parsing, cursor-keeping, frame-building and
 * reconnect behaviour, with only the transport faked. The full server loop gets its turn in
 * the two-client harness next door in `shared/room`.
 */
class RemoteSessionTest {

    @Test
    fun joiningVaultsTheTokenTheMomentItArrives() = runTest {
        val wire = Wire(this)

        wire.deliverJoined(view = null)
        wire.settle()

        assertEquals("tok-1", wire.vault.seatToken(CODE), "the token is filed on arrival")
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
                    entry(0, other, wire.dealtView),
                    entry(1, other, wire.dealtView),
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

    // ------------------------------------------------------------------ the scripted wire

    private class ScriptedSocket : RoomSocket {
        val sent = mutableListOf<String>()
        private val channel = Channel<String>(Channel.UNLIMITED)
        override val incoming = channel
        override suspend fun send(text: String) {
            sent += text
        }

        fun deliver(text: String) {
            channel.trySend(text)
        }

        fun fail(cause: Throwable) {
            channel.close(cause)
        }

        override fun close() {
            channel.close()
        }
    }

    private inner class Wire(
        scope: kotlinx.coroutines.test.TestScope,
        spareSockets: List<ScriptedSocket> = emptyList(),
    ) {
        val socket = ScriptedSocket()
        val vault = MemoryVault()
        val state = initializeGame(9L, Difficulty.EASY)
        val dealtView: PlayerView = projectView(state, state.players.first().id)

        private val sockets = ArrayDeque(listOf(socket) + spareSockets)
        private val connector = object : RoomConnector {
            override suspend fun connect(code: String): RoomSocket = sockets.removeFirst()
            override suspend fun createRoom(isPublic: Boolean, hostNickname: String) =
                CreatedRoom(CODE, "room-$CODE")

            /** Nothing in this suite browses; the room it drives is one it was handed. */
            override suspend fun listPublicRooms(): List<PublicRoom> = emptyList()
        }

        private val testScope = scope
        val room = RemoteRoom(
            connector = connector,
            code = CODE,
            vault = vault,
            nickname = "Ann",
            scope = scope,
        )

        fun deliver(message: ServerMessage) =
            socket.deliver(ProtocolJson.encodeToString(ServerMessage.serializer(), message))

        fun deliverJoined(view: PlayerView?) = socket.deliver(joinedJson(view))

        fun joinedJson(view: PlayerView?): String = ProtocolJson.encodeToString(
            ServerMessage.serializer(),
            ServerMessage.Joined(
                seat = 0,
                token = "tok-1",
                seats = List(SEATS) { PublicSeat(index = it, occupied = it == 0) },
                nextIndex = 0,
                lobby = LobbyView(
                    phase = RoomPhase.LOBBY,
                    seats = List(SEATS) {
                        LobbySeat(it, occupied = it == 0, isBot = false, removable = false)
                    },
                    humans = 1,
                ),
                view = view,
            ),
        )

        /** Lets the room's loop drain what the wire delivered. */
        fun settle() = testScope.testScheduler.advanceUntilIdle()

        /** Runs what is ready without advancing the clock — for steps holding a timeout. */
        fun pump() = testScope.testScheduler.runCurrent()
    }

    private fun entry(index: Int, playerId: String, view: PlayerView) = EventEntry(
        index = index,
        seat = 1,
        playerId = playerId,
        action = GameAction.DrawCard(PlayerIdPayload(playerId)),
        byBot = true,
        view = view,
    )

    private companion object {
        const val CODE = "TEST42"
        const val SEATS = 4
    }
}

package game.vinto.client

import game.vinto.engine.projectView
import game.vinto.protocol.ClientMessage
import game.vinto.protocol.EventEntry
import game.vinto.protocol.RoomPhase
import game.vinto.protocol.RoundResult
import game.vinto.protocol.ServerMessage
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.LeaderIdPayload
import game.vinto.shapes.PlayerIdPayload
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The rest of a room's life on the client: the messages that are not a deal or a move, the
 * lobby's verbs, and the ways a dispatch can fail to resolve.
 *
 * `RemoteSessionTest` covers the spine — join, deal, dispatch, reconnect, the round boundary.
 * These are the arms around it, each of which had been running untested: a room that closes
 * must not be reconnected to; a refusal with no move in flight has to land somewhere a screen
 * can hear it; a seat spins while the room is asked and stops when it answers or plainly will
 * not; and a move the phone could not send, or the room never answered, has to come back as a
 * refusal rather than a hang.
 */
class RemoteRoomLifecycleTest {

    // ------------------------------------------------------------------ the room ending

    @Test
    fun aRoomThatClosesIsNotReconnectedTo() = runTest {
        val wire = Wire(this, spareSockets = listOf(ScriptedSocket()))
        wire.deliverJoined(view = null)
        wire.settle()

        wire.deliver(ServerMessage.Closed("the room ended"))
        wire.settle()

        val closed = assertIs<ConnectionState.Closed>(wire.room.connection.value)
        assertEquals("the room ended", closed.reason)
        assertNull(closed.trouble, "a room that ended is not a room worth another go")
        assertEquals(1, wire.connector.asked, "the loop went back for a room that said goodbye")
        assertFalse(lobbyUi(wire.room.lobby.value, closed, 0).canRetry)

        wire.room.retry()
        wire.settle()
        assertEquals(1, wire.connector.asked, "retry is for a failure, not for a room that finished")
    }

    @Test
    fun theSessionEndingLeavesTheScoreboardStanding() = runTest {
        val wire = Wire(this)
        wire.deliverJoined(view = wire.dealtView)
        wire.settle()

        wire.deliver(ServerMessage.Ended("not enough players"))
        wire.settle()

        assertEquals("not enough players", wire.room.ended.value)
        assertEquals(ConnectionState.Connected, wire.room.connection.value, "the room still stands")
        assertNotNull(wire.room.session.value, "and so does the table it is showing")
        wire.room.leave()
    }

    @Test
    fun leavingIsFinal() = runTest {
        val wire = Wire(this, spareSockets = listOf(ScriptedSocket()))
        wire.deliverJoined(view = null)
        wire.settle()

        wire.room.leave()
        wire.settle()

        val closed = assertIs<ConnectionState.Closed>(wire.room.connection.value)
        assertNull(closed.trouble)
        assertEquals(
            "tok-1",
            wire.vault.seatToken(WIRE_CODE),
            "the seat token is kept — the seat is reclaimable until the room dies",
        )
        wire.room.retry()
        wire.settle()
        assertEquals(1, wire.connector.asked, "leaving a room then reconnected to it")
    }

    @Test
    fun retryWhileConnectedDoesNothing() = runTest {
        val wire = Wire(this, spareSockets = listOf(ScriptedSocket()))
        wire.deliverJoined(view = null)
        wire.settle()

        wire.room.retry()
        wire.settle()

        assertEquals(1, wire.connector.asked)
        assertEquals(ConnectionState.Connected, wire.room.connection.value)
        wire.room.leave()
    }

    // ------------------------------------------------------------------ refusals with nobody waiting

    @Test
    fun aRefusalOfALobbyRequestIsANotice() = runTest {
        val wire = Wire(this)
        wire.deliverJoined(view = null)
        wire.settle()
        val heard = mutableListOf<String>()
        val listener = launch { wire.room.notices.collect { heard += it } }
        wire.pump()

        wire.deliver(ServerMessage.Error("only a seated player may add a bot"))
        wire.settle()

        assertEquals(listOf("only a seated player may add a bot"), heard)
        listener.cancel()
        wire.room.leave()
    }

    @Test
    fun aRefusalWithNoMoveInFlightReachesTheSessionsEvents() = runTest {
        val wire = Wire(this)
        wire.deliverJoined(view = wire.dealtView)
        wire.settle()
        val session = assertNotNull(wire.room.session.value)

        wire.deliver(ServerMessage.Error("not your turn"))
        wire.settle()

        assertEquals(SessionEvent.Refused("not your turn"), session.events.replayCache.last())
        wire.room.leave()
    }

    // ------------------------------------------------------------------ the lobby's verbs

    @Test
    fun addingABotSpinsTheSeatTheRoomWillFillUntilTheLobbyAnswers() = runTest {
        val wire = Wire(this)
        wire.deliverJoined(view = null)
        wire.settle()

        wire.room.addBot()
        wire.pump()

        assertEquals(setOf(1), wire.room.pendingSeats.value, "the first free seat is the one the room fills")
        val asked = assertIs<ClientMessage.AddBot>(wire.socket.lastSent().message)
        assertEquals("tok-1", asked.token)

        wire.deliver(ServerMessage.Lobby(wire.lobbyWith(occupied = setOf(0, 1), bots = setOf(1))))
        wire.settle()

        assertTrue(wire.room.pendingSeats.value.isEmpty(), "the lobby is the authority, and it answered")
        assertTrue(wire.room.lobby.value!!.seats[1].isBot)
        wire.room.leave()
    }

    @Test
    fun aSeatStopsSpinningWhenTheRoomNeverAnswers() = runTest {
        val wire = Wire(this)
        wire.deliverJoined(view = null)
        wire.settle()

        wire.room.addBot()
        wire.pump()
        assertEquals(setOf(1), wire.room.pendingSeats.value)

        testScheduler.advanceTimeBy(PENDING_TIMEOUT_MS + 1)
        testScheduler.runCurrent()

        assertTrue(wire.room.pendingSeats.value.isEmpty(), "a seat spun for as long as the room was open")
        wire.room.leave()
    }

    @Test
    fun theOtherLobbyVerbsCarryTheTokenAndSpinTheirSeat() = runTest {
        val wire = Wire(this)
        wire.deliverJoined(view = null)
        wire.settle()

        wire.room.removeBot(2)
        wire.pump()
        assertEquals(setOf(2), wire.room.pendingSeats.value, "the seat being emptied is the one that waits")
        val removal = assertIs<ClientMessage.RemoveBot>(wire.socket.lastSent().message)
        assertEquals(2, removal.seat)
        assertEquals("tok-1", removal.token)

        wire.room.nextRound()
        wire.pump()
        assertEquals("tok-1", assertIs<ClientMessage.NextRound>(wire.socket.lastSent().message).token)

        wire.room.moreTime()
        wire.pump()
        assertEquals("tok-1", assertIs<ClientMessage.MoreTime>(wire.socket.lastSent().message).token)
        wire.room.leave()
    }

    // ------------------------------------------------------------------ deals

    @Test
    fun aNewDealIsANewSessionAndTheStandingsRideWithIt() = runTest {
        val wire = Wire(this)
        wire.deliverJoined(view = wire.dealtView)
        wire.settle()
        val first = assertNotNull(wire.room.session.value)
        val standings = listOf(RoundResult(1, wire.dealtView.viewerId, mapOf("a" to 4), mapOf("a" to 3)))

        wire.deliver(ServerMessage.Started(view = wire.dealtView, nextIndex = 40, standings = standings))
        wire.settle()

        val second = assertNotNull(wire.room.session.value)
        assertNotSame(first, second, "a new round is a new session, so screens can key on the instance")
        assertEquals(40, second.cursor, "the cursor starts where the room's log stands")
        assertEquals(standings, wire.room.standings.value)

        // A `started` with nothing to see leaves the table alone.
        wire.deliver(ServerMessage.Started(view = null, nextIndex = 41))
        wire.settle()
        assertSame(second, wire.room.session.value)
        wire.room.leave()
    }

    // ------------------------------------------------------------------ dispatches that do not resolve

    @Test
    fun aMoveThatCannotLeaveThePhoneIsARefusalNotACrash() = runTest {
        val wire = Wire(this)
        wire.deliverJoined(view = wire.dealtView)
        wire.settle()
        val session = assertNotNull(wire.room.session.value)
        val action = GameAction.DrawCard(PlayerIdPayload(session.playerId))

        wire.socket.sendFails = IllegalStateException("socket closed")
        val reason = assertNotNull(session.dispatch(action), "a move that never left was reported as accepted")
        assertFalse("socket closed" in reason, "plumbing shown to the player: $reason")

        // And the session is not stuck on it: the next move resolves against the wire as usual.
        wire.socket.sendFails = null
        val next = async { session.dispatch(action) }
        wire.pump()
        wire.deliver(
            ServerMessage.Events(
                events = listOf(EventEntry(0, 0, session.playerId, action, byBot = false, view = wire.dealtView)),
                nextIndex = 1,
                view = wire.dealtView,
            ),
        )
        wire.pump()
        assertNull(next.await())
        wire.room.leave()
    }

    @Test
    fun aRoomThatNeverAnswersAMoveGivesUpOnIt() = runTest {
        val wire = Wire(this)
        wire.deliverJoined(view = wire.dealtView)
        wire.settle()
        val session = assertNotNull(wire.room.session.value)
        val action = GameAction.DrawCard(PlayerIdPayload(session.playerId))

        val waiting = async { session.dispatch(action) }
        wire.pump()
        assertFalse(waiting.isCompleted, "nothing has answered yet")

        testScheduler.advanceTimeBy(DISPATCH_TIMEOUT_MS + 1)
        testScheduler.runCurrent()

        assertTrue(waiting.isCompleted, "the dispatch is still waiting on a room that will not answer")
        assertNotNull(waiting.await(), "a move nobody answered was reported as accepted")

        // A late echo is harmless: nothing is waiting, so it is simply a frame.
        wire.deliver(
            ServerMessage.Events(
                events = listOf(EventEntry(0, 0, session.playerId, action, byBot = false, view = wire.dealtView)),
                nextIndex = 1,
                view = wire.dealtView,
            ),
        )
        wire.settle()
        assertEquals(1, session.cursor)
        wire.room.leave()
    }

    @Test
    fun anActorlessEchoLandsTheDispatch() = runTest {
        val wire = Wire(this)
        wire.deliverJoined(view = wire.dealtView)
        wire.settle()
        val session = assertNotNull(wire.room.session.value)
        val other = wire.dealtView.players[1].id
        val choice = GameAction.SetCoalitionLeader(LeaderIdPayload(other))

        val pending = async { session.dispatch(choice) }
        wire.pump()
        // Choosing a leader names nobody, so the room logs it with no player; the action
        // alone is the match.
        wire.deliver(
            ServerMessage.Events(
                events = listOf(EventEntry(0, -1, "", choice, byBot = false, view = wire.dealtView)),
                nextIndex = 1,
                view = wire.dealtView,
            ),
        )
        wire.pump()

        assertNull(pending.await())
        wire.room.leave()
    }

    // ------------------------------------------------------------------ frames

    @Test
    fun aCatchUpWithoutViewsLandsInOneFrameAndStaleEntriesAreIgnored() = runTest {
        val wire = Wire(this)
        wire.deliverJoined(view = wire.dealtView)
        wire.settle()
        val session = assertNotNull(wire.room.session.value)
        val other = wire.dealtView.players[1].id
        val batches = mutableListOf<List<Frame>>()
        val collector = launch { session.frames.collect { batches += it } }
        wire.pump()

        // Stored entries with no view — the room keeps no past states — collapse to a landing.
        wire.deliver(
            ServerMessage.Events(
                events = listOf(botEntry(0, other, view = null), botEntry(1, other, view = null)),
                nextIndex = 2,
                view = wire.dealtView,
            ),
        )
        wire.settle()
        assertEquals(2, session.cursor)
        val landing = batches.last()
        assertEquals(1, landing.size, "a catch-up is one frame, not a replay of what the tunnel ate")
        assertIs<GameAction.Empty>(landing.single().action)
        assertTrue(landing.single().scenes.isEmpty(), "a landing narrates no journey")
        val seen = batches.size

        // The same entries again, below the cursor, with nowhere to land: nothing happens.
        wire.deliver(
            ServerMessage.Events(
                events = listOf(botEntry(0, other, wire.dealtView), botEntry(1, other, wire.dealtView)),
                nextIndex = 2,
                view = null,
            ),
        )
        wire.settle()
        assertEquals(2, session.cursor)
        assertEquals(seen, batches.size, "a duplicate delivery was animated again")

        collector.cancel()
        wire.room.leave()
    }

    @Test
    fun reachingScoringAnnouncesTheRoundOnce() = runTest {
        val wire = Wire(this)
        wire.deliverJoined(view = wire.dealtView)
        wire.settle()
        val session = assertNotNull(wire.room.session.value)
        val heard = mutableListOf<SessionEvent>()
        val collector = launch { session.events.collect { heard += it } }
        wire.pump()
        val scored = projectView(wire.state.copy(phase = GamePhase.SCORING), wire.dealtView.viewerId)

        wire.deliver(ServerMessage.Events(events = emptyList(), nextIndex = 0, view = scored))
        wire.settle()
        assertTrue(session.isOver)
        assertEquals(1, heard.count { it is SessionEvent.RoundEnded }, "the end was announced ${heard.size} times")

        // Landing on the scored table again — a sync after a reconnect — is not a second end.
        wire.deliver(ServerMessage.Sync(events = emptyList(), nextIndex = 3, view = scored))
        wire.settle()
        assertEquals(1, heard.count { it is SessionEvent.RoundEnded })
        assertEquals(3, session.cursor)

        collector.cancel()
        wire.room.leave()
    }

    // ------------------------------------------------------------------ compatibility

    @Test
    fun aMessageThisBuildDoesNotKnowIsSkipped() = runTest {
        val wire = Wire(this)
        wire.deliverJoined(view = null)
        wire.settle()

        wire.socket.deliver("""{"type":"hologram","payload":{"depth":3}}""")
        wire.deliver(ServerMessage.Lobby(wire.lobbyWith(occupied = setOf(0, 1), phase = RoomPhase.LOBBY)))
        wire.settle()

        assertEquals(
            ConnectionState.Connected,
            wire.room.connection.value,
            "a newer room's message ended the connection",
        )
        assertEquals(2, wire.room.lobby.value?.humans, "the message after it was lost")
        wire.room.leave()
    }

    private companion object {
        /** `RemoteRoom`'s own numbers, private there; pinned here so a drift fails a test. */
        const val PENDING_TIMEOUT_MS = 5_000L
        const val DISPATCH_TIMEOUT_MS = 10_000L
    }
}

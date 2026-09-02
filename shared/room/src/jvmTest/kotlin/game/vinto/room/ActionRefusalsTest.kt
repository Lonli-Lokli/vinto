package game.vinto.room

import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every way an action is turned away, in the order the room checks them — and the two
 * lookups the socket layer leans on to know who it is talking to.
 *
 * The order is the point of the first four cases. A stranger is refused before anything is
 * charged; a charge lands before the action is even parsed, so a flood of garbage costs the
 * flooder as much as a flood of moves; and only then does the engine get asked. Each refusal
 * is asserted by its wording because the wording is what a client shows.
 */
class ActionRefusalsTest {

    @Test
    fun aTokenNoSeatHoldsCannotActAndIsNotCharged() {
        val state = dealtRoom()
        val annId = decodeRoom(state).seats[0].playerId!!
        val move = actionJson(GameAction.DrawCard(PlayerIdPayload(annId)))

        val refused = decodeAction(applyAction(state, STRANGER, move, LATER))

        assertEquals("no seat holds that token", refused.error)
        assertTrue(refused.events.isEmpty())
        assertTrue(refused.state.buckets.isEmpty(), "a stranger was given a budget")

        // The envelope form says the same and sends nothing: the refusal is the sender's alone.
        val envelopes = decodeEnvelopes(applyActionEnvelopes(state, STRANGER, move, LATER))
        assertEquals("no seat holds that token", envelopes.error)
        assertTrue(envelopes.messages.isEmpty())
        assertNull(envelopes.retryAfterMs)
    }

    @Test
    fun aLobbyRefusesGameActionsRatherThanDealingOneOnDemand() {
        val lobby = lobbyOfTwo()
        val move = actionJson(GameAction.DrawCard(PlayerIdPayload("p1")))

        val refused = decodeAction(applyAction(lobby, TOKEN_A, move, START))

        assertEquals("the game has not started", refused.error)
        assertNull(refused.state.game, "the countdown was advisory")
        assertNotNull(refused.state.buckets[0], "the charge lands before the game is looked for")
    }

    @Test
    fun anUnreadableActionIsRefusedNotCrashed() {
        val state = dealtRoom()

        listOf(
            """{"type":"NOT_A_THING","payload":{}}""",
            "not json at all",
            """{"payload":{"playerId":"p1"}}""",
        ).forEach { garbage ->
            val refused = decodeAction(applyAction(state, TOKEN_A, garbage, LATER))
            val error = assertNotNull(refused.error, "accepted: $garbage")
            assertTrue(error.startsWith("unreadable action"), "wrong refusal for $garbage: $error")
            assertTrue(refused.events.isEmpty())
        }
    }

    @Test
    fun aMoveTheRulesForbidIsRefusedWithTheValidatorsReason() {
        val state = dealtRoom()
        val room = decodeRoom(state)
        val annId = room.seats[0].playerId!!

        // Drawing during setup: legal for nobody, and the validator says why.
        val move = actionJson(GameAction.DrawCard(PlayerIdPayload(annId)))
        val refused = decodeAction(applyAction(state, TOKEN_A, move, LATER))

        assertNotNull(refused.error)
        assertTrue(refused.events.isEmpty())
        assertEquals(room.log.size, refused.state.log.size, "a refused move reached the log")
        assertEquals(room.game, refused.state.game, "a refused move changed the game")
    }

    /**
     * The budget refills with the clock, not with a tick: the object sleeps between messages
     * and the only clock it has is the one that arrives with the next one.
     */
    @Test
    fun theBudgetRefillsWithTime() {
        var state = dealtRoom()
        val annId = decodeRoom(state).seats[0].playerId!!
        val move = actionJson(GameAction.PeekSetupCard(PositionPayload(annId, 0)))

        repeat(10) {
            state = encode(decodeAction(applyAction(state, TOKEN_A, move, LATER)).state)
        }
        val spent = decodeAction(applyAction(state, TOKEN_A, move, LATER))
        assertEquals("too many actions", spent.error)
        assertEquals(1_000.0, spent.retryAfterMs, "an empty bucket earns one action a second")

        val tooSoon = decodeAction(applyAction(encode(spent.state), TOKEN_A, move, LATER + 400))
        assertEquals("too many actions", tooSoon.error)
        assertEquals(600.0, tooSoon.retryAfterMs, "the wait is what is left, not a fresh second")

        val refilled = decodeAction(applyAction(encode(tooSoon.state), TOKEN_A, move, LATER + 1_000))
        assertNull(refilled.retryAfterMs, "a second later, one action is affordable again")
    }

    // ------------------------------------------------------------------ who is who

    @Test
    fun eachSeatResolvesFromItsOwnTokenAndAStrangerFromNone() {
        val state = dealtRoom()

        assertEquals(0, seatForToken(state, TOKEN_A))
        assertEquals(1, seatForToken(state, TOKEN_B))
        assertEquals(-1, seatForToken(state, STRANGER))
        assertEquals(-1, seatForToken(state, ""), "an empty token holds no seat")
    }

    @Test
    fun aViewNeedsASeatWithAPlayerBehindIt() {
        assertEquals("unknown seat 9", decodeView(viewForSeat(dealtRoom(), 9, LATER)).error)
        assertEquals("the game has not started", decodeView(viewForSeat(lobbyOfTwo(), 0, START)).error)

        val dealt = decodeRoom(dealtRoom())
        val unmapped = dealt.copy(seats = dealt.seats.map { if (it.index == 3) it.copy(playerId = null) else it })
        assertEquals("seat 3 has no player yet", decodeView(viewForSeat(encode(unmapped), 3, LATER)).error)
    }

    /** The session clock rides on the view as a duration, because a phone's own clock may be wrong. */
    @Test
    fun theViewCarriesWhatIsLeftOfTheSession() {
        val state = dealtRoom()
        val buzzer = decodeRoom(state).session.endsAtEpochMs!!

        val view = assertNotNull(decodeView(viewForSeat(state, 1, LATER)).view)
        assertEquals(decodeRoom(state).seats[1].playerId, view.viewerId)
        assertEquals((buzzer - LATER).toLong(), view.sessionMsRemaining)

        val late = assertNotNull(decodeView(viewForSeat(state, 1, buzzer + 5_000)).view)
        assertEquals(0L, late.sessionMsRemaining, "a clock past its end reads zero, never negative")

        assertNull(decodeView(viewForSeat(lobbyOfTwo(), 0, START)).view)
    }
}

package game.vinto.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Who won, said from the side of whoever is reading it.
 *
 * Reported 2026-09-16: *"we must clearly show and write who won and why after coalition last
 * turn (without opening scores)"*. A round ended on a chime and a button called "See the
 * score", so the answer lived only inside a table of numbers. This is the model half of
 * telling somebody instead — the fact, both sides' points, and the one thing a sign can be
 * drawn from.
 */
class VerdictTest {

    private val seats = listOf(
        "human-1" to "You",
        "bot-1" to "Ember",
        "bot-2" to "Tide",
        "bot-3" to "Dune",
    )

    /** The reported round: the caller out on 0, the others holding 36, 2 and 24. */
    private fun round(callerId: String?, hands: Map<String, Int>, points: Map<String, Int>) =
        RoundResult(callerId = callerId, hands = hands, points = points, seats = seats)

    @Test
    fun aCoalitionMemberWhoseOwnHandWasHopelessStillWonWithTheCoalition() {
        val result = round(
            callerId = "human-1",
            hands = mapOf("human-1" to 12, "bot-1" to 36, "bot-2" to 2, "bot-3" to 24),
            points = mapOf("human-1" to -1, "bot-1" to 3, "bot-2" to 3, "bot-3" to 3),
        )

        // Ember held 36 and had nothing to do with it; the coalition beat the call, so Ember won.
        assertEquals(true, verdictFor(result, "bot-1").viewerWon, "a coalition member was told it lost")
        assertEquals(false, verdictFor(result, "human-1").viewerWon, "the beaten caller was told it won")

        val mine = verdictFor(result, "bot-1")
        assertEquals(-1, mine.callerPoints)
        assertEquals(3, mine.coalitionPoints)
        assertTrue(mine.outcome is RoundOutcome.CoalitionWon)
    }

    @Test
    fun theCallerWinsAndTheOthersAreToldTheyLost() {
        val result = round(
            callerId = "human-1",
            hands = mapOf("human-1" to 0, "bot-1" to 36, "bot-2" to 2, "bot-3" to 24),
            points = mapOf("human-1" to 3, "bot-1" to -1, "bot-2" to -1, "bot-3" to -1),
        )

        assertEquals(true, verdictFor(result, "human-1").viewerWon)
        for (seat in listOf("bot-1", "bot-2", "bot-3")) {
            assertEquals(false, verdictFor(result, seat).viewerWon, "$seat was told it won")
        }
        assertEquals(3, verdictFor(result, "human-1").callerPoints)
        assertEquals(-1, verdictFor(result, "human-1").coalitionPoints)
    }

    /** Level is the call holding: it pays the caller, so the caller's side is the one that won. */
    @Test
    fun aLevelRoundGoesToTheCaller() {
        val result = round(
            callerId = "human-1",
            hands = mapOf("human-1" to 2, "bot-1" to 36, "bot-2" to 2, "bot-3" to 24),
            points = mapOf("human-1" to 2, "bot-1" to 0, "bot-2" to 0, "bot-3" to 0),
        )

        val caller = verdictFor(result, "human-1")
        assertTrue(caller.outcome is RoundOutcome.Level)
        assertEquals(true, caller.viewerWon, "the call held and the caller was told it lost")
        assertEquals(2, caller.callerPoints, "a level round pays the caller two, not three")
        assertEquals(false, verdictFor(result, "bot-2").viewerWon)
    }

    @Test
    fun nobodyWinsARoundNobodyCalled() {
        val result = round(
            callerId = null,
            hands = mapOf("human-1" to 7, "bot-1" to 9, "bot-2" to 2, "bot-3" to 4),
            points = seats.associate { it.first to 0 },
        )

        assertNull(verdictFor(result, "human-1").viewerWon, "somebody won a round nobody called")
        assertTrue(verdictFor(result, "human-1").outcome is RoundOutcome.DeckRanOut)
    }

    @Test
    fun awatcherIsOnNobodysSide() {
        val result = round(
            callerId = "human-1",
            hands = mapOf("human-1" to 0, "bot-1" to 36, "bot-2" to 2, "bot-3" to 24),
            points = mapOf("human-1" to 3, "bot-1" to -1, "bot-2" to -1, "bot-3" to -1),
        )

        assertNull(verdictFor(result, "nobody").viewerWon, "a watcher was given a side")
    }
}

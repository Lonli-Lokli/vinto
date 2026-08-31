package game.vinto.client

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which side won, and whose hand decided it.
 *
 * The score sheet used to open with "Round 3" and a column of `+3` and `−1`, leaving the
 * player to derive the result from the arithmetic at the exact moment they wanted the answer.
 * This is what the sheet says instead — and it is a type rather than a sentence, so the words
 * stay the UI's business and the *result* can be checked without asserting on English.
 *
 * The tie is the case worth having a test for. It is the one the rules treat asymmetrically —
 * the caller still takes +3 and the others take **nothing** rather than losing one — and the
 * one an earlier version of the lesson's copy got wrong in the other direction.
 */
class RoundOutcomeTest {

    private val caller = "p1"
    private val hands = mapOf(caller to 12, "p2" to 20, "p3" to 15, "p4" to 30)

    @Test
    fun theCallerUnderTheBestOfTheOthersHasHeldTheCall() {
        assertEquals(RoundOutcome.CallerWon(caller = 12, best = 15), outcomeOf(hands, caller))
    }

    @Test
    fun levelIsItsOwnAnswerRatherThanAWin() {
        val level = hands + ("p3" to 12)
        assertEquals(RoundOutcome.Level(caller = 12, best = 12), outcomeOf(level, caller))

        // And it pays what the rules say it pays, which is not what a win pays.
        val points = roundPoints(level, caller)
        assertEquals(3, points[caller], "the caller still takes +3 on a tie")
        assertEquals(0, points["p3"], "and the others take nothing, rather than losing one")
    }

    @Test
    fun somebodyUnderTheCallerBeatsTheCall() {
        val beaten = hands + ("p3" to 4)
        assertEquals(RoundOutcome.CoalitionWon(caller = 12, best = 4), outcomeOf(beaten, caller))
    }

    /** A round nobody called can only have ended on the deck, and pays nothing. */
    @Test
    fun aRoundNobodyCalledIsNotAContest() {
        assertEquals(RoundOutcome.DeckRanOut, outcomeOf(hands, callerId = null))
        assertEquals(emptySet(), bestCoalitionHands(hands, callerId = null))
    }

    /**
     * The row the sheet rings: the hand everything else was measured against.
     *
     * A set, because two players can tie on the lowest and marking one of them would be
     * picking a winner the rules do not pick.
     */
    @Test
    fun theDecidingHandIsMarkedAndATieMarksBoth() {
        assertEquals(setOf("p3"), bestCoalitionHands(hands, caller))
        assertEquals(setOf("p2", "p3"), bestCoalitionHands(hands + ("p2" to 15), caller))
        assertEquals(
            setOf("p3"),
            bestCoalitionHands(hands + (caller to 1), caller),
            "the caller's own hand is never the coalition's best, however low it is",
        )
    }
}

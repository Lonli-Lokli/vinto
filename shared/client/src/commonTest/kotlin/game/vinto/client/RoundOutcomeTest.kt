package game.vinto.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
        assertEquals(2, points[caller], "a tie pays the caller 2, where a win pays 3")
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

    /**
     * The totals every outcome carries, and the reason this lives here rather than at the screen.
     *
     * The score sheet used to pick these apart with its own `when` over [RoundOutcome]. In
     * `composeApp` — a different module from the sealed type — an exhaustive `when` whose
     * branches read `caller`/`best` off the smart cast MATCHES NOTHING on Kotlin/Native, so iOS
     * threw `NoWhenBranchMatchedException` at the end of every round. A `when` over the same
     * value whose branches do not touch the cast matched perfectly, three lines above it.
     *
     * This case runs on the JVM, on JS and on the iOS simulator, which is what makes it evidence
     * rather than an assertion: the same `when` compiled beside its type is fine everywhere.
     */
    @Test
    fun everyOutcomeReportsTheTotalsItWasDecidedOn() {
        assertEquals(12 to 15, RoundOutcome.CallerWon(caller = 12, best = 15).totals())
        assertEquals(12 to 12, RoundOutcome.Level(caller = 12, best = 12).totals())
        assertEquals(12 to 4, RoundOutcome.CoalitionWon(caller = 12, best = 4).totals())

        // The one outcome with nothing to compare: nobody called, so there is no pair.
        assertNull(RoundOutcome.DeckRanOut.totals())
    }

    /** And it agrees with [outcomeOf], so the sheet's two lines cannot disagree. */
    @Test
    fun theTotalsMatchTheOutcomeTheyCameFrom() {
        val hands = mapOf("me" to 5, "a" to 16, "b" to 16)
        val totals = outcomeOf(hands, callerId = "me").totals()
        assertEquals(5 to 16, totals, "the sheet would have printed a different pair from the verdict")
    }
}

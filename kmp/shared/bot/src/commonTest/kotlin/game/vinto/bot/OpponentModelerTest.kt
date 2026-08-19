package game.vinto.bot

import game.vinto.shapes.Rank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ported from `packages/bot/src/lib/__tests__/opponent-modeler.test.ts`.
 *
 * These inferences replaced reading opponents' hands outright, so they are the difference
 * between a bot that plays well and one that cheats.
 */
class OpponentModelerTest {

    // --- initialization -------------------------------------------------------------------

    @Test
    fun aNewPlayerStartsWithNoBeliefsAndAnAverageEstimate() {
        val modeler = OpponentModeler()
        modeler.initializePlayer("p2")

        val beliefs = assertNotNull(modeler.getPlayerBeliefs("p2"))
        assertEquals("p2", beliefs.playerId)
        // Literal rather than referencing the constants, which are private to the
        // implementation — a test that reads them would agree with any value they took.
        assertEquals(25, beliefs.estimatedScore, "a fresh opponent should look average")
        assertEquals(0.0, beliefs.vintoReadiness, "a fresh opponent should look like nobody in particular")
        assertTrue(beliefs.cardBeliefs.isEmpty())
    }

    @Test
    fun initializingTwiceDoesNotThrowAwayWhatWasLearned() {
        val modeler = OpponentModeler()
        modeler.handleObservedAction(
            ObservedAction.TossIn("p2", testCard(Rank.FIVE, "5_0"), position = 0),
        )
        val learned = assertNotNull(modeler.getPlayerBeliefs("p2"))

        modeler.initializePlayer("p2")

        assertEquals(learned, modeler.getPlayerBeliefs("p2"), "a second initialize wiped the beliefs")
    }

    @Test
    fun aPlayerIsTrackedFromTheirFirstObservedAction() {
        val modeler = OpponentModeler()
        assertNull(modeler.getPlayerBeliefs("p9"))

        modeler.handleObservedAction(ObservedAction.PeekOwn("p9"))

        assertNotNull(modeler.getPlayerBeliefs("p9"))
    }

    // --- inference from what was played ----------------------------------------------------

    @Test
    fun swappingInAnActionCardImpliesTheReplacedCardWasWorseThanTen() {
        val modeler = OpponentModeler()
        modeler.handleObservedAction(
            ObservedAction.SwapFromDiscard("p2", testCard(Rank.QUEEN, "Q_0"), position = 0),
        )

        // A Queen is worth 10, so taking one only makes sense over something worth 11 or more.
        assertEquals(11, modeler.getBelief("p2", 0)?.minValue)
    }

    @Test
    fun anEstimateNeverFallsBelowZero() {
        val modeler = OpponentModeler()
        // Enough discards to drive the estimate past zero if nothing stopped it.
        repeat(40) {
            modeler.handleObservedAction(
                ObservedAction.DiscardDrawn("p2", testCard(Rank.SIX, "6_$it")),
            )
        }

        assertTrue(
            (modeler.getPlayerBeliefs("p2")?.estimatedScore ?: -1) >= 0,
            "the estimated score went negative",
        )
    }

    @Test
    fun aSwapActionLooksReadierThanAPeekWhicheverSwapItIs() {
        fun readinessAfter(action: ObservedAction): Double {
            val modeler = OpponentModeler()
            modeler.handleObservedAction(action)
            return modeler.getPlayerBeliefs("p2")?.vintoReadiness ?: 0.0
        }

        val peek = readinessAfter(ObservedAction.UseAction("p2", testCard(Rank.SEVEN, "7_0")))
        val jack = readinessAfter(ObservedAction.UseAction("p2", testCard(Rank.JACK, "J_0")))
        val queen = readinessAfter(ObservedAction.UseAction("p2", testCard(Rank.QUEEN, "Q_0")))

        // Peeking is looking for information; swapping is acting on it.
        assertTrue(jack > peek, "a Jack did not read as readier than a peek")
        assertTrue(queen > peek, "a Queen did not read as readier than a peek")
    }

    @Test
    fun swappingYourOwnCardsLooksReadierThanPeekingThem() {
        fun readinessAfter(action: ObservedAction): Double {
            val modeler = OpponentModeler()
            modeler.handleObservedAction(action)
            return modeler.getPlayerBeliefs("p2")?.vintoReadiness ?: 0.0
        }

        assertTrue(
            readinessAfter(ObservedAction.SwapOwn("p2")) >
                readinessAfter(ObservedAction.PeekOwn("p2")),
        )
    }

    @Test
    fun aPlayerBelievedToHaveALowScoreLooksReadierThanOneWithAHighScore() {
        fun readinessWithEstimate(discards: Int): Double {
            val modeler = OpponentModeler()
            // Each discarded draw lowers the estimate, which is what drives readiness.
            repeat(discards) {
                modeler.handleObservedAction(
                    ObservedAction.DiscardDrawn("p2", testCard(Rank.SIX, "6_$it")),
                )
            }
            modeler.handleObservedAction(ObservedAction.SwapOwn("p2"))
            return modeler.getPlayerBeliefs("p2")?.vintoReadiness ?: 0.0
        }

        assertTrue(
            readinessWithEstimate(discards = 8) > readinessWithEstimate(discards = 0),
            "a player believed to be doing well did not look closer to calling Vinto",
        )
    }

    @Test
    fun nobodyIsTheMostLikelyCallerWhenNobodyIsTracked() {
        assertNull(OpponentModeler().getMostLikelyVintoCaller())
    }

    @Test
    fun swapFromDiscardBoundsTheReplacedCardFromBelow() {
        val modeler = OpponentModeler()
        // Taking a 7 and swapping it in only makes sense if what it replaced was worse than 7.
        modeler.handleObservedAction(
            ObservedAction.SwapFromDiscard("p2", testCard(Rank.SEVEN, "7_0"), position = 2),
        )

        val belief = modeler.getBelief("p2", 2)
        assertNotNull(belief)
        assertEquals(8, belief.minValue)
        assertTrue(belief.confidence >= 0.8)
    }

    @Test
    fun aTossInRevealsARankOutright() {
        val modeler = OpponentModeler()
        modeler.handleObservedAction(
            ObservedAction.TossIn("p2", testCard(Rank.QUEEN, "Q_1"), position = 1),
        )

        val belief = modeler.getBelief("p2", 1)
        assertNotNull(belief)
        assertEquals(listOf(Rank.QUEEN), belief.likelyRanks)
        assertEquals(1.0, belief.confidence)
    }

    @Test
    fun discardingADrawnCardLowersTheEstimate() {
        val modeler = OpponentModeler()
        modeler.initializePlayer("p2")
        val before = modeler.getPlayerBeliefs("p2")!!.estimatedScore

        modeler.handleObservedAction(ObservedAction.DiscardDrawn("p2", testCard(Rank.TEN, "10_0")))

        assertTrue(modeler.getPlayerBeliefs("p2")!!.estimatedScore < before)
    }

    @Test
    fun peekingLooksLessReadyThanSwapping() {
        val peeker = OpponentModeler()
        peeker.handleObservedAction(ObservedAction.UseAction("p2", testCard(Rank.NINE, "9_0")))

        val swapper = OpponentModeler()
        swapper.handleObservedAction(ObservedAction.UseAction("p3", testCard(Rank.JACK, "J_0")))

        assertTrue(
            swapper.getPlayerBeliefs("p3")!!.vintoReadiness >
                peeker.getPlayerBeliefs("p2")!!.vintoReadiness,
            "a player tidying their hand should look closer to calling than one still looking",
        )
    }

    @Test
    fun readinessStaysWithinBounds() {
        val modeler = OpponentModeler()
        repeat(50) { modeler.handleObservedAction(ObservedAction.SwapOwn("p2")) }
        val high = modeler.getPlayerBeliefs("p2")!!.vintoReadiness
        assertTrue(high in 0.0..1.0, "readiness escaped 0..1: $high")

        repeat(50) { modeler.handleObservedAction(ObservedAction.PeekOwn("p2")) }
        val low = modeler.getPlayerBeliefs("p2")!!.vintoReadiness
        assertTrue(low in 0.0..1.0, "readiness escaped 0..1: $low")
    }

    @Test
    fun namesTheMostLikelyCaller() {
        val modeler = OpponentModeler()
        modeler.handleObservedAction(ObservedAction.PeekOwn("p2"))
        repeat(5) { modeler.handleObservedAction(ObservedAction.SwapOwn("p3")) }

        assertEquals("p3", modeler.getMostLikelyVintoCaller())
    }

    @Test
    fun beliefsFollowTheCardsWhenAHandShrinks() {
        val modeler = OpponentModeler()
        modeler.handleObservedAction(
            ObservedAction.TossIn("p2", testCard(Rank.QUEEN, "Q_1"), position = 3),
        )
        modeler.handleObservedAction(
            ObservedAction.SwapFromDiscard("p2", testCard(Rank.SEVEN, "7_0"), position = 1),
        )

        // Position 2 is gone: what was at 3 is now at 2, and what was at 1 has not moved.
        modeler.shiftCardBeliefs("p2", removedPosition = 2)

        assertEquals(listOf(Rank.QUEEN), modeler.getBelief("p2", 2)?.likelyRanks)
        assertEquals(8, modeler.getBelief("p2", 1)?.minValue)
        assertNull(modeler.getBelief("p2", 3), "a belief was left pointing past the hand")
    }

    @Test
    fun removingABeliefLeavesTheRest() {
        val modeler = OpponentModeler()
        modeler.handleObservedAction(
            ObservedAction.TossIn("p2", testCard(Rank.KING, "K_0"), position = 0),
        )
        modeler.handleObservedAction(
            ObservedAction.TossIn("p2", testCard(Rank.ACE, "A_0"), position = 1),
        )

        modeler.removeCardBelief("p2", 0)

        assertNull(modeler.getBelief("p2", 0))
        assertEquals(listOf(Rank.ACE), modeler.getBelief("p2", 1)?.likelyRanks)
    }

    @Test
    fun resetForgetsEverything() {
        val modeler = OpponentModeler()
        modeler.handleObservedAction(ObservedAction.SwapOwn("p2"))
        modeler.reset()

        // Hands from the previous round are gone, so beliefs about them are worse than useless.
        assertNull(modeler.getPlayerBeliefs("p2"))
        assertNull(modeler.getMostLikelyVintoCaller())
    }
}

package game.vinto.bot

import game.vinto.shapes.Difficulty
import game.vinto.shapes.Rank
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The one decision that cannot be taken back.
 *
 * Everything here is about the asymmetry the solver is built on: being wrong about calling
 * Vinto costs the round, and being wrong about *not* calling costs a turn. So the analysis is
 * deliberately pessimistic, and these tests check that the pessimism is actually present
 * rather than merely intended — an optimistic solver would still pass a naive "does it
 * compare two numbers" test.
 */
class VintoRoundSolverTest {

    /** Hard difficulty observes perfectly, which keeps the arithmetic under test visible. */
    private fun memory() = BotMemory("bot-1", Difficulty.HARD, Random(1))

    private fun solverKnowing(vararg seen: Triple<String, Int, Rank>): Pair<VintoRoundSolver, BotMemory> {
        val botMemory = memory()
        seen.forEach { (playerId, position, rank) ->
            botMemory.observeCard(testCard(rank, "${rank.serialName}_$playerId$position"), playerId, position)
        }
        return VintoRoundSolver(botMemory) to botMemory
    }

    private fun hand(vararg ranks: Rank) =
        ranks.mapIndexed { index, rank -> testCard(rank, "${rank.serialName}_bot$index") }

    @Test
    fun anUnreadTableMakesAMarginalCallUnsafe() {
        val (solver, _) = solverKnowing()
        val opponents = listOf(VintoRoundSolver.OpponentHand("p2", cardCount = 4))

        // Eight points is a good hand, and against four cards nobody has seen it is still not
        // provably ahead — because the four could be the four best cards left.
        val marginal = solver.validateVintoCall(hand(Rank.THREE, Rank.FIVE), opponents)
        assertFalse(marginal.shouldCallVinto, marginal.reason)

        val overwhelming = solver.validateVintoCall(hand(Rank.JOKER, Rank.KING), opponents)
        assertTrue(overwhelming.shouldCallVinto, overwhelming.reason)
    }

    @Test
    fun unseenCardsArePricedAsGoodOnesRatherThanAverageOnes() {
        val (solver, _) = solverKnowing()
        val fourUnknown = listOf(VintoRoundSolver.OpponentHand("p2", cardCount = 4))

        val result = solver.validateVintoCall(hand(Rank.TWO), fourUnknown)

        // A neutral estimate would put four unseen cards near 24 points. The whole reason the
        // solver refuses marginal calls is that it does not believe that.
        assertTrue(
            result.worstCaseOpponentScore < 4 * 6.0,
            "four unseen cards were priced at ${result.worstCaseOpponentScore}, which is not pessimistic",
        )
        assertTrue(result.worstCaseOpponentScore > 0)
    }

    @Test
    fun aKnownSwapActionMakesAnOpponentMoreDangerousDespiteCostingMore() {
        val quiet = solverKnowing(Triple("p2", 0, Rank.SIX), Triple("p2", 1, Rank.SIX)).first
        val armed = solverKnowing(Triple("p2", 0, Rank.JACK), Triple("p2", 1, Rank.SIX)).first
        val opponents = listOf(VintoRoundSolver.OpponentHand("p2", cardCount = 2))
        val botHand = hand(Rank.TWO)

        val quietCase = quiet.validateVintoCall(botHand, opponents).worstCaseOpponentScore
        val armedCase = armed.validateVintoCall(botHand, opponents).worstCaseOpponentScore

        // Jack is 10 and a six is 6, so the armed hand is worth *more* on paper — 16 against
        // 12 — and is still treated as the more dangerous one, because it can trade.
        assertTrue(
            armedCase < quietCase,
            "armed $armedCase should undercut quiet $quietCase",
        )
    }

    @Test
    fun anOpponentCannotBeAssumedToScoreBelowZero() {
        val solver = solverKnowing(
            Triple("p2", 0, Rank.KING),
            Triple("p2", 1, Rank.KING),
        ).first

        val result = solver.validateVintoCall(
            hand(Rank.TWO),
            listOf(VintoRoundSolver.OpponentHand("p2", cardCount = 2)),
        )

        // Two Kings are worth nothing and are both swap actions, so the naive arithmetic
        // lands at -10.
        assertEquals(0.0, result.worstCaseOpponentScore)
        assertFalse(result.shouldCallVinto)
    }

    @Test
    fun theCallIsJudgedAgainstTheBestOpponentNotTheAverageOne() {
        val solver = solverKnowing(
            Triple("weak", 0, Rank.QUEEN),
            Triple("weak", 1, Rank.QUEEN),
            Triple("strong", 0, Rank.TWO),
            Triple("strong", 1, Rank.THREE),
        ).first

        val result = solver.validateVintoCall(
            hand(Rank.FOUR, Rank.FOUR),
            listOf(
                VintoRoundSolver.OpponentHand("weak", cardCount = 2),
                VintoRoundSolver.OpponentHand("strong", cardCount = 2),
            ),
        )

        // The weak hand is 20 and the strong one is 5. Averaging them would say call.
        assertEquals(5.0, result.worstCaseOpponentScore)
        assertFalse(result.shouldCallVinto, result.reason)
    }

    @Test
    fun confidenceTracksHowMuchOfTheTableWasActuallySeen() {
        val blind = solverKnowing().first
        val partial = solverKnowing(Triple("p2", 0, Rank.SIX)).first
        val complete = solverKnowing(Triple("p2", 0, Rank.SIX), Triple("p2", 1, Rank.SIX)).first
        val opponents = listOf(VintoRoundSolver.OpponentHand("p2", cardCount = 2))
        val botHand = hand(Rank.TWO)

        val blindConfidence = blind.validateVintoCall(botHand, opponents).confidence
        val partialConfidence = partial.validateVintoCall(botHand, opponents).confidence
        val completeConfidence = complete.validateVintoCall(botHand, opponents).confidence

        assertEquals(0.3, blindConfidence)
        assertTrue(partialConfidence > blindConfidence)
        assertTrue(completeConfidence > partialConfidence)
        assertEquals(0.95, completeConfidence)
    }

    @Test
    fun anEmptyTableIsNotMistakenForAWinnableOne() {
        val solver = solverKnowing().first

        val result = solver.validateVintoCall(hand(Rank.SIX), opponents = emptyList())

        assertEquals(0.3, result.confidence, "no opponents means no information, not certainty")
    }

    @Test
    fun theReasonSaysWhichWayItWentAndOnWhatNumbers() {
        val solver = solverKnowing(Triple("p2", 0, Rank.QUEEN), Triple("p2", 1, Rank.QUEEN)).first
        val opponents = listOf(VintoRoundSolver.OpponentHand("p2", cardCount = 2))

        val safe = solver.validateVintoCall(hand(Rank.TWO), opponents)
        val risky = solver.validateVintoCall(hand(Rank.QUEEN, Rank.QUEEN), opponents)

        assertTrue(safe.reason.startsWith("Safe to call Vinto"), safe.reason)
        assertTrue(safe.reason.contains("2/2 opponent cards known"), safe.reason)
        assertTrue(risky.reason.startsWith("Risky to call Vinto"), risky.reason)
    }
}

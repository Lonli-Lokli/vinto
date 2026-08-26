package game.vinto.bot

import game.vinto.shapes.Card
import game.vinto.shapes.CardAction
import game.vinto.shapes.getCardAction
import game.vinto.shapes.getCardValue
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * Whether calling Vinto is actually safe, ported from
 * `packages/bot/src/lib/vinto-round-solver.ts`.
 *
 * Calling Vinto is the one decision the bot cannot take back. Every other mistake costs a
 * few points; this one hands the round to whoever is quietly sitting on a better hand, and
 * the coalition still gets a turn afterwards to improve it. So the solver does not ask
 * whether the bot is *probably* ahead — it asks whether it is still ahead when every
 * opponent gets everything they could want:
 *
 * - unseen cards are assumed to be among the best still in the deck, not average ones;
 * - every swap action an opponent is known to hold is assumed to land well;
 * - a King is assumed to be spent as a swap, which is its most valuable use here.
 *
 * The answer comes with a [VintoValidationResult.confidence], because "safe against the
 * worst case" means much less when the bot has seen almost none of the table.
 */
class VintoRoundSolver(private val botMemory: BotMemory) {

    /** A memory below this is a hunch; the solver counts the card as unseen instead. */
    private companion object {

        /** What one swap action is assumed to save its owner — a 10 traded for a 2, say. */
        const val SWAP_BENEFIT = 5.0

        /** Used when the bot has lost track of the deck entirely. */
        const val NEUTRAL_CARD_VALUE = 6.0

        /** Unseen cards are assumed to come from the best of what is left, not the middle. */
        const val OPTIMISTIC_PERCENTILE = 0.3

        /** Knowing nothing is not the same as knowing the answer is no. */
        const val NO_INFORMATION_CONFIDENCE = 0.3
        const val BASE_CONFIDENCE = 0.3
        const val CONFIDENCE_FROM_KNOWLEDGE = 0.65

        const val PERCENT = 100
    }

    data class VintoValidationResult(
        val shouldCallVinto: Boolean,
        val callerScore: Int,
        val worstCaseOpponentScore: Double,
        /** 0-1: how much of the table the analysis is actually based on. */
        val confidence: Double,
        val reason: String,
    )

    /** An opponent as the analysis sees them: what is known, what is not, and what they hold. */
    private data class PlayerAnalysisState(
        val id: String,
        val knownCards: List<Card>,
        val unknownCardCount: Int,
        val actionCardTypes: List<CardAction>,
    ) {
        val totalCards: Int get() = knownCards.size + unknownCardCount
    }

    data class OpponentHand(val id: String, val cardCount: Int)

    fun validateVintoCall(botCards: List<Card>, opponents: List<OpponentHand>): VintoValidationResult {
        val callerScore = botCards.sumOf { it.value }
        val opponentStates = opponents.map { buildOpponentState(it.id, it.cardCount) }

        // The bot is only safe against the *best* opponent, so the worst case is the minimum.
        val worstCaseOpponentScore = opponentStates
            .map { calculateWorstCaseScore(it) }
            .minOrNull()
            ?: Double.MAX_VALUE

        val shouldCallVinto = callerScore < worstCaseOpponentScore
        val confidence = calculateConfidence(opponentStates)

        return VintoValidationResult(
            shouldCallVinto = shouldCallVinto,
            callerScore = callerScore,
            worstCaseOpponentScore = worstCaseOpponentScore,
            confidence = confidence,
            reason = generateReason(
                shouldCall = shouldCallVinto,
                callerScore = callerScore,
                worstCaseScore = worstCaseOpponentScore,
                opponents = opponentStates,
                confidence = confidence,
            ),
        )
    }

    private fun buildOpponentState(opponentId: String, cardCount: Int): PlayerAnalysisState {
        val playerMemory = botMemory.getPlayerMemory(opponentId)

        val knownCards = mutableListOf<Card>()
        val actionCardTypes = mutableListOf<CardAction>()
        var unknownCardCount = 0

        for (position in 0 until cardCount) {
            val memory = playerMemory[position]
            if (memory != null && memory.confidence > TRUSTED_CONFIDENCE) {
                knownCards += memory.card
                getCardAction(memory.card.rank)?.let { actionCardTypes += it }
            } else {
                unknownCardCount++
            }
        }

        return PlayerAnalysisState(opponentId, knownCards, unknownCardCount, actionCardTypes)
    }

    /**
     * The best score this opponent could still reach, which is the worst news for the caller.
     *
     * Peeks are not counted directly — they reveal rather than reduce — but they are what
     * makes the swap assumption reasonable, since an opponent who can see their hand knows
     * which card to trade away.
     */
    private fun calculateWorstCaseScore(opponent: PlayerAnalysisState): Double {
        val knownScore = opponent.knownCards.sumOf { it.value }.toDouble()
        val unknownScore = opponent.unknownCardCount * calculateBestPossibleUnknownValue()

        val swaps = opponent.actionCardTypes.count {
            it == CardAction.SWAP_CARDS || it == CardAction.PEEK_AND_SWAP
        }
        // A King declares any rank, so it is assumed to be spent on the best of them.
        val kings = opponent.actionCardTypes.count { it == CardAction.DECLARE_ACTION }
        val actionBenefit = (swaps + kings) * SWAP_BENEFIT

        // Action benefit is an assumption, so it may improve a hand only to zero, never
        // *below* it — two Kings are not worth -10. But a hand *observed* to be negative
        // (a Joker seen at the table) really is negative, and pretending otherwise would
        // let a caller on zero walk into a hand it has already seen beating it.
        val observedScore = knownScore + unknownScore
        return max(min(0.0, observedScore), observedScore - actionBenefit)
    }

    /**
     * What an unseen card is assumed to be worth: the average of the best 30% still out
     * there, rather than the average of everything.
     *
     * This is the asymmetry the whole solver rests on. Assuming an opponent's blind cards are
     * average makes calling Vinto look safe far too often, because the hands that beat you
     * are exactly the ones that are not average.
     */
    private fun calculateBestPossibleUnknownValue(): Double {
        val remainingValues = botMemory.getCardDistribution()
            .filter { it.value > 0 }
            .flatMap { (rank, count) -> List(count) { getCardValue(rank) } }
            .sorted()

        if (remainingValues.isEmpty()) return NEUTRAL_CARD_VALUE

        val percentileSize = ceil(remainingValues.size * OPTIMISTIC_PERCENTILE).toInt()
        val best = remainingValues.take(max(1, percentileSize))

        return best.sum().toDouble() / best.size
    }

    /** How much of the table the analysis actually saw, on a 0.3-0.95 scale. */
    private fun calculateConfidence(opponents: List<PlayerAnalysisState>): Double {
        val totalCards = opponents.sumOf { it.totalCards }
        if (totalCards == 0) return NO_INFORMATION_CONFIDENCE

        val knownCards = opponents.sumOf { it.knownCards.size }
        return BASE_CONFIDENCE + (knownCards.toDouble() / totalCards) * CONFIDENCE_FROM_KNOWLEDGE
    }

    /** Diagnostic text: this ends up in logs and in the UI's explanation of a bot's call. */
    private fun generateReason(
        shouldCall: Boolean,
        callerScore: Int,
        worstCaseScore: Double,
        opponents: List<PlayerAnalysisState>,
        confidence: Double,
    ): String {
        val margin = round(kotlin.math.abs(callerScore - worstCaseScore)).toInt()
        val knownOpponentCards = opponents.sumOf { it.knownCards.size }
        val totalOpponentCards = opponents.sumOf { it.totalCards }
        val confidencePercent = round(confidence * PERCENT).toInt()
        val worstCase = round(worstCaseScore).toInt()

        return if (shouldCall) {
            "Safe to call Vinto. Score $callerScore < $worstCase (margin: $margin). " +
                "Confidence: $confidencePercent% " +
                "($knownOpponentCards/$totalOpponentCards opponent cards known)."
        } else {
            val actionCards = opponents.sumOf { it.actionCardTypes.size }
            "Risky to call Vinto. Score $callerScore >= $worstCase (deficit: $margin). " +
                "Opponents have $actionCards known action cards. Confidence: $confidencePercent%."
        }
    }
}

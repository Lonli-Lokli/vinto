package game.vinto.bot

import game.vinto.shapes.Card
import game.vinto.shapes.Rank
import game.vinto.shapes.getCardValue
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Beliefs about opponents' hidden hands, built only from what is publicly observable.
 *
 * This is the honest alternative to reading opponents' cards, and the reason the previous bot
 * engine was deleted (`docs/bot/BOT-ENGINE-DECISION.md`). Everything here is inferred from
 * moves anyone at the table can see — what was taken from the discard pile, what was thrown
 * away, what was tossed in — so a bot using it is doing what a good human player does.
 *
 * The strongest inference is the first one: taking a 7 from the discard and swapping it in
 * means the card it replaced was worth *more* than 7, or the swap made no sense.
 *
 * Ported from `legacy-web/packages/bot/src/lib/opponent-modeler.ts`.
 */

/** Tuning for how observed actions move a player's apparent readiness to call Vinto. */
private object ReadinessAdjustment {
    /** Peeking means still gathering information, so not ready. */
    const val PEEK_ACTION_PENALTY = -0.1

    /** Swapping (J, Q) means optimising a hand, so getting ready. */
    const val SWAP_ACTION_BOOST = 0.15
    const val PEEK_OWN_PENALTY = -0.05
    const val SWAP_OWN_BOOST = 0.1

    /** A Queen both peeks and swaps, so it counts as a partial swap signal. */
    const val QUEEN_BOOST_FRACTION = 0.7

    const val DISCARD_DRAWN_SCORE_REDUCTION = 2

    /** A high-value discard implies a better hand behind it. */
    const val DISCARD_VALUE_DIVISOR = 3
}

private object ScoreReadiness {
    /** At or below this, maximally ready. */
    const val EXCELLENT_SCORE_THRESHOLD = 10.0

    /** At or above this, not ready at all. */
    const val POOR_SCORE_THRESHOLD = 30.0

    const val EXISTING_READINESS_WEIGHT = 0.7
    const val NEW_SCORE_WEIGHT = 0.3
}

private object DefaultBeliefs {
    /** Five cards at roughly five points each. */
    const val INITIAL_ESTIMATED_SCORE = 25

    const val INITIAL_VINTO_READINESS = 0.0

    /** Below this, a constraint is not worth acting on. */
    const val MIN_SWAP_INFERENCE_CONFIDENCE = 0.8
}

/** What is believed about one hidden card. */
data class CardBelief(
    val minValue: Int? = null,
    val maxValue: Int? = null,
    val likelyRanks: List<Rank> = emptyList(),
    /** Why this belief was formed — for debugging a bot that plays oddly. */
    val reason: String? = null,
    val confidence: Double = 0.0,
)

data class OpponentBeliefs(
    val playerId: String,
    val cardBeliefs: Map<Int, CardBelief> = emptyMap(),
    val estimatedScore: Int = DefaultBeliefs.INITIAL_ESTIMATED_SCORE,
    /** 0-1; how close this player looks to calling Vinto. */
    val vintoReadiness: Double = DefaultBeliefs.INITIAL_VINTO_READINESS,
)

/** Something a bot saw happen, which may inform a belief. */
sealed interface ObservedAction {
    val playerId: String

    /** Took a card from the discard pile and swapped it in — the strongest inference. */
    data class SwapFromDiscard(
        override val playerId: String,
        val card: Card,
        val position: Int,
    ) : ObservedAction

    /** Drew and threw away immediately, implying better cards already in hand. */
    data class DiscardDrawn(override val playerId: String, val card: Card) : ObservedAction

    data class UseAction(override val playerId: String, val card: Card) : ObservedAction

    /** Tossed in a card, confirming its rank outright. */
    data class TossIn(
        override val playerId: String,
        val card: Card,
        val position: Int,
    ) : ObservedAction

    data class PeekOwn(override val playerId: String) : ObservedAction

    data class SwapOwn(override val playerId: String) : ObservedAction
}

class OpponentModeler {

    private val beliefs = mutableMapOf<String, OpponentBeliefs>()

    fun initializePlayer(playerId: String) {
        beliefs.getOrPut(playerId) { OpponentBeliefs(playerId = playerId) }
    }

    fun getBelief(playerId: String, position: Int): CardBelief? =
        beliefs[playerId]?.cardBeliefs?.get(position)

    fun getPlayerBeliefs(playerId: String): OpponentBeliefs? = beliefs[playerId]

    fun getAllBeliefs(): Map<String, OpponentBeliefs> = beliefs.toMap()

    fun handleObservedAction(action: ObservedAction) {
        initializePlayer(action.playerId)

        when (action) {
            is ObservedAction.SwapFromDiscard -> swapFromDiscard(action)
            is ObservedAction.DiscardDrawn -> discardDrawn(action)
            is ObservedAction.UseAction -> useAction(action)
            is ObservedAction.TossIn -> tossIn(action)
            is ObservedAction.PeekOwn ->
                adjustReadiness(action.playerId, ReadinessAdjustment.PEEK_OWN_PENALTY)

            is ObservedAction.SwapOwn ->
                adjustReadiness(action.playerId, ReadinessAdjustment.SWAP_OWN_BOOST)
        }

        updateVintoReadiness(action.playerId)
    }

    /**
     * They took a card worth X from the discard and swapped it into position P, so whatever
     * was at P must have been worth more than X — otherwise the swap made them worse off.
     */
    private fun swapFromDiscard(action: ObservedAction.SwapFromDiscard) {
        val current = beliefs.getValue(action.playerId)
        val discardValue = getCardValue(action.card.rank)

        val belief = CardBelief(
            minValue = discardValue + 1,
            reason = "Swapped out for ${action.card.rank.serialName} ($discardValue pts)",
            confidence = DefaultBeliefs.MIN_SWAP_INFERENCE_CONFIDENCE,
        )
        beliefs[action.playerId] =
            current.copy(cardBeliefs = current.cardBeliefs + (action.position to belief))
    }

    /** Throwing away a good card implies better ones behind it, so cut the estimate harder. */
    private fun discardDrawn(action: ObservedAction.DiscardDrawn) {
        val current = beliefs.getValue(action.playerId)
        val discardedValue = getCardValue(action.card.rank)

        val reduction = max(
            ReadinessAdjustment.DISCARD_DRAWN_SCORE_REDUCTION,
            floor(discardedValue.toDouble() / ReadinessAdjustment.DISCARD_VALUE_DIVISOR).toInt(),
        )
        beliefs[action.playerId] = current.copy(
            estimatedScore = max(0, current.estimatedScore - reduction),
        )
    }

    /**
     * Peeking says "still learning"; swapping says "tidying up before calling". A Queen does
     * both, so it counts as a fraction of a swap signal.
     */
    private fun useAction(action: ObservedAction.UseAction) {
        val delta = when (action.card.rank) {
            Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN -> ReadinessAdjustment.PEEK_ACTION_PENALTY
            Rank.QUEEN ->
                ReadinessAdjustment.SWAP_ACTION_BOOST * ReadinessAdjustment.QUEEN_BOOST_FRACTION

            Rank.JACK -> ReadinessAdjustment.SWAP_ACTION_BOOST
            else -> return
        }
        adjustReadiness(action.playerId, delta)
    }

    /** A tossed-in card is shown to the table, so its rank is known outright. */
    private fun tossIn(action: ObservedAction.TossIn) {
        val current = beliefs.getValue(action.playerId)
        val belief = CardBelief(
            likelyRanks = listOf(action.card.rank),
            reason = "Tossed in ${action.card.rank.serialName}",
            confidence = 1.0,
        )
        beliefs[action.playerId] =
            current.copy(cardBeliefs = current.cardBeliefs + (action.position to belief))
    }

    private fun adjustReadiness(playerId: String, delta: Double) {
        val current = beliefs.getValue(playerId)
        beliefs[playerId] = current.copy(
            vintoReadiness = (current.vintoReadiness + delta).coerceIn(0.0, 1.0),
        )
    }

    /**
     * Blends the readiness implied by their estimated score with the readiness their recent
     * behaviour suggests, weighted towards the latter so a single observation cannot swing it.
     */
    private fun updateVintoReadiness(playerId: String) {
        val current = beliefs[playerId] ?: return

        val range = ScoreReadiness.POOR_SCORE_THRESHOLD - ScoreReadiness.EXCELLENT_SCORE_THRESHOLD
        val scoreReadiness =
            min(1.0, max(0.0, (ScoreReadiness.POOR_SCORE_THRESHOLD - current.estimatedScore) / range))

        beliefs[playerId] = current.copy(
            vintoReadiness = ScoreReadiness.EXISTING_READINESS_WEIGHT * current.vintoReadiness +
                ScoreReadiness.NEW_SCORE_WEIGHT * scoreReadiness,
        )
    }

    /** Whoever looks closest to calling. Used to decide how much time is left to improve. */
    fun getMostLikelyVintoCaller(): String? =
        beliefs.values.maxByOrNull { it.vintoReadiness }?.playerId

    fun removeCardBelief(playerId: String, position: Int) {
        val current = beliefs[playerId] ?: return
        beliefs[playerId] = current.copy(cardBeliefs = current.cardBeliefs - position)
    }

    /**
     * A card leaving a hand renumbers everything after it, so beliefs must move with it —
     * otherwise a belief quietly starts describing a different card. The engine has exactly
     * the same hazard in `participate-in-toss`.
     */
    fun shiftCardBeliefs(playerId: String, removedPosition: Int) {
        val current = beliefs[playerId] ?: return
        beliefs[playerId] = current.copy(
            cardBeliefs = current.cardBeliefs
                .filterKeys { it != removedPosition }
                .mapKeys { (position, _) -> if (position > removedPosition) position - 1 else position },
        )
    }

    /** Beliefs do not survive a round; the hands they describe are gone. */
    fun reset() = beliefs.clear()
}

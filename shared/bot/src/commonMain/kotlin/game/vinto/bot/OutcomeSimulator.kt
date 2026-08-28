package game.vinto.bot

import game.vinto.shapes.Card
import game.vinto.shapes.PlayerState
import game.vinto.shapes.Rank

/**
 * A one-ply look at what a turn would leave behind, ported from
 * `legacy-web/packages/bot/src/lib/mcts-outcome-simulator.ts`.
 *
 * This is not the search. It answers a narrower question — *given this drawn card, which of
 * my own cards should it replace?* — cheaply enough to run over every position in hand. MCTS
 * decides whether to swap at all; this decides where.
 *
 * The three things a turn changes are hand size, knowledge and score, and they are weighted
 * in that order (see [SwapWeights]). Knowledge first looks odd for a game scored on points
 * until you notice that a bot which does not know its own hand can never call Vinto, so its
 * points never get cashed in.
 */
object OutcomeSimulator {

    /** A peek is worth more than the one card it reveals; it also aims the next decision. */
    private const val PEEK_STRATEGIC_BONUS = 3

    /** A Queen sees two cards and gets to act on the pair, so it is worth more than two peeks. */
    private const val QUEEN_STRATEGIC_BONUS = 2
    private const val QUEEN_PEEK_COUNT = 2

    /** An Ace inconveniences somebody else; useful, rarely decisive. */
    private const val ACE_VALUE = 1

    /** A swap that reveals something is worth exactly the one card it reveals. */
    private const val KNOWLEDGE_SWAP_VALUE = 1

    private const val KING_BASE_VALUE = 4
    private const val KING_PAIR_BONUS = 3
    private const val KING_DECLARABLE_ACTION_BONUS = 2

    /**
     * The hand as the bot believes it, for everything below: believed cards where memory
     * speaks, the expected value of an unseen card where it does not. The real cards are
     * never read — the last place in the solo bot that still peeked at its own unread
     * positions was this file, and pricing an unseen card at its true value is a small
     * cheat with a big tell (the Joker-protection penalty firing on a card nobody saw).
     */
    data class BelievedHand(
        val cards: Map<Int, Card>,
        val handSize: Int,
        val expectedUnseenValue: Double,
    ) {
        fun believedScore(): Double =
            cards.values.sumOf { it.value.toDouble() } +
                (handSize - cards.size) * expectedUnseenValue

        fun valueAt(position: Int): Double =
            cards[position]?.value?.toDouble() ?: expectedUnseenValue
    }

    /**
     * Discarding the drawn card outright.
     *
     * Nothing enters the hand, so the only thing that moves is the toss-in the discard opens
     * — which can still be the best move on the table when the bot holds the same rank.
     */
    fun simulateDiscardOutcome(drawnCard: Card, botPlayer: PlayerState, believed: BelievedHand): TurnOutcome {
        val afterTossIn = simulateTossInCascade(
            discardedRank = drawnCard.rank,
            currentHandSize = believed.handSize,
            currentScore = believed.believedScore(),
            believed = believed,
        )

        return TurnOutcome(
            finalHandSize = afterTossIn.handSize,
            finalKnownCards = botPlayer.knownCardPositions.size,
            finalScore = afterTossIn.score.toInt(),
        )
    }

    /**
     * Swapping the drawn card into a given position, in the three stages a swap actually has.
     *
     * Replacing an *unknown* card is worth a point of knowledge on its own: the drawn card
     * was seen when it was drawn, so the position stops being a blind spot. That is why a bot
     * with an unread hand will swap into it even at a small cost in points.
     */
    fun simulateTurnOutcome(
        drawnCard: Card,
        swapPosition: Int,
        botPlayer: PlayerState,
        context: BotDecisionContext,
        believed: BelievedHand,
    ): TurnOutcome {
        if (swapPosition !in 0 until believed.handSize) {
            return simulateDiscardOutcome(drawnCard, botPlayer, believed)
        }
        val displaced = believed.cards[swapPosition]

        // Stage 1 — the swap itself.
        var knownCards = botPlayer.knownCardPositions.size
        if (swapPosition !in botPlayer.knownCardPositions) knownCards += 1
        val scoreAfterSwap =
            believed.believedScore() - believed.valueAt(swapPosition) + drawnCard.value

        // Stage 2 — the toss-in window the discarded card opens. A card the bot cannot name
        // opens a window it cannot plan for, so an unbelieved displacement models none.
        val afterTossIn = displaced?.let {
            simulateTossInCascade(
                discardedRank = it.rank,
                currentHandSize = believed.handSize,
                currentScore = scoreAfterSwap,
                believed = believed,
            )
        } ?: TossInResult(believed.handSize, scoreAfterSwap)

        // Stage 3 — what the discarded card's action would be worth if declared correctly;
        // nothing can be declared about a card the bot never read.
        val knowledgeGain = displaced
            ?.let { simulateActionKnowledgeGain(it, botPlayer, context, believed) } ?: 0

        return TurnOutcome(
            finalHandSize = afterTossIn.handSize,
            finalKnownCards = knownCards + knowledgeGain,
            finalScore = afterTossIn.score.toInt(),
        )
    }

    data class TossInResult(val handSize: Int, val score: Double)

    /**
     * What the bot itself would shed into a window on this rank.
     *
     * Only cards it believes it has seen: a card it cannot name cannot be tossed in, because
     * tossing in the wrong rank costs a penalty card and locks the bot out of the window.
     */
    fun simulateTossInCascade(
        discardedRank: Rank,
        currentHandSize: Int,
        currentScore: Double,
        believed: BelievedHand,
    ): TossInResult {
        var tossInCount = 0
        var scoreReduction = 0.0

        for ((_, card) in believed.cards) {
            if (card.rank != discardedRank) continue
            tossInCount++
            scoreReduction += card.value
        }

        return TossInResult(
            handSize = currentHandSize - tossInCount,
            score = currentScore - scoreReduction,
        )
    }

    /**
     * What an action is worth, denominated in cards-worth-of-knowledge.
     *
     * The units are notional — an Ace does not reveal a card — but they are the units the
     * swap weights are calibrated in, so an action competes with a peek on one scale.
     */
    fun simulateActionKnowledgeGain(
        discardedCard: Card,
        botPlayer: PlayerState,
        context: BotDecisionContext,
        believed: BelievedHand,
    ): Int {
        if (discardedCard.actionText == null) return 0

        val unknownCount = countUnknownCards(botPlayer)

        return when (discardedCard.rank) {
            Rank.SEVEN, Rank.EIGHT -> if (unknownCount > 0) PEEK_STRATEGIC_BONUS else 0
            Rank.QUEEN -> {
                val peeked = minOf(QUEEN_PEEK_COUNT, unknownCount)
                if (peeked > 0) peeked + QUEEN_STRATEGIC_BONUS else 0
            }
            Rank.JACK, Rank.NINE, Rank.TEN -> simulateKnowledgeGainingSwap(botPlayer, context)
            Rank.KING -> calculateKingActionValue(believed)
            Rank.ACE -> ACE_VALUE
            else -> 0
        }
    }

    /**
     * A King is worth more the more the bot can point it at.
     *
     * It declares a rank and plays that rank's action, so a second King is a cascade and any
     * believed action card is something to declare — the value is in the hand around it, as
     * the bot remembers it.
     */
    private fun calculateKingActionValue(believed: BelievedHand): Int {
        val cards = believed.cards.values

        val otherKings = cards.count { it.rank == Rank.KING }
        val declarableActions = cards.count { it.actionText != null && it.rank != Rank.KING }

        return KING_BASE_VALUE +
            otherKings * KING_PAIR_BONUS +
            declarableActions * KING_DECLARABLE_ACTION_BONUS
    }

    /**
     * Whether a swap action could actually reveal anything.
     *
     * It needs both halves: a blind spot of the bot's own to fill, and a card it has read in
     * an *opponent's* hand to fill it with. Either alone is worth nothing — and the bot's own
     * entry in the knowledge map is not an opponent's, which is the mistake this used to
     * make.
     */
    private fun simulateKnowledgeGainingSwap(
        botPlayer: PlayerState,
        context: BotDecisionContext,
    ): Int {
        if (countUnknownCards(botPlayer) == 0) return 0
        val knowsAnOpponentCard = context.opponentKnowledge
            .any { (ownerId, cards) -> ownerId != botPlayer.id && cards.isNotEmpty() }
        return if (knowsAnOpponentCard) KNOWLEDGE_SWAP_VALUE else 0
    }

    /**
     * The comparable score for an outcome, with the swaps that must never happen priced out.
     *
     * The penalties are deliberately out of scale with everything else. Giving away a Joker
     * for a 6 costs `7 × 15 × 3.0 × 100`, which no amount of knowledge gain can outvote —
     * that is the point. They are a floor, not a tuning parameter.
     */
    fun calculateStrategicOutcomeScore(
        outcome: TurnOutcome,
        drawnCard: Card,
        swappedOutCard: Card?,
    ): Double {
        val baseScore = calculateOutcomeScore(outcome)
        if (swappedOutCard == null) return baseScore

        // Positive means taking on points, which is what the penalties are for.
        val scoreDelta = (drawnCard.value - swappedOutCard.value).toDouble()

        val penalty = when {
            swappedOutCard.rank == Rank.JOKER ->
                scoreDelta * SwapWeights.SCORE *
                    CardProtection.JOKER_MULTIPLIER * CardProtection.JOKER_PENALTY_AMPLIFIER

            swappedOutCard.rank == Rank.KING ->
                scoreDelta * SwapWeights.SCORE *
                    CardProtection.KING_MULTIPLIER * CardProtection.KING_PENALTY_AMPLIFIER

            // Only when the swap is for the worse. Trading a 6 away for a 2 is a good swap
            // and must not be taxed for it.
            scoreDelta > 0 ->
                scoreDelta * SwapWeights.SCORE * CardProtection.GENERAL_SWAP_PENALTY_MULTIPLIER

            else -> 0.0
        }

        return baseScore - penalty
    }

    /** More knowledge, fewer cards, lower score — in that order of importance. */
    fun calculateOutcomeScore(outcome: TurnOutcome): Double =
        outcome.finalKnownCards * SwapWeights.KNOWLEDGE -
            outcome.finalHandSize * SwapWeights.HAND_SIZE -
            outcome.finalScore * SwapWeights.SCORE
}

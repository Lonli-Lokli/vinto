package game.vinto.bot

import game.vinto.shapes.Rank
import game.vinto.shapes.isActionable

/**
 * The five things the search cares about when scoring a position, ported from
 * `packages/bot/src/lib/evaluation-helpers.ts`.
 *
 * Each returns 0-1 so they can be weighted against each other. The weights themselves live
 * in [evaluateState].
 */

/** Memories below this are hunches; the evaluator only counts what the bot is sure of. */

/**
 * Matching ranks in hand, which is where multi-step play comes from.
 *
 * A pair is not just two cards — it is a toss-in that removes both, and if they are Kings it
 * is a toss-in that also hands the bot an action. Rewarding pairs is what makes the search
 * look past the current turn without having to search deeper.
 */
fun evaluateTossInPotential(botPlayer: MctsPlayerState): Double {
    val rankCounts = mutableMapOf<Rank, Int>()
    val rankValues = mutableMapOf<Rank, Int>()

    for (position in 0 until botPlayer.cardCount) {
        val memory = botPlayer.knownCards[position] ?: continue
        if (memory.confidence <= TRUSTED_CONFIDENCE) continue

        val rank = memory.card.rank
        rankCounts[rank] = (rankCounts[rank] ?: 0) + 1
        rankValues.getOrPut(rank) { memory.card.value }
    }

    var tossInValue = 0.0
    for ((rank, count) in rankCounts) {
        if (count < 2) continue

        val value = rankValues[rank] ?: DEFAULT_UNKNOWN_VALUE
        // A pair sheds one extra card, a triple two, and so on.
        val cascade = count - 1

        tossInValue += cascade * value * CASCADE_MULTIPLIER
        if (value >= HIGH_VALUE_THRESHOLD) tossInValue += cascade * HIGH_VALUE_BONUS
        if (rank == Rank.KING) tossInValue += cascade * KING_PAIR_BONUS
    }

    return minOf(1.0, tossInValue / MAX_TOSS_IN_VALUE)
}

private const val DEFAULT_UNKNOWN_VALUE = 5
private const val CASCADE_MULTIPLIER = 3
private const val HIGH_VALUE_THRESHOLD = 10
private const val HIGH_VALUE_BONUS = 5
private const val KING_PAIR_BONUS = 10
private const val MAX_TOSS_IN_VALUE = 60.0

/**
 * Normalisation ranges for [evaluateRelativePosition].
 *
 * These map a raw advantage onto 0-1. The offsets say where "level" sits — being 10 points
 * behind the field still scores above zero, because the game is long — and the spans say how
 * big a lead has to be before it counts as won.
 */
private const val SCORE_ADVANTAGE_OFFSET = 10
private const val SCORE_ADVANTAGE_SPAN = 30
private const val CARD_ADVANTAGE_OFFSET = 2
private const val CARD_ADVANTAGE_SPAN = 5
private const val BEST_RIVAL_OFFSET = 5
private const val BEST_RIVAL_SPAN = 25

/** Where the bot stands against the table, on score and on hand size. */
fun evaluateRelativePosition(state: MctsGameState, botPlayer: MctsPlayerState): Double {
    val opponents = state.players.filter { it.id != botPlayer.id }
    if (opponents.isEmpty()) return 0.0

    val averageOpponentScore = opponents.sumOf { it.score } / opponents.size
    val bestOpponentScore = opponents.minOf { it.score }
    val averageOpponentCards = opponents.sumOf { it.cardCount }.toDouble() / opponents.size

    val scoreComponent = ((averageOpponentScore - botPlayer.score + SCORE_ADVANTAGE_OFFSET) /
        SCORE_ADVANTAGE_SPAN).coerceIn(0.0, 1.0)
    val cardComponent = ((averageOpponentCards - botPlayer.cardCount + CARD_ADVANTAGE_OFFSET) /
        CARD_ADVANTAGE_SPAN).coerceIn(0.0, 1.0)
    // Being ahead of the *field* is worth less than being ahead of whoever is actually winning.
    val competitive = ((bestOpponentScore - botPlayer.score + BEST_RIVAL_OFFSET) /
        BEST_RIVAL_SPAN).coerceIn(0.0, 1.0)

    return scoreComponent * POSITION_SCORE_WEIGHT +
        cardComponent * POSITION_CARD_WEIGHT +
        competitive * POSITION_RIVAL_WEIGHT
}

/** Score matters most, then how many cards are left to shed, then the closest rival. */
private const val POSITION_SCORE_WEIGHT = 0.5
private const val POSITION_CARD_WEIGHT = 0.3
private const val POSITION_RIVAL_WEIGHT = 0.2

/**
 * Action cards in hand, weighted by how much each action is actually worth.
 *
 * The `when` below is a tuning table and is left as literals on purpose: a name per rank
 * would say no more than the rank already does, and the table is easier to re-tune when the
 * numbers sit beside their cases.
 */
@Suppress("MagicNumber")
fun evaluateActionCardValue(botPlayer: MctsPlayerState): Double {
    var actionValue = 0.0
    val rankCounts = mutableMapOf<Rank, Int>()

    for (position in 0 until botPlayer.cardCount) {
        val memory = botPlayer.knownCards[position] ?: continue
        if (memory.confidence <= TRUSTED_CONFIDENCE) continue

        val rank = memory.card.rank
        rankCounts[rank] = (rankCounts[rank] ?: 0) + 1
        if (!rank.isActionable()) continue

        actionValue += when (rank) {
            Rank.KING -> 15.0
            Rank.QUEEN, Rank.JACK -> 10.0
            Rank.NINE, Rank.TEN -> 6.0
            Rank.SEVEN, Rank.EIGHT -> 4.0
            Rank.ACE -> 3.0
            else -> 0.0
        }
    }

    // A King can declare a rank the bot already holds a pair of, turning one action into a
    // cascade — so the two together are worth more than either apart.
    val pairCount = rankCounts.count { it.value >= 2 }
    if (rankCounts.containsKey(Rank.KING) && pairCount > 0) {
        actionValue += pairCount * KING_SYNERGY_BONUS
    }

    return minOf(1.0, actionValue / MAX_ACTION_VALUE)
}

private const val KING_SYNERGY_BONUS = 8
private const val MAX_ACTION_VALUE = 50.0

/** How much the bot knows, weighted towards its own hand — which is what it can act on. */
@Suppress("MagicNumber")
fun evaluateInformationAdvantage(state: MctsGameState, botPlayer: MctsPlayerState): Double {
    val ownKnowledge =
        if (botPlayer.cardCount > 0) botPlayer.knownCards.size.toDouble() / botPlayer.cardCount
        else 0.0

    val opponents = state.players.filter { it.id != botPlayer.id }
    val totalOpponentCards = opponents.sumOf { it.cardCount }
    val knownOpponentCards = opponents.sumOf { it.knownCards.size }
    val opponentKnowledge =
        if (totalOpponentCards > 0) knownOpponentCards.toDouble() / totalOpponentCards else 0.0

    return ownKnowledge * 0.6 + opponentKnowledge * 0.4
}

/** Inverted danger: an opponent who is shorter, lower-scoring or unreadable is a threat. */
@Suppress("MagicNumber")
fun evaluateThreatLevel(state: MctsGameState, botPlayer: MctsPlayerState): Double {
    var threat = 0.0

    for (opponent in state.players.filter { it.id != botPlayer.id }) {
        if (opponent.cardCount < botPlayer.cardCount) {
            threat += (botPlayer.cardCount - opponent.cardCount) * 0.1
        }
        if (opponent.score < botPlayer.score) {
            threat += (botPlayer.score - opponent.score) / 40
        }
        val unknownRatio =
            if (opponent.cardCount > 0) {
                (opponent.cardCount - opponent.knownCards.size).toDouble() / opponent.cardCount
            } else {
                0.0
            }
        threat += unknownRatio * 0.08
    }

    return maxOf(0.0, 1 - threat)
}

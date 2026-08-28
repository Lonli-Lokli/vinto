package game.vinto.engine

import game.vinto.shapes.Card
import game.vinto.shapes.PlayerState

/**
 * Final scores per player.
 *
 * In coalition mode the Vinto caller gets their own total, while every other player — the
 * coalition — is scored on the *best* (lowest) total among them, since they win or lose
 * together. Without a caller, everyone is scored individually.
 *
 * Ported from `legacy-web/packages/engine/src/lib/utils/scoring.ts`.
 */
fun calculateFinalScores(
    players: List<PlayerState>,
    vintoCallerId: String?,
): Map<String, Int> {
    val vintoCaller = vintoCallerId?.let { id -> players.firstOrNull { it.id == id } }
        ?: return players.associate { it.id to calculateCardTotal(it.cards) }

    val coalitionMembers = players.filter { it.id != vintoCaller.id }
    val bestCoalitionScore = coalitionMembers.minOfOrNull { calculateCardTotal(it.cards) }

    val scores = mutableMapOf(vintoCaller.id to calculateCardTotal(vintoCaller.cards))
    if (bestCoalitionScore != null) {
        for (member in coalitionMembers) scores[member.id] = bestCoalitionScore
    }
    return scores
}

fun calculateCardTotal(cards: List<Card>): Int = cards.sumOf { it.value }

/**
 * What a round is worth, from `VINTO_RULES.md` — "Scoring a Round".
 *
 * The caller is compared against the *best* coalition hand, because the coalition wins or
 * loses together. Three outcomes, and the asymmetry between them is the whole reason calling
 * Vinto is a decision rather than a habit:
 *
 * - the caller is strictly lower  → caller **+3**, every coalition member **−1**
 * - a coalition member is lower   → caller **−1**, every coalition member **+3**
 * - a tie                         → caller **+3**, coalition **0**
 *
 * A tie going to the caller is not a rounding convenience: it is what makes "beat them" mean
 * beat them, and it is why the coalition planner searches for a strictly lower total.
 *
 * Without a caller — a round that ended some other way — nobody scores.
 */
fun calculateRoundPoints(
    players: List<PlayerState>,
    vintoCallerId: String?,
): Map<String, Int> {
    val caller = vintoCallerId?.let { id -> players.firstOrNull { it.id == id } }
        ?: return players.associate { it.id to 0 }

    val coalition = players.filter { it.id != caller.id }
    val callerTotal = calculateCardTotal(caller.cards)
    val bestCoalition = coalition.minOfOrNull { calculateCardTotal(it.cards) }
        ?: return players.associate { it.id to 0 }

    val callerWins = callerTotal <= bestCoalition
    val tie = callerTotal == bestCoalition

    return players.associate { player ->
        player.id to when {
            player.id == caller.id -> if (callerWins) CALLER_WIN_POINTS else CALLER_LOSS_POINTS
            tie -> 0
            callerWins -> COALITION_LOSS_POINTS
            else -> COALITION_WIN_POINTS
        }
    }
}

private const val CALLER_WIN_POINTS = 3
private const val CALLER_LOSS_POINTS = -1
private const val COALITION_WIN_POINTS = 3
private const val COALITION_LOSS_POINTS = -1

/**
 * Game points by final rank, from `VINTO_RULES.md` — "Game End": 5, 3, 2, then nothing.
 *
 * Ranking is by cumulative round points, highest first. Players who tie share a rank and each
 * take that rank's award — the alternative, breaking ties arbitrarily, would hand somebody two
 * points for having a name earlier in the alphabet.
 */
fun calculateGamePoints(cumulativePoints: Map<String, Int>): Map<String, Int> {
    val ordered = cumulativePoints.values.distinct().sortedDescending()

    return cumulativePoints.mapValues { (_, points) ->
        GAME_POINTS_BY_RANK.getOrElse(ordered.indexOf(points)) { 0 }
    }
}

private val GAME_POINTS_BY_RANK = listOf(5, 3, 2)

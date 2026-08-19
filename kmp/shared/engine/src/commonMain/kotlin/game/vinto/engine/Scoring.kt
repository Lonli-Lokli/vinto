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
 * Ported from `packages/engine/src/lib/utils/scoring.ts`.
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

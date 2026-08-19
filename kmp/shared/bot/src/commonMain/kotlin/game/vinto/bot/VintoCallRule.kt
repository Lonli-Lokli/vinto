package game.vinto.bot

import game.vinto.shapes.getCardValue

/**
 * Whether a bot should call Vinto.
 *
 * Deliberately simple and conservative, mirroring how people actually play: call only when
 * your hand is worth zero or less. Zero is roughly what the coalition can reach between them,
 * and the tie-break favours the caller — the coalition wins only if its lowest total is
 * *strictly* lower — so a hand of zero is a winning call.
 *
 * A confidence-based version (estimate opponents from memory, call at ~95% certainty) belongs
 * here eventually; `VintoRoundSolver.validateVintoCall` already does the worst-case maths.
 * This rule is the floor, not the ceiling.
 *
 * Ported from `packages/bot/src/lib/vinto-call-rule.ts`.
 */

/** Hands are only counted once the bot knows every card it holds. */
fun knowsEntireHand(context: BotDecisionContext): Boolean {
    val known = context.botPlayer.knownCardPositions.toSet()
    return context.botPlayer.cards.indices.all { it in known }
}

/** Total value of the bot's own hand. Only meaningful when [knowsEntireHand] is true. */
fun ownHandScore(context: BotDecisionContext): Int =
    context.botPlayer.cards.sumOf { getCardValue(it.rank) }

fun shouldCallVintoByScore(context: BotDecisionContext, threshold: Int = 0): Boolean {
    // Calling in the opening is a coin flip whatever the hand, and it makes for a dull game.
    // Everyone gets a couple of turns first.
    if (context.gameState.turnNumber < context.allPlayers.size * 2) return false

    // Nobody calls after someone else has, and the caller cannot call twice.
    if (context.gameState.vintoCallerId != null) return false

    // Any unknown card sinks the call: a bot holding a known Joker and three unseen cards has
    // a *known* score of -1 and a real expected score far above it. Requiring full
    // self-knowledge is what keeps this rule honest without opponent modelling.
    if (!knowsEntireHand(context)) return false

    return ownHandScore(context) <= threshold
}

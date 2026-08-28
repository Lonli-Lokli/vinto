package game.vinto.bot

import game.vinto.shapes.Rank
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
 * Ported from `legacy-web/packages/bot/src/lib/vinto-call-rule.ts`.
 */

/**
 * What the caller-to-be would say its own cards are, when nothing better is supplied: the
 * engine's record of the positions it has read — a perfect memory. The service passes its
 * [BotMemory.believedOwnCards] instead, so a weak bot judges the call on what it actually
 * remembers, exactly as it declares to a coalition.
 */
private fun rememberedHand(context: BotDecisionContext): Map<Int, Rank> =
    context.opponentKnowledge[context.botId].orEmpty().mapValues { it.value.rank }

/** Hands are only counted once the bot believes it knows every card it holds. */
fun knowsEntireHand(
    context: BotDecisionContext,
    believed: Map<Int, Rank> = rememberedHand(context),
): Boolean = context.botPlayer.cards.indices.all { it in believed }

/** Total value of the hand as the bot believes it. Only meaningful under [knowsEntireHand]. */
fun ownHandScore(
    context: BotDecisionContext,
    believed: Map<Int, Rank> = rememberedHand(context),
): Int = believed.values.sumOf { getCardValue(it) }

/**
 * The conditions under which a call is even considerable, whatever the hand is worth.
 * Shared between the plain score rule and the solver-wired call in the service.
 */
fun vintoCallGatesOpen(
    context: BotDecisionContext,
    believed: Map<Int, Rank> = rememberedHand(context),
): Boolean {
    // Calling in the opening is a coin flip whatever the hand, and it makes for a dull game.
    // Everyone gets a couple of turns first.
    if (context.gameState.turnNumber < context.allPlayers.size * 2) return false

    // Nobody calls after someone else has, and the caller cannot call twice.
    if (context.gameState.vintoCallerId != null) return false

    // Any unknown card sinks the call: a bot holding a known Joker and three unseen cards has
    // a *known* score of -1 and a real expected score far above it. Requiring full
    // self-belief is what keeps this rule honest without opponent modelling — and a belief
    // that is wrong makes for a call that loses, which is a memory problem, not a rules one.
    return knowsEntireHand(context, believed)
}

fun shouldCallVintoByScore(
    context: BotDecisionContext,
    threshold: Int = 0,
    believed: Map<Int, Rank> = rememberedHand(context),
): Boolean {
    if (!vintoCallGatesOpen(context, believed)) return false

    return ownHandScore(context, believed) <= threshold
}

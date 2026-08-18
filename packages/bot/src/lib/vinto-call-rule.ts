import { getCardValue } from '@vinto/shapes';
import { BotDecisionContext } from './shapes';

/**
 * Decides whether a bot should call Vinto.
 *
 * The rule is deliberately simple and conservative, mirroring how people actually play:
 * only call when your hand is worth zero or less. Zero is roughly what the coalition can
 * reach between them, and the tie-break favours the caller (the coalition wins only if
 * its lowest total is *strictly* lower), so a hand of zero is a winning call.
 *
 * A confidence-based version — estimate opponents' hands from BotMemory and call when
 * ~95% sure of not losing — belongs here later; see `VintoRoundSolver.validateVintoCall`,
 * which already does the worst-case maths. This rule is the floor, not the ceiling.
 */

/** Hands are only counted once the bot knows every card it holds. */
export function knowsEntireHand(context: BotDecisionContext): boolean {
  const known = new Set(context.botPlayer.knownCardPositions);
  return context.botPlayer.cards.every((_, position) => known.has(position));
}

/** Total value of the bot's own hand. Only meaningful when `knowsEntireHand` is true. */
export function ownHandScore(context: BotDecisionContext): number {
  return context.botPlayer.cards.reduce(
    (total, card) => total + getCardValue(card.rank),
    0,
  );
}

export function shouldCallVintoByScore(
  context: BotDecisionContext,
  threshold = 0,
): boolean {
  // Calling in the opening is a coin flip regardless of the hand, and it makes for a
  // dull game. Everyone gets a couple of turns first.
  if (context.gameState.turnNumber < context.allPlayers.length * 2) {
    return false;
  }

  // The Vinto caller cannot call twice, and nobody calls after someone else has.
  if (context.gameState.vintoCallerId) {
    return false;
  }

  // Any unknown card sinks the call: a bot holding a known Joker and three unseen cards
  // has a *known* score of -1 and a real expected score far above it. Requiring full
  // self-knowledge is what keeps this rule honest without opponent modelling.
  if (!knowsEntireHand(context)) {
    return false;
  }

  return ownHandScore(context) <= threshold;
}

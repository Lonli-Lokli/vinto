// Pure functions for bot decision heuristics

import { Card, PlayerState, Rank, getCardValue } from '@vinto/shapes';

/**
 * Check if we should always take action cards from discard
 *
 * RULE: Can only take from discard if top card is unused action card (7-K, A)
 * and MUST use the action immediately (cannot swap into hand)
 *
 * This heuristic handles cards where using the action is always beneficial:
 * - 7, 8: Peek one of your own cards (if we have unknown cards)
 * - Q: Peek TWO cards from different players + optional swap (very powerful!)
 */
export function shouldAlwaysTakeDiscardPeekCard(
  discardTop: Card | null,
  botPlayer: PlayerState
): boolean {
  if (!discardTop) return false;

  const { rank: discardRank, played } = discardTop;
  const hasUnknownCards = countUnknownCards(botPlayer) > 0;

  // Only take if card is an unused action card
  if (played) return false;

  // Always take Queen - it peeks TWO cards + optional swap (extremely valuable)
  if (discardRank === 'Q') {
    return true;
  }

  // Always take 7/8 if we have unknown cards (peek our own)
  return (discardRank === '7' || discardRank === '8') && hasUnknownCards;
}

/**
 * Check if we should always use peek action when drawn from deck
 *
 * When drawing from deck, player can choose to:
 * 1. Use action immediately (discard card, apply effect)
 * 2. Swap into hand (and optionally guess the swapped-out card's rank)
 *
 * This heuristic identifies when using the action is strictly better than swapping:
 * - 7, 8: Low value (7-8 points), peek action is worth more than card value if we have unknowns
 * - Q: Value 10, but action is VERY powerful (peek 2 + optional swap) - always worth using
 */
export function shouldAlwaysUsePeekAction(
  drawnCard: Card,
  botPlayer: PlayerState
): boolean {
  if (!drawnCard.actionText || drawnCard.played) return false;

  const hasUnknownCards = countUnknownCards(botPlayer) > 0;

  // Always use Queen - it peeks TWO cards + optional swap (extremely valuable)
  if (drawnCard.rank === 'Q') {
    return true;
  }

  // Always use 7/8 if we have unknown cards (peek our own)
  // Low card value (7-8) makes the peek action more valuable than swapping
  return (drawnCard.rank === '7' || drawnCard.rank === '8') && hasUnknownCards;
}

/**
 * Evaluate Ace swap vs action decision
 * Returns true if we should use the Ace action, false if we should swap
 */
export function shouldUseAceAction(
  botPlayer: PlayerState,
  allPlayers: PlayerState[],
  botId: string
): boolean {
  // Check if we have high-value known cards to swap
  let maxKnownValue = 0;
  for (let pos = 0; pos < botPlayer.cards.length; pos++) {
    if (botPlayer.knownCardPositions.includes(pos)) {
      const card = botPlayer.cards[pos];
      if (card.value > maxKnownValue) {
        maxKnownValue = card.value;
      }
    }
  }

  // If we can swap Ace for a card worth 8+, do that instead
  if (maxKnownValue >= 8) {
    return false;
  }

  // Check if any opponent is close to calling Vinto (defensive Ace use)
  const botScore = botPlayer.cards.reduce((sum, c) => sum + c.value, 0);
  for (const player of allPlayers) {
    if (player.id === botId) continue;
    const opponentScore = player.cards.reduce((sum, c) => sum + c.value, 0);

    // If opponent has low score and few cards, Ace force-draw is valuable
    if (opponentScore < botScore - 3 && player.cards.length <= 3) {
      return true;
    }
  }

  // Default: swap Ace (low value card, action is situational)
  return false;
}

/**
 * Check if we should participate in toss-in
 * ALWAYS beneficial if we have ANY matching cards
 */
export function shouldParticipateInTossIn(
  discardedRanks: [Rank, ...Rank[]],
  botPlayer: PlayerState
): boolean {
  const ranksToCheck: Rank[] = discardedRanks.filter(
    (rank) => getCardValue(rank) >= 0
  );

  // Simple check: do we have any cards that match the toss-in ranks?
  return botPlayer.cards.some((card, index) => {
    // Only consider known cards
    if (!botPlayer.knownCardPositions.includes(index)) {
      return false;
    }
    // Check if card matches any of the toss-in ranks
    return ranksToCheck.includes(card.rank);
  });
}

/**
 * Count how many unknown cards a player has
 */
export function countUnknownCards(player: PlayerState): number {
  return player.cards.length - player.knownCardPositions.length;
}

/**
 * Calculate the total value (score) of a hand
 */
export function calculateHandScore(cards: Card[]): number {
  return cards.reduce((sum, card) => sum + card.value, 0);
}

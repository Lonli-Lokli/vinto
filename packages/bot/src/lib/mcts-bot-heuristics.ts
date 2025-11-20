// Pure functions for bot decision heuristics

import { Card, PlayerState, Rank, getCardValue } from '@vinto/shapes';

/**
 * Check if we should always take 7 or 8 from discard
 * These peek actions are STRICTLY better than drawing when we have unknown cards
 */
export function shouldAlwaysTakeDiscardPeekCard(
  discardTop: Card | null,
  botPlayer: PlayerState
): boolean {
  if (!discardTop) return false;

  const { rank: discardRank, played } = discardTop;
  const hasUnknownCards = countUnknownCards(botPlayer) > 0;

  return (discardRank === '7' || discardRank === '8' || discardRank === 'Q') && hasUnknownCards && !played;
}

/**
 * Check if we should always use 7 or 8 peek action
 * These are STRICTLY better than swapping when we have unknown cards
 */
export function shouldAlwaysUsePeekAction(
  drawnCard: Card,
  botPlayer: PlayerState
): boolean {
  if (!drawnCard.actionText || drawnCard.played) return false;

  const hasUnknownCards = countUnknownCards(botPlayer) > 0;
  return (drawnCard.rank === '7' || drawnCard.rank === '8' || drawnCard.rank === 'Q') && hasUnknownCards;
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

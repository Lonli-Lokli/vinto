// utils/action-description-helper.ts
import { getCardName, Rank } from '@vinto/shapes';

export interface ActionTarget {
  playerName: string;
  position?: number;
  cardRank?: Rank;
}

/**
 * Format toss-in action description
 */
export function formatTossInDescription(
  playerName: string,
  cardRank: Rank,
  wasCorrect: boolean
): string {
  const cardName = getCardName(cardRank);

  if (wasCorrect) {
    return `${playerName} tossed in ${cardName}`;
  } else {
    return `${playerName} tossed in ${cardName} (wrong - penalty!)`;
  }
}

/**
 * Format swap description
 */
export function formatSwapDescription(
  playerName: string,
  drawnCardRank: Rank,
  position: number
): string {
  return `${playerName} swapped ${getCardName(drawnCardRank)} to position ${
    position + 1
  }`;
}

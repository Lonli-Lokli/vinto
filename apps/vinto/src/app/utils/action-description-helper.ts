// utils/action-description-helper.ts
import { getCardName, Rank } from '@/shared';

export interface ActionTarget {
  playerName: string;
  position?: number;
  cardRank?: Rank;
}

/**
 * Format a detailed action description for UI display
 */
export function formatActionDescription(
  playerName: string,
  cardRank: Rank,
  actionType: string,
  targets?: ActionTarget[]
): string {
  const cardName = getCardName(cardRank);

  switch (actionType) {
    case 'peek-own':
      return `${playerName} used ${cardName} (peek own card)`;

    case 'peek-opponent':
      if (targets && targets.length > 0) {
        return `${playerName} used ${cardName} (peek opponent) → saw ${targets[0].playerName}'s card`;
      }
      return `${playerName} used ${cardName} (peek opponent)`;

    case 'peek-and-swap':
      if (targets && targets.length >= 2) {
        return `${playerName} used ${cardName} (peek & swap) → swapped 2 cards`;
      }
      return `${playerName} used ${cardName} (peek & swap)`;

    case 'swap-cards':
      if (targets && targets.length >= 2) {
        return `${playerName} used ${cardName} (swap) → ${targets[0].playerName} ↔ ${targets[1].playerName}`;
      }
      return `${playerName} used ${cardName} (swap cards)`;

    case 'force-draw':
      if (targets && targets.length > 0) {
        return `${playerName} used ${cardName} (force draw) → ${targets[0].playerName} draws 1 card`;
      }
      return `${playerName} used ${cardName} (force draw)`;

    case 'declare-action':
      if (targets && targets.length > 0 && targets[0].cardRank) {
        return `${playerName} used ${cardName} (declare) → chose ${getCardName(
          targets[0].cardRank
        )}`;
      }
      return `${playerName} used ${cardName} (declare action)`;

    case 'toss-in':
      return `${playerName} tossed in ${cardName}`;

    case 'draw':
      return `${playerName} drew a card`;

    case 'discard':
      return `${playerName} discarded ${cardName}`;

    default:
      return `${playerName} used ${cardName}`;
  }
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

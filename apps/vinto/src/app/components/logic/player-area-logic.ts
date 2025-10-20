// player-area-logic.ts
// Pure functions for PlayerArea component logic

import { GamePhase, PlayerState } from '@vinto/shapes';

export type PlayerPosition = 'bottom' | 'left' | 'top' | 'right';

export type CardSize = 'sm' | 'md' | 'lg';

/**
 * Determine if we can see this player's cards based on official Vinto rules
 */
export function canSeePlayerCard(params: {
  cardIndex: number;
  player: PlayerState;
  gamePhase: GamePhase;
  temporarilyVisibleCards: Set<number>;
  coalitionLeaderId: string | null;
  humanPlayerId: string | null;
}): boolean {
  const {
    cardIndex,
    player,
    gamePhase,
    temporarilyVisibleCards,
    coalitionLeaderId,
    humanPlayerId,
  } = params;

  // During setup phase, human can see their own cards for memorization
  if (
    gamePhase === 'setup' &&
    player.isHuman &&
    (player.knownCardPositions.includes(cardIndex) ||
      temporarilyVisibleCards.has(cardIndex))
  ) {
    return true;
  }

  // During gameplay, show temporarily visible cards (from actions like peek)
  // This works for both human and bot cards when they're being peeked
  if (
    (gamePhase === 'playing' || gamePhase === 'final') &&
    temporarilyVisibleCards.has(cardIndex)
  ) {
    return true;
  }

  // Coalition leader sees ALL coalition member cards during final phase
  if (
    (gamePhase === 'playing' || gamePhase === 'final') &&
    coalitionLeaderId &&
    humanPlayerId &&
    coalitionLeaderId === humanPlayerId &&
    player.coalitionWith.length > 0 &&
    !player.isVintoCaller
  ) {
    // Human is coalition leader and this player is a coalition member
    return true;
  }

  // During scoring phase, ALL cards are revealed
  if (gamePhase === 'scoring') {
    return true;
  }

  return false;
}

/**
 * Calculate dynamic card size based on number of cards and player type
 */
export function getCardSizeForPlayer(
  cardCount: number,
  isHuman: boolean
): CardSize {
  if (cardCount > 7) return 'sm';
  if (cardCount > 5) return 'md';
  return isHuman ? 'lg' : 'md';
}

/**
 * Determine if a specific card is selectable
 */
export function isCardSelectable(params: {
  hasOnCardClick: boolean;
  isSelectingSwapPosition: boolean;
  swapPosition: number | null;
  cardIndex: number;
  isSelectingActionTarget: boolean;
}): boolean {
  const { hasOnCardClick, isSelectingSwapPosition, isSelectingActionTarget } =
    params;

  return hasOnCardClick && (isSelectingSwapPosition || isSelectingActionTarget);
}

/**
 * Determine if a card should be highlighted
 */
export function shouldHighlightCard(params: {
  isSelectingSwapPosition: boolean;
  swapPosition: number | null;
  cardIndex: number;
  isSelectingActionTarget: boolean;
}): boolean {
  const { isSelectingSwapPosition, isSelectingActionTarget } = params;

  return isSelectingSwapPosition || isSelectingActionTarget;
}

/**
 * Get CSS classes for card container based on player position
 */
export function getCardContainerClasses(position: PlayerPosition): string {
  const classes = {
    bottom: 'flex flex-wrap gap-1 justify-center max-w-full',
    top: 'flex flex-wrap gap-1 justify-center max-w-full',
    left: 'flex flex-col flex-wrap gap-1 items-center max-h-full',
    right: 'flex flex-col flex-wrap gap-1 items-center max-h-full',
  };
  return classes[position];
}

/**
 * Check if avatar should be positioned before cards
 */
export function shouldAvatarComeFirst(position: PlayerPosition): boolean {
  return position === 'bottom' || position === 'left';
}

/**
 * Check if this is a side player (left/right position)
 */
export function isSidePlayer(position: PlayerPosition): boolean {
  return position === 'left' || position === 'right';
}

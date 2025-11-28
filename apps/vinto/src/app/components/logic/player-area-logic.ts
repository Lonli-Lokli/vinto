// player-area-logic.ts
// Pure functions for PlayerArea component logic

import { GamePhase, PlayerState } from '@vinto/shapes';
import { CardSize } from '../helpers';

export type PlayerPosition = 'bottom' | 'left' | 'top' | 'right';

/**
 * Determine if we can see this player's cards based on official Vinto rules
 * Also checks if card is known by bots during coalition mode (final round)
 *
 * @returns Object with:
 *  - canSee: whether the card value should be revealed
 *  - isBotKnown: whether to show bot-known indicator (yellow glow)
 */
export function canSeePlayerCard(params: {
  cardIndex: number;
  targetPlayer: PlayerState;
  observingPlayer: PlayerState | undefined;
  gamePhase: GamePhase;
  temporarilyVisibleCards: Map<number, string[]>;
  coalitionLeaderId: string | null;
  vintoCallerId?: string | null;
  allPlayers?: PlayerState[];
}): boolean {
  const {
    cardIndex,
    targetPlayer,
    observingPlayer,
    gamePhase,
    temporarilyVisibleCards,
    coalitionLeaderId,
    vintoCallerId = null,
    allPlayers = [],
  } = params;

  let canSee = false;

  const cardVisibilities = temporarilyVisibleCards.get(cardIndex) || [];
  // During setup phase, human can see their own cards for memorization
  if (
    gamePhase === 'setup' &&
    targetPlayer.id === observingPlayer?.id &&
    (targetPlayer.knownCardPositions.includes(cardIndex) ||
      temporarilyVisibleCards.has(cardIndex))
  ) {
    canSee = true;
  }

  // During gameplay, show temporarily visible cards (from actions like peek)
  // This works for both human and bot cards when they're being peeked
  if (
    (gamePhase === 'playing' || gamePhase === 'final') &&
    cardVisibilities.includes(observingPlayer?.id ?? '') // '' handles null case
  ) {
    canSee = true;
  }

  // Coalition leader sees ALL coalition member cards during final phase
  if (
    (gamePhase === 'playing' || gamePhase === 'final') &&
    coalitionLeaderId &&
    coalitionLeaderId === observingPlayer?.id &&
    targetPlayer.coalitionWith.length > 0 &&
    !targetPlayer.isVintoCaller
  ) {
    // Human is coalition leader and this player is a coalition member
    canSee = true;
  }

  // During scoring phase, ALL cards are revealed
  if (gamePhase === 'scoring') {
    canSee = true;
  }

  if (
    gamePhase === 'final' &&
    vintoCallerId === observingPlayer?.id &&
    targetPlayer.isBot &&
    allPlayers.length > 0
  ) {
    // During coalition mode, bots share knowledge
    // Check if ANY bot knows about this specific card position on this player
    canSee = canSee || allPlayers.some(bot => {
      if (!bot.isBot) return false;

      // Check if this bot knows about the current card position
      // If the bot is viewing its own cards, check knownCardPositions directly
      if (bot.id === targetPlayer.id) {
        return bot.knownCardPositions.includes(cardIndex);
      }

      // Check if this bot has opponent knowledge about this player's card
      const opponentKnowledge = bot.opponentKnowledge?.[targetPlayer.id];
      if (opponentKnowledge?.knownCards?.[cardIndex]) {
        return true;
      }

      return false;
    });
  }

  return canSee;
}

/**
 * Calculate dynamic card size based on number of cards and player type
 */
export function getCardSizeForPlayer(
  cardCount: number,
  position: PlayerPosition,
  isHuman: boolean
): CardSize {
  // Side players: very constrained vertically, never use large
  if (position === 'left' || position === 'right') {
    if (cardCount <= 5) return 'md';
    return 'sm';
  }

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
 * @deprecated Use HorizontalPlayerCards or VerticalPlayerCards components instead
 * Note: left/right cards are rotated 90°, so they need special spacing treatment.
 * The rotation keeps the original box dimensions (w-8 h-12 = 32×48px box),
 * but visually the card appears 48×32. Using negative margin compensates for this
 * by pulling cards closer together to match the visual gap of top/bottom players.
 */
export function getCardContainerClasses(position: PlayerPosition): string {
  const classes = {
    bottom: 'flex flex-wrap gap-1 justify-center max-w-full',
    top: 'flex flex-wrap gap-1 justify-center max-w-full',
    left: 'flex flex-col flex-wrap gap-1 items-center max-h-full -space-y-2',
    right: 'flex flex-col flex-wrap gap-1 items-center max-h-full -space-y-2',
  };
  return classes[position];
}

/**
 * Check if position should use horizontal card layout (top/bottom)
 */
export function isHorizontalPosition(position: PlayerPosition): boolean {
  return position === 'top' || position === 'bottom';
}

/**
 * Check if position should use vertical card layout (left/right)
 */
export function isVerticalPosition(position: PlayerPosition): boolean {
  return position === 'left' || position === 'right';
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


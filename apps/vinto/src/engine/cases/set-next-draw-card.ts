// engine/cases/set-next-draw-card.ts
// Handle SET_NEXT_DRAW_CARD action (debug/testing only)

import { GameState, SetNextDrawCardAction } from '@/shared';
import copy from 'fast-copy';

/**
 * Handle SET_NEXT_DRAW_CARD action
 *
 * Debug action that moves a card of the specified rank to the top of the draw pile
 * This allows testing specific card draws during development
 *
 * @param state Current game state
 * @param action SetNextDrawCardAction
 * @returns New game state with reordered draw pile
 */
export function handleSetNextDrawCard(
  state: GameState,
  action: SetNextDrawCardAction
): GameState {
  const { rank } = action.payload;

  const newState = copy(state);

  // Find a card with the specified rank in the draw pile
  const cardIndex = newState.drawPile.findIndex((card) => card.rank === rank);

  if (cardIndex === -1) {
    // Card not found in draw pile, return unchanged state
    console.warn(`No card with rank ${rank} found in draw pile`);
    return state;
  }

  // Move the card to the top of the draw pile (index 0)
  const [card] = newState.drawPile.splice(cardIndex, 1);
  newState.drawPile.unshift(card);

  return newState;
}

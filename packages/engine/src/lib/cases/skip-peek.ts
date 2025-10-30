import { GameState, SkipPeekAction } from '@vinto/shapes';
import copy from 'fast-copy';
import { clearTossInAfterActionableCard } from '../utils/toss-in-utils';

/**
 * SKIP_PEEK Handler
 *
 * Flow:
 * 1. Player has not peeked at a card (7, 8, 9, or 10 action)
 * 2. Confirming they've not seen it and are ready to continue
 * 3. Move card to discard pile
 * 4. Clear pending action
 * 5. Increment turn and transition to idle
 *
 * Note: The actual peek happens in SELECT_ACTION_TARGET
 * This just confirms and completes the turn
 * Difference with CONFIRM_PEEK is that card stays as not played
 */
export function handleSkipPeek(
  state: GameState,
  action: SkipPeekAction
): GameState {
  // Create new state (deep copy for safety)
  const newState = copy(state);

  const peekCard = newState.pendingAction?.card;

  // Move action card to discard pile
  if (peekCard) {
    newState.discardPile.addToTop({
      ...copy(peekCard),
    });
  }

  clearTossInAfterActionableCard(
    newState,
    action.payload.playerId,
    peekCard?.rank
  );

  return newState;
}

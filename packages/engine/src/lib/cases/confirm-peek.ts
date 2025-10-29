import { GameState, ConfirmPeekAction } from '@vinto/shapes';
import copy from 'fast-copy';
import {
  addTossInCard,
  clearTossInAfterActionableCard,
  clearTossInReadyList,
  getAutomaticallyReadyPlayers,
} from '../utils/toss-in-utils';
import { getTargetTypeFromRank } from '../utils/action-utils';

/**
 * CONFIRM_PEEK Handler
 *
 * Flow:
 * 1. Player has peeked at a card (7, 8, 9, or 10 action)
 * 2. Confirming they've seen it and are ready to continue
 * 3. Move card to discard pile
 * 4. Clear pending action
 * 5. Mark card as played
 * 6. Increment turn and transition to idle
 *
 * Note: The actual peek happens in SELECT_ACTION_TARGET
 * This just confirms and completes the turn
 */
export function handleConfirmPeek(
  state: GameState,
  action: ConfirmPeekAction
): GameState {
  // Create new state (deep copy for safety)
  const newState = copy(state);

  const peekCard = newState.pendingAction?.card;

  // Move action card to discard pile
  if (peekCard) {
    newState.discardPile.addToTop({
      ...copy(peekCard),
      played: true,
    });
  }

  clearTossInAfterActionableCard(
    newState,
    action.payload.playerId,
    peekCard?.rank
  );

  return newState;
}

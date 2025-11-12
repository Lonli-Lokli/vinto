import { GameState, DiscardCardAction, logger } from '@vinto/shapes';
import copy from 'fast-copy';
import { clearTossInAfterActionableCard } from '../utils/toss-in-utils';

/**
 * DISCARD_CARD Handler
 *
 * Flow:
 * 1. Player has drawn a card and decided to discard it (or swapped it)
 * 2. The card to discard is in pendingAction
 * 3. Add that card to discard pile
 * 4. Clear pendingAction
 * 5. Initialize toss-in phase
 */
export function handleDiscardCard(
  state: GameState,
  action: DiscardCardAction
): GameState {
  const { playerId } = action.payload;

  // Create new state (deep copy for safety)
  const newState = copy(state);

  // The card to discard is in pendingAction
  if (!newState.pendingAction?.card) {
    logger.warn('[handleDiscardCard] No card in pendingAction to discard', {
      playerId,
      phase: state.phase,
      subPhase: state.subPhase,
      hasPendingAction: !!state.pendingAction,
    });
    return state;
  }

  const discardedCard = newState.pendingAction.card;

  clearTossInAfterActionableCard(
    copy(discardedCard),
    newState,
    action.payload.playerId
  );

  return newState;
}

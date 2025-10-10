import copy from 'fast-copy';
import { getTargetTypeFromRank } from '../utils/action-utils';
import { GameState, UseCardActionAction } from '@/shared';

/**
 * USE_CARD_ACTION Handler
 *
 * Flow:
 * 1. Player has drawn/swapped and now chooses to use the card's action
 * 2. Update pending action phase to indicate we're using the action
 * 3. Set targetType based on card rank
 * 4. Transition to 'awaiting_action' phase
 * 5. Next: Player will SELECT_ACTION_TARGET (if card needs target)
 *
 * Note: The actual card effect execution happens later after target selection
 */
export function handleUseCardAction(
  state: GameState,
  _action: UseCardActionAction
): GameState {
  // Create new state (deep copy for safety)
  const newState = copy(state);

  // Update pending action to reflect we're now using the card's action
  if (newState.pendingAction) {
    newState.pendingAction.actionPhase = 'selecting-target';
    newState.pendingAction.targetType = getTargetTypeFromRank(
      newState.pendingAction.card.rank
    );
  }

  // Transition to awaiting_action phase (ready for target selection)
  newState.subPhase = 'awaiting_action';

  return newState;
}

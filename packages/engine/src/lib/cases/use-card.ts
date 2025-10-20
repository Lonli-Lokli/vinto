import copy from 'fast-copy';
import { getTargetTypeFromRank } from '../utils/action-utils';
import { GameState, UseCardActionAction } from '@vinto/shapes';

/**
 * USE_CARD_ACTION Handler
 *
 * Flow:
 * 1. Player has drawn/swapped and now chooses to use the card's action
 * 2. Update pending action phase to 'selecting-target'
 * 3. Set targetType based on card rank
 * 4. Transition to 'awaiting_action' subPhase
 * 5. Next: Player will SELECT_ACTION_TARGET to select card(s)/player(s)
 *
 * Note: All cards now use the same flow with SELECT_ACTION_TARGET.
 * Multi-step actions (J, Q, K) track progress via targets.length
 */
export function handleUseCardAction(
  state: GameState,
  _action: UseCardActionAction
): GameState {
  // Create new state (deep copy for safety)
  const newState = copy(state);

  // Update pending action to reflect we're now using the card's action
  if (newState.pendingAction) {
    const cardRank = newState.pendingAction.card.rank;

    // All cards use the same 'selecting-target' phase
    newState.pendingAction.actionPhase = 'selecting-target';
    newState.pendingAction.targetType = getTargetTypeFromRank(cardRank);
  }

  // Transition to awaiting_action phase (ready for target selection)
  newState.subPhase = 'awaiting_action';

  return newState;
}

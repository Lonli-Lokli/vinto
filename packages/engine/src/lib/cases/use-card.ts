import copy from 'fast-copy';
import { getTargetTypeFromRank } from '../utils/action-utils';
import { GameState, UseCardActionAction } from '@vinto/shapes';

/**
 * USE_CARD_ACTION Handler
 *
 * Flow:
 * 1. Player has drawn/swapped and now chooses to use the card's action
 * 2. Update pending action phase to indicate we're using the action
 * 3. Set targetType based on card rank
 * 4. For King cards: transition to 'selecting-king-card' phase (select card first)
 * 5. For other cards: transition to 'selecting-target' phase
 * 6. Transition to 'awaiting_action' phase
 * 7. Next: Player will SELECT_KING_CARD_TARGET (for King) or SELECT_ACTION_TARGET (for other cards)
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
    const cardRank = newState.pendingAction.card.rank;

    // King has a special two-step flow: first select a card, then declare rank
    if (cardRank === 'K') {
      newState.pendingAction.actionPhase = 'selecting-king-card';
    } else {
      newState.pendingAction.actionPhase = 'selecting-target';
    }

    newState.pendingAction.targetType = getTargetTypeFromRank(cardRank);
  }

  // Transition to awaiting_action phase (ready for target selection)
  newState.subPhase = 'awaiting_action';

  return newState;
}

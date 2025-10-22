import { GameState, PlayDiscardAction } from '@vinto/shapes';
import copy from 'fast-copy';
import { getTargetTypeFromRank } from '../utils/action-utils';

/**
 * PLAY_DISCARD Handler
 *
 * Flow:
 * 1. Transition to 'drawing' phase (for animation, same as draw)
 * 2. Remove top card from discard pile
 * 3. Create pending action with taken card
 * 4. Immediately transition to using the card's action (no choice to discard)
 *
 * Note: When taking a card from discard, you MUST use its action.
 * You cannot discard it like you can when drawing from the deck.
 */
export function handlePlayDiscard(
  state: GameState,
  action: PlayDiscardAction
): GameState {
  const { playerId } = action.payload;

  // Create new state (deep copy for safety)
  const newState = copy(state);

  // Transition to drawing phase (skip if already in ai_thinking for bots)
  if (newState.subPhase !== 'ai_thinking') {
    newState.subPhase = 'drawing';
  }

  // Take the top card from discard pile
  const takenCard = newState.discardPile.drawTop();
  if (!takenCard) {
    // Should never happen due to validation, but be defensive
    return state;
  }

  // Create pending action and immediately set it to use the action
  // (no choice to discard when taking from discard pile)
  newState.pendingAction = {
    card: takenCard,
    from: 'hand',
    playerId,
    actionPhase: 'selecting-target',
    targetType: getTargetTypeFromRank(takenCard.rank),
    targets: [],
  };

  // Transition directly to awaiting_action phase
  // Player must use the card's action, no option to discard
  newState.subPhase = 'awaiting_action';

  return newState;
}

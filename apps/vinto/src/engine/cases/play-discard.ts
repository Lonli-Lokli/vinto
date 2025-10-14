import { GameState, PlayDiscardAction } from '@/shared';
import copy from 'fast-copy';

/**
 * PLAY_DISCARD Handler
 *
 * Flow:
 * 1. Transition to 'drawing' phase (for animation, same as draw)
 * 2. Remove top card from discard pile
 * 3. Create pending action with taken card
 * 4. Transition to 'choosing' phase
 *
 * Note: Very similar to DRAW_CARD, but takes from discard pile
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

  // Create pending action
  newState.pendingAction = {
    card: takenCard,
    playerId,
    actionPhase: 'choosing-action',
    targets: [],
  };

  // Transition to choosing phase
  newState.subPhase = 'choosing';

  return newState;
}

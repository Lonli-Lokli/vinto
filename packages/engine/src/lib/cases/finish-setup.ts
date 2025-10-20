import { GameState, FinishSetupAction } from '@vinto/shapes';
import copy from 'fast-copy';

/**
 * FINISH_SETUP Handler
 *
 * Flow:
 * 1. Player has finished peeking at their setup cards
 * 2. Transition from 'setup' phase to 'playing' phase
 * 3. Game can now begin normally
 */
export function handleFinishSetup(
  state: GameState,
  _action: FinishSetupAction
): GameState {
  // Create new state (deep copy for safety)
  const newState = copy(state);

  // Transition from setup to playing
  if (newState.phase === 'setup') {
    newState.phase = 'playing';
    newState.subPhase = 'idle';
  }

  return newState;
}

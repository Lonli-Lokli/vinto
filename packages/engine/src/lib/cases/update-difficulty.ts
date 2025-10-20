// engine/cases/update-difficulty.ts
// Handle UPDATE_DIFFICULTY action

import { GameState, UpdateDifficultyAction } from '@vinto/shapes';
import copy from 'fast-copy';
/**
 * Update game difficulty setting
 *
 * This affects bot AI decision-making behavior.
 * Can be changed at any time during the game.
 */
export function handleUpdateDifficulty(
  state: GameState,
  action: UpdateDifficultyAction
): GameState {
  // Create new state (deep copy for safety)
  const newState = copy(state);
  // Update difficulty
  newState.difficulty = action.payload.difficulty;
  return newState;
}

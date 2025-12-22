// engine/cases/update-bot-version.ts
// Handle UPDATE_BOT_VERSION action

import { GameState, UpdateBotVersionAction } from '@vinto/shapes';
import { copy } from 'fast-copy';

/**
 * Update bot version setting
 *
 * This affects which bot AI implementation is used.
 * Can be changed at any time during the game.
 */
export function handleUpdateBotVersion(
  state: GameState,
  action: UpdateBotVersionAction
): GameState {
  // Create new state (deep copy for safety)
  const newState = copy(state);
  // Update bot version
  newState.botVersion = action.payload.botVersion;
  return newState;
}
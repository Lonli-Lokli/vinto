import { AdvanceTurnAction, GameState } from '@/shared';
import copy from 'fast-copy';

/**
 * ADVANCE_TURN Handler
 *
 * Flow:
 * 1. Move to next player (circular)
 * 2. Increment turn count when wrapping back to first player
 * 3. Check if next player is a bot
 * 4. Transition to appropriate phase (idle for humans, ai_thinking for bots)
 */
export function handleAdvanceTurn(
  state: GameState,
  _action: AdvanceTurnAction
): GameState {
  // Create new state (deep copy for safety)
  const newState = copy(state);

  // Advance to next player (circular)
  newState.currentPlayerIndex =
    (newState.currentPlayerIndex + 1) % newState.players.length;

  // Increment turn count when we wrap back to the first player (completing a full round)
  if (newState.currentPlayerIndex === 0) {
    newState.turnCount++;
  }

  // Check if game should end (after vinto call, when we complete the final round)
  if (
    newState.phase === 'final' &&
    newState.players[newState.currentPlayerIndex].id === newState.vintoCallerId
  ) {
    // Final round complete - end the game
    newState.phase = 'scoring';
    newState.subPhase = 'idle';

    console.log('[handleAdvanceTurn] Final round complete, game finished');

    return newState;
  }

  // Get the new current player
  const nextPlayer = newState.players[newState.currentPlayerIndex];

  // Transition to appropriate phase based on player type
  if (nextPlayer.isBot) {
    newState.subPhase = 'ai_thinking';
  } else {
    newState.subPhase = 'idle';
  }

  return newState;
}

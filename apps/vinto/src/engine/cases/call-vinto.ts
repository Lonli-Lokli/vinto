import { GameState, CallVintoAction } from '@/shared';
import copy from 'fast-copy';

/**
 * CALL_VINTO Handler
 *
 * Flow:
 * 1. Player declares "Vinto!" when they have low score
 * 2. Set vintoCallerId to track who called it
 * 3. Set finalTurnTriggered to true (last round begins)
 * 4. Game continues normally but will end after all players get one more turn
 *
 * Note: Vinto is called to declare you're close to winning
 */
export function handleCallVinto(
  state: GameState,
  action: CallVintoAction
): GameState {
  const { playerId } = action.payload;

  // Create new state (deep copy for safety)
  const newState = copy(state);

  // Set vinto caller
  newState.vintoCallerId = playerId;

  // Trigger final turn (everyone gets one more turn)
  newState.finalTurnTriggered = true;

  // Find the player and mark them as vinto caller
  const player = newState.players.find((p) => p.id === playerId);
  if (player) {
    player.isVintoCaller = true;
  }

  // Game continues in same phase (vinto is declared, turn continues)
  return newState;
}

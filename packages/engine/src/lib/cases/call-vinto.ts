import { GameState, CallVintoAction } from '@vinto/shapes';
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

  // Transition to final phase
  newState.phase = 'final';

  // Find the player and mark them as vinto caller
  for (const player of newState.players) {
    player.isVintoCaller = player.id === playerId;

    if (!player.isVintoCaller) {
      player.coalitionWith = newState.players
        .filter((p) => p.id !== playerId)
        .map((p) => p.id);
    }
  }

  // If Vinto is called during a toss-in, the player has already completed their turn
  // (they drew/played and discarded, which triggered the toss-in)
  // So we need to advance to the next player
  const wasDuringTossIn =
    newState.subPhase === 'toss_queue_active' ||
    newState.subPhase === 'toss_queue_processing';

  // Clear any active toss-in since Vinto caller cannot participate
  if (newState.activeTossIn) {
    const originalPlayerIndex = newState.activeTossIn.originalPlayerIndex;
    newState.activeTossIn = null;

    // If called during toss-in, advance to next player
    if (wasDuringTossIn) {
      newState.currentPlayerIndex =
        (originalPlayerIndex + 1) % newState.players.length;

      if (newState.currentPlayerIndex === 0) {
        newState.turnNumber++;
      }

      // Set appropriate subPhase for next player
      const nextPlayer = newState.players[newState.currentPlayerIndex];
      newState.subPhase = nextPlayer.isBot ? 'ai_thinking' : 'idle';
    } else {
      // If called before turn completion, player continues their turn
      newState.subPhase = 'idle';
    }
  }

  console.log('[handleCallVinto] Vinto called, entering final phase:', {
    playerId,
    phase: newState.phase,
    subPhase: newState.subPhase,
    currentPlayer: newState.players[newState.currentPlayerIndex].name,
    finalTurnTriggered: newState.finalTurnTriggered,
  });

  return newState;
}

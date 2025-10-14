import { GameState, PlayerTossInFinishedAction } from '@/shared';
import copy from 'fast-copy';

/**
 * PLAYER_TOSS_IN_FINISHED Handler
 *
 * Flow:
 * 1. Player confirms they're done with toss-in participation
 * 2. Add player to playersReadyForNextTurn list
 * 3. Check if all human players have confirmed
 * 4. If all humans ready:
 *    - Clear toss-in
 *    - Advance turn automatically
 * 5. If not all ready yet:
 *    - Keep toss-in active
 *    - Wait for other players
 *
 * Note: Bot players are considered automatically ready
 * Note: In network multiplayer, server would track this and dispatch FINISH_TOSS_IN_PERIOD
 */
export function handlePlayerTossInFinished(
  state: GameState,
  action: PlayerTossInFinishedAction
): GameState {
  const { playerId } = action.payload;

  // Create new state (deep copy for safety)
  const newState = copy(state);

  if (!newState.activeTossIn) {
    console.warn('[handlePlayerTossInFinished] No active toss-in');
    return state;
  }

  // Add player to ready list if not already there
  if (!newState.activeTossIn.playersReadyForNextTurn.includes(playerId)) {
    newState.activeTossIn.playersReadyForNextTurn.push(playerId);
  }

  console.log('[handlePlayerTossInFinished] Player ready:', {
    playerId,
    readyPlayers: newState.activeTossIn.playersReadyForNextTurn,
  });

  // Check if all human players are ready
  const humanPlayers = newState.players.filter((p) => p.isHuman);
  const allHumansReady = humanPlayers.every((p) =>
    newState.activeTossIn!.playersReadyForNextTurn.includes(p.id)
  );

  console.log('[handlePlayerTossInFinished] All humans ready:', {
    allHumansReady,
    totalHumans: humanPlayers.length,
    readyCount: newState.activeTossIn.playersReadyForNextTurn.length,
  });

  // If all human players are ready, finish toss-in and advance turn
  if (allHumansReady) {
    console.log(
      '[handlePlayerTossInFinished] All humans ready, finishing toss-in and advancing turn'
    );

    // Clear toss-in
    newState.activeTossIn = null;

    // Advance to next player (circular)
    newState.currentPlayerIndex =
      (newState.currentPlayerIndex + 1) % newState.players.length;

    // Get the new current player
    const nextPlayer = newState.players[newState.currentPlayerIndex];

    // Transition to appropriate phase based on player type
    if (nextPlayer.isBot) {
      newState.subPhase = 'ai_thinking';
    } else {
      newState.subPhase = 'idle';
    }

    console.log(
      '[handlePlayerTossInFinished] Toss-in complete, turn advanced to:',
      {
        nextPlayer: nextPlayer.name,
        subPhase: newState.subPhase,
      }
    );
  }

  return newState;
}

import { GameState, PlayerTossInFinishedAction } from '@/shared';
import copy from 'fast-copy';
import { getTargetTypeFromRank } from '../utils/action-utils';

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

  // If all human players are ready, check if we have queued actions to process
  if (allHumansReady) {
    const hasQueuedActions = newState.activeTossIn.queuedActions.length > 0;

    if (hasQueuedActions) {
      console.log(
        '[handlePlayerTossInFinished] All humans ready, starting to process queued actions',
        {
          queuedCount: newState.activeTossIn.queuedActions.length,
          queuedCards: newState.activeTossIn.queuedActions.map(
            (a) => a.card.rank
          ),
        }
      );

      // Start processing the first queued action
      const firstAction = newState.activeTossIn.queuedActions[0];

      // Set up pending action for the first queued card
      newState.pendingAction = {
        card: firstAction.card,
        playerId: firstAction.playerId,
        actionPhase: 'choosing-action',
        targetType: getTargetTypeFromRank(firstAction.card.rank),
        targets: [],
      };

      // Transition to awaiting_action phase (shows Skip button in UI)
      newState.subPhase = 'awaiting_action';

      // Mark toss-in as not waiting for input (processing queue)
      newState.activeTossIn.waitingForInput = false;

      console.log(
        '[handlePlayerTossInFinished] Processing first queued action:',
        {
          playerId: firstAction.playerId,
          card: firstAction.card.rank,
        }
      );
    } else {
      console.log(
        '[handlePlayerTossInFinished] All humans ready, no queued actions, finishing toss-in and advancing turn'
      );

      // Save the original player index before clearing toss-in
      const originalPlayerIndex = newState.activeTossIn.originalPlayerIndex;

      // Clear toss-in
      newState.activeTossIn = null;

      // Advance to next player from the ORIGINAL player who initiated the turn (circular)
      newState.currentPlayerIndex =
        (originalPlayerIndex + 1) % newState.players.length;

      // Increment turn count when wrapping back to first player
      if (newState.currentPlayerIndex === 0) {
        newState.turnCount++;
      }

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
          originalPlayerIndex,
          nextPlayerIndex: newState.currentPlayerIndex,
          nextPlayer: nextPlayer.name,
          subPhase: newState.subPhase,
          turnCount: newState.turnCount,
        }
      );
    }
  }

  return newState;
}

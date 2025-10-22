import { GameState, PlayerTossInFinishedAction, logger } from '@vinto/shapes';
import copy from 'fast-copy';
import { getTargetTypeFromRank } from '../utils/action-utils';
import { areAllHumansReady } from '../utils/toss-in-utils';

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
    logger.warn('[handlePlayerTossInFinished] No active toss-in', {
      playerId: action.payload.playerId,
      phase: state.phase,
      subPhase: state.subPhase,
    });
    return state;
  }

  // Add player to ready list if not already there
  if (!newState.activeTossIn.playersReadyForNextTurn.includes(playerId)) {
    newState.activeTossIn.playersReadyForNextTurn.push(playerId);
  }

  // Check if all human players are ready
  const allHumansReady = areAllHumansReady(newState);

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

      // All cards now use 'choosing-action' phase initially
      // Multi-step actions (J, Q, K) track progress via targets.length

      // Set up pending action for the first queued card
      newState.pendingAction = {
        card: firstAction.card,
        from: 'hand',
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
        '[handlePlayerTossInFinished] All humans ready, no queued actions'
      );

      // Clear pendingAction - GameEngine will handle turn advancement
      newState.pendingAction = null;
    }
  }

  return newState;
}

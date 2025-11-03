import {
  GameState,
  PlayerTossInFinishedAction,
  getCardShortDescription,
  getCardValue,
  logger,
} from '@vinto/shapes';
import copy from 'fast-copy';
import { getTargetTypeFromRank } from '../utils/action-utils';
import { areAllPlayersReady } from '../utils/toss-in-utils';

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

  // Check if all players are ready
  const allReady = areAllPlayersReady(newState);

  // If all players are ready, check if we have queued actions to process
  if (allReady) {
    const hasQueuedActions = newState.activeTossIn.queuedActions.length > 0;

    if (hasQueuedActions) {
      console.log(
        '[handlePlayerTossInFinished] All humans ready, starting to process queued actions',
        {
          queuedCount: newState.activeTossIn.queuedActions.length,
          queuedCards: newState.activeTossIn.queuedActions.map((a) => a.rank),
        }
      );

      // Start processing the first queued action
      const firstAction = newState.activeTossIn.queuedActions[0];

      // All cards now use 'choosing-action' phase initially
      // Multi-step actions (J, Q, K) track progress via targets.length
      // Exception: King (K) should start at 'selecting-target' since it needs immediate declaration

      // Set up pending action for the first queued card
      newState.pendingAction = {
        card: {
          rank: firstAction.rank,
          played: false,
          id: `[handlePlayerTossInFinished]_${Date.now().toString()}`,
          value: getCardValue(firstAction.rank),
          actionText: getCardShortDescription(firstAction.rank),
        },
        from: 'hand',
        playerId: firstAction.playerId,
        actionPhase: firstAction.rank === 'K' ? 'selecting-target' : 'choosing-action',
        targetType: getTargetTypeFromRank(firstAction.rank),
        targets: [],
      };

      console.log(
        '[handlePlayerTossInFinished] Processing first queued action:',
        {
          playerId: firstAction.playerId,
          card: firstAction.rank,
        }
      );

      // CRITICAL: Set currentPlayerIndex to the player executing the queued action
      // This ensures UI shows correct active player and BotAI processes correct bot
      const actionPlayerIndex = newState.players.findIndex(
        (p) => p.id === firstAction.playerId
      );
      if (actionPlayerIndex !== -1) {
        newState.currentPlayerIndex = actionPlayerIndex;
        console.log(
          '[handlePlayerTossInFinished] Set currentPlayerIndex to action player:',
          {
            playerId: firstAction.playerId,
            playerIndex: actionPlayerIndex,
          }
        );
      }

      const isHumanPlayer =
        newState.players.find((p) => p.id === firstAction.playerId)?.isHuman ??
        false;
      // Transition to selecting or awaiting_action for human phase to process the queued action
      // we skip selecting for human because they have an ability to skip action on UI
      newState.subPhase = isHumanPlayer ? 'awaiting_action' : 'selecting';
      newState.activeTossIn.waitingForInput = false;
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

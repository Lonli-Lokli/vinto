import {
  GameState,
  PlayerTossInFinishedAction,
  getCardShortDescription,
  getCardValue,
  logger,
} from '@vinto/shapes';
import { copy } from 'fast-copy';
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

  if (allReady) {
    const hasQueuedActions = newState.activeTossIn.queuedActions.length > 0;

    if (hasQueuedActions) {
      console.log(
        '[handlePlayerTossInFinished] All players ready, starting queued actions',
        {
          queuedCount: newState.activeTossIn.queuedActions.length,
          queuedCards: newState.activeTossIn.queuedActions.map((a) => a.rank),
        }
      );

      // Start processing first queued action
      const firstAction = newState.activeTossIn.queuedActions[0];

      // FIX BUG 2: ALL toss in actions start at 'selecting-target' phase
      // UI still allows allows players to skip the action if desired (including King)
      newState.pendingAction = {
        card: {
          rank: firstAction.rank,
          played: false,
          id: `tossin_queued_${Date.now()}`,
          value: getCardValue(firstAction.rank),
          actionText: getCardShortDescription(firstAction.rank),
        },
        from: 'hand',
        playerId: firstAction.playerId,
        actionPhase: 'selecting-target',
        targetType: getTargetTypeFromRank(firstAction.rank),
        targets: [],
      };

      console.log(
        '[handlePlayerTossInFinished] Processing first queued action:',
        {
          playerId: firstAction.playerId,
          rank: firstAction.rank,
          actionPhase: 'choosing-action', // Logged for clarity
        }
      );

      // Set currentPlayerIndex to action player
      const actionPlayerIndex = newState.players.findIndex(
        (p) => p.id === firstAction.playerId
      );
      if (actionPlayerIndex !== -1) {
        newState.currentPlayerIndex = actionPlayerIndex;
        console.log('[handlePlayerTossInFinished] Set currentPlayerIndex:', {
          playerId: firstAction.playerId,
          playerIndex: actionPlayerIndex,
        });
      }

      const isHumanPlayer =
        newState.players.find((p) => p.id === firstAction.playerId)?.isHuman ??
        false;

      // Human: awaiting_action (UI shows skip/use buttons)
      // Bot: selecting (bot will decide automatically)
      newState.subPhase = isHumanPlayer ? 'awaiting_action' : 'selecting';
      newState.activeTossIn.waitingForInput = false;
    } else {
      console.log(
        '[handlePlayerTossInFinished] All players ready, no queued actions'
      );

      // Clear pendingAction - GameEngine will handle turn advancement
      newState.pendingAction = null;
    }
  }

  return newState;
}

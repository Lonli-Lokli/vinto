import { ExecuteQueenSwapAction, GameState } from '@vinto/shapes';
import copy from 'fast-copy';
import {
  clearTossInReadyList,
  getAutomaticallyReadyPlayers,
} from '../utils/toss-in-utils';

/**
 * EXECUTE_QUEEN_SWAP Handler
 *
 * Flow:
 * 1. Player has used a Queen card action
 * 2. Player has selected 2 cards to peek at (stored in pendingAction.targets)
 * 3. Player chooses to swap those two cards (Queen's special ability)
 * 4. Swap the two target cards between players/positions
 * 5. Move Queen card to discard pile
 * 6. Complete turn (increment turn count, transition to idle)
 *
 * Note: Queen allows peeking at 2 cards then optionally swapping them
 * This handler executes the swap; SKIP_QUEEN_SWAP skips it
 */
export function handleExecuteQueenSwap(
  state: GameState,
  _action: ExecuteQueenSwapAction
): GameState {
  // Create new state (deep copy for safety)
  const newState = copy(state);

  // Get the two targets from pending action
  const targets = newState.pendingAction!.targets;
  const [target1, target2] = targets;

  // Find the two target players
  const player1 = newState.players.find((p) => p.id === target1.playerId);
  const player2 = newState.players.find((p) => p.id === target2.playerId);

  if (!player1 || !player2) {
    // Should never happen due to validation
    return state;
  }

  // Swap the two cards
  const card1 = player1.cards[target1.position];
  const card2 = player2.cards[target2.position];

  player1.cards[target1.position] = card2;
  player2.cards[target2.position] = card1;

  // Update known card positions after swap
  // Queen action: Player peeked at both cards before swapping, so they know:
  // - card2 is now at player1's position
  // - card1 is now at player2's position
  // The current player (who used Queen) now knows these positions
  const currentPlayer = newState.players[newState.currentPlayerIndex];

  // If current player is player1, they now know their new card at target1.position
  if (currentPlayer.id === player1.id) {
    if (!currentPlayer.knownCardPositions.includes(target1.position)) {
      currentPlayer.knownCardPositions.push(target1.position);
    }
  }

  // If current player is player2, they now know their new card at target2.position
  if (currentPlayer.id === player2.id) {
    if (!currentPlayer.knownCardPositions.includes(target2.position)) {
      currentPlayer.knownCardPositions.push(target2.position);
    }
  }

  const queenCard = newState.pendingAction?.card;

  // Move Queen card to discard pile
  if (queenCard) {
    newState.discardPile.addToTop({
      ...copy(queenCard),
      played: true
    });
  }

  // Clear pending action
  newState.pendingAction = null;

  // Check if we're processing a toss-in queue
  const isProcessingTossInQueue =
    newState.activeTossIn !== null &&
    newState.activeTossIn.queuedActions.length > 0;

  if (isProcessingTossInQueue) {
    // Remove the processed action from the queue
    newState.activeTossIn!.queuedActions.shift();

    console.log(
      '[handleExecuteQueenSwap] Action completed during toss-in queue processing',
      {
        remainingActions: newState.activeTossIn!.queuedActions.length,
      }
    );

    // Check if there are more queued actions
    if (newState.activeTossIn!.queuedActions.length > 0) {
      // Process next queued action
      const nextAction = newState.activeTossIn!.queuedActions[0];

      newState.pendingAction = {
        card: nextAction.card,
        playerId: nextAction.playerId,
        actionPhase: 'choosing-action',
        targets: [],
      };

      newState.subPhase = 'awaiting_action';

      console.log('[handleExecuteQueenSwap] Processing next queued action:', {
        playerId: nextAction.playerId,
        card: nextAction.card.rank,
      });
    } else {
      // No more queued actions - clear pendingAction
      // GameEngine will handle turn advancement automatically
      newState.pendingAction = null;

      console.log(
        '[handleExecuteQueenSwap] All toss-in actions processed, turn will advance'
      );
    }
  } else if (newState.activeTossIn !== null) {
    // Return to toss-in phase (action was from toss-in participation but no queue)
    // Clear the ready list so players can confirm again for this new toss-in round
    clearTossInReadyList(newState);
    newState.subPhase = 'toss_queue_active';
    newState.activeTossIn.waitingForInput = true;
    console.log(
      '[handleExecuteQueenSwap] Queen swap executed during toss-in, returning to toss-in phase (ready list cleared)'
    );
  } else {
    // Initialize new toss-in phase (normal turn flow)
    if (queenCard) {
      newState.activeTossIn = {
        rank: queenCard.rank,
        initiatorId: _action.payload.playerId,
        originalPlayerIndex: newState.currentPlayerIndex,
        participants: [],
        queuedActions: [],
        waitingForInput: true,
        playersReadyForNextTurn: getAutomaticallyReadyPlayers(newState.players),
      };
    }

    // Transition to toss-in phase
    newState.subPhase = 'toss_queue_active';

    console.log('[handleExecuteQueenSwap] Queen swap executed, toss-in active');
  }

  return newState;
}

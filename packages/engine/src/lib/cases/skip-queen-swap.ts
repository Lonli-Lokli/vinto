import { GameState, SkipQueenSwapAction } from '@vinto/shapes';
import copy from 'fast-copy';
import {
  addTossInCard,
  clearTossInReadyList,
  getAutomaticallyReadyPlayers,
} from '../utils/toss-in-utils';

/**
 * SKIP_QUEEN_SWAP Handler
 *
 * Flow:
 * 1. Player has used a Queen card action
 * 2. Player has selected 2 cards to peek at (stored in pendingAction.targets)
 * 3. Player chooses NOT to swap those two cards (declined Queen's ability)
 * 4. Move Queen card to discard pile (without swapping)
 * 5. Complete turn (increment turn count, transition to idle)
 *
 * Note: Same as EXECUTE_QUEEN_SWAP but skips the actual swap
 */
export function handleSkipQueenSwap(
  state: GameState,
  _action: SkipQueenSwapAction
): GameState {
  // Create new state (deep copy for safety)
  const newState = copy(state);

  const queenCard = newState.pendingAction?.card;

  // Move Queen card to discard pile (skip the swap)
  if (queenCard) {
    newState.discardPile.addToTop({
      ...copy(queenCard),
      played: true,
    });
  }

  // TODO: we should update knowledge even for skipped swap - players know the cards they peeked at remain in same positions
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
      '[handleSkipQueenSwap] Action completed during toss-in queue processing',
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

      console.log('[handleSkipQueenSwap] Processing next queued action:', {
        playerId: nextAction.playerId,
        card: nextAction.card.rank,
      });
    } else {
      // No more queued actions - clear pendingAction
      // GameEngine will handle turn advancement automatically
      newState.pendingAction = null;

      console.log(
        '[handleSkipQueenSwap] All toss-in actions processed, turn will advance'
      );
    }
  } else if (newState.activeTossIn !== null) {
    // Return to toss-in phase (action was from toss-in participation but no queue)
    // ADD or REPLACE this card's rank to toss-in ranks if not already present
    newState.activeTossIn.ranks = addTossInCard(
      newState.activeTossIn.ranks,
      queenCard?.rank
    );
    
    // Clear the ready list so players can confirm again for this new toss-in round
    clearTossInReadyList(newState);
    newState.subPhase = 'toss_queue_active';
    newState.activeTossIn.waitingForInput = true;
    console.log(
      '[handleSkipQueenSwap] Queen swap skipped during toss-in, rank added, returning to toss-in phase (ready list cleared)',
      { ranks: newState.activeTossIn.ranks }
    );
  } else {
    // Initialize new toss-in phase (normal turn flow)
    if (queenCard) {
      // Players who called VINTO are automatically marked as ready (can't participate in toss-in)
      newState.activeTossIn = {
        ranks: [queenCard.rank],
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

    console.log('[handleSkipQueenSwap] Queen swap skipped, toss-in active');
  }

  return newState;
}

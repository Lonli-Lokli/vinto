import { GameState, SkipQueenSwapAction } from '@/shared';
import copy from 'fast-copy';

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
    newState.discardPile.addToTop(queenCard);
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
      // No more queued actions, finish toss-in and advance turn
      const originalPlayerIndex = newState.activeTossIn!.originalPlayerIndex;
      newState.activeTossIn = null;

      newState.currentPlayerIndex =
        (originalPlayerIndex + 1) % newState.players.length;

      if (newState.currentPlayerIndex === 0) {
        newState.turnCount++;
      }

      const nextPlayer = newState.players[newState.currentPlayerIndex];
      newState.subPhase = nextPlayer.isBot ? 'ai_thinking' : 'idle';

      console.log(
        '[handleSkipQueenSwap] All toss-in actions processed, turn advanced'
      );
    }
  } else if (newState.activeTossIn !== null) {
    // Return to toss-in phase (action was from toss-in participation but no queue)
    newState.subPhase = 'toss_queue_active';
    newState.activeTossIn.waitingForInput = true;
    console.log(
      '[handleSkipQueenSwap] Queen swap skipped during toss-in, returning to toss-in phase'
    );
  } else {
    // Initialize new toss-in phase (normal turn flow)
    if (queenCard) {
      // Players who called VINTO are automatically marked as ready (can't participate in toss-in)
      const playersAlreadyReady = newState.players
        .filter((p) => p.isVintoCaller)
        .map((p) => p.id);

      newState.activeTossIn = {
        rank: queenCard.rank,
        initiatorId: _action.payload.playerId,
        originalPlayerIndex: newState.currentPlayerIndex,
        participants: [],
        queuedActions: [],
        waitingForInput: true,
        playersReadyForNextTurn: playersAlreadyReady,
      };
    }

    // Transition to toss-in phase
    newState.subPhase = 'toss_queue_active';

    console.log('[handleSkipQueenSwap] Queen swap skipped, toss-in active');
  }

  return newState;
}

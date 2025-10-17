import { GameState, SkipJackSwapAction } from '@/shared';
import copy from 'fast-copy';
import {
  clearTossInReadyList,
  getAutomaticallyReadyPlayers,
} from '../utils/toss-in-utils';

/**
 * SKIP_JACK_SWAP Handler
 *
 * Flow:
 * 1. Player has used a Jack card action
 * 2. Player has selected 2 cards (stored in pendingAction.targets)
 * 3. Player chooses NOT to swap those two cards (declined Jack's ability)
 * 4. Move Jack card to discard pile (without swapping)
 * 5. Complete turn (increment turn count, transition to idle)
 *
 * Note: Same as EXECUTE_JACK_SWAP but skips the actual swap
 */
export function handleSkipJackSwap(
  state: GameState,
  _action: SkipJackSwapAction
): GameState {
  // Create new state (deep copy for safety)
  const newState = copy(state);

  const jackCard = newState.pendingAction?.card;

  // Move Jack card to discard pile (skip the swap)
  if (jackCard) {
    newState.discardPile.addToTop(jackCard);
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
      '[handleSkipJackSwap] Action completed during toss-in queue processing',
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

      console.log('[handleSkipJackSwap] Processing next queued action:', {
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

      // Check if game should end (after vinto call, when we return to the vinto caller)
      if (
        newState.phase === 'final' &&
        newState.players[newState.currentPlayerIndex].id ===
          newState.vintoCallerId
      ) {
        // Final round complete - end the game
        newState.phase = 'scoring';
        newState.subPhase = 'idle';

        console.log('[handleSkipJackSwap] Final round complete, game finished');

        return newState;
      }

      const nextPlayer = newState.players[newState.currentPlayerIndex];
      newState.subPhase = nextPlayer.isBot ? 'ai_thinking' : 'idle';

      console.log(
        '[handleSkipJackSwap] All toss-in actions processed, turn advanced'
      );
    }
  } else if (newState.activeTossIn !== null) {
    // Return to toss-in phase (action was from toss-in participation but no queue)
    // Clear the ready list so players can confirm again for this new toss-in round
    clearTossInReadyList(newState);
    newState.subPhase = 'toss_queue_active';
    newState.activeTossIn.waitingForInput = true;
    console.log(
      '[handleSkipJackSwap] Jack swap skipped during toss-in, returning to toss-in phase (ready list cleared)'
    );
  } else {
    // Initialize new toss-in phase (normal turn flow)
    if (jackCard) {
      // Players who called VINTO are automatically marked as ready (can't participate in toss-in)
      newState.activeTossIn = {
        rank: jackCard.rank,
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

    console.log('[handleSkipJackSwap] Jack swap skipped, toss-in active');
  }

  return newState;
}

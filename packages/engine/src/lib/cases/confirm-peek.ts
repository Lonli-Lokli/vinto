import { GameState, ConfirmPeekAction } from '@vinto/shapes';
import copy from 'fast-copy';
import {
  clearTossInReadyList,
  getAutomaticallyReadyPlayers,
} from '../utils/toss-in-utils';

/**
 * CONFIRM_PEEK Handler
 *
 * Flow:
 * 1. Player has peeked at a card (7, 8, 9, or 10 action)
 * 2. Confirming they've seen it and are ready to continue
 * 3. Move card to discard pile
 * 4. Clear pending action
 * 5. Increment turn and transition to idle
 *
 * Note: The actual peek happens in SELECT_ACTION_TARGET
 * This just confirms and completes the turn
 */
export function handleConfirmPeek(
  state: GameState,
  _action: ConfirmPeekAction
): GameState {
  // Create new state (deep copy for safety)
  const newState = copy(state);

  const peekCard = newState.pendingAction?.card;

  // Move action card to discard pile
  if (peekCard) {
    newState.discardPile.addToTop({
      ...copy(peekCard),
      played: true
    });
  }

  // Clear pending action
  newState.pendingAction = null;

  // Check if we're processing a toss-in queue
  if (
    newState.activeTossIn !== null &&
    newState.activeTossIn.queuedActions.length > 0
  ) {
    // Remove the processed action from the queue
    newState.activeTossIn.queuedActions.shift();

    console.log(
      '[handleConfirmPeek] Action completed during toss-in queue processing',
      {
        remainingActions: newState.activeTossIn.queuedActions.length,
      }
    );

    // Check if there are more queued actions
    if (newState.activeTossIn.queuedActions.length > 0) {
      // Process next queued action
      const nextAction = newState.activeTossIn.queuedActions[0];

      newState.pendingAction = {
        card: nextAction.card,
        playerId: nextAction.playerId,
        actionPhase: 'choosing-action',
        targets: [],
      };

      newState.subPhase = 'awaiting_action';

      console.log('[handleConfirmPeek] Processing next queued action:', {
        playerId: nextAction.playerId,
        card: nextAction.card.rank,
      });
    } else {
      // No more queued actions - clear pendingAction
      // GameEngine will handle turn advancement automatically
      newState.pendingAction = null;

      console.log(
        '[handleConfirmPeek] All toss-in actions processed, turn will advance'
      );
    }
  } else if (newState.activeTossIn !== null) {
    // Return to toss-in phase (action was from toss-in participation but no queue)
    // Clear the ready list so players can confirm again for this new toss-in round
    clearTossInReadyList(newState);
    newState.subPhase = 'toss_queue_active';
    newState.activeTossIn.waitingForInput = true;

    console.log(
      '[handleConfirmPeek] Peek confirmed during toss-in, returning to toss-in phase (ready list cleared)'
    );
  } else {
    // Initialize new toss-in phase (normal turn flow)
    if (peekCard) {
      newState.activeTossIn = {
        ranks: [peekCard.rank],
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

    console.log('[handleConfirmPeek] Peek confirmed, toss-in active');
  }

  return newState;
}

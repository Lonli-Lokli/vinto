import { GameState, ConfirmPeekAction } from '@/shared';
import copy from 'fast-copy';

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
    newState.discardPile.addToTop(peekCard);
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
      // No more queued actions, finish toss-in and advance turn
      const originalPlayerIndex = newState.activeTossIn.originalPlayerIndex;
      newState.activeTossIn = null;

      // Advance to next player from original player
      newState.currentPlayerIndex =
        (originalPlayerIndex + 1) % newState.players.length;

      if (newState.currentPlayerIndex === 0) {
        newState.turnCount++;
      }

      const nextPlayer = newState.players[newState.currentPlayerIndex];
      newState.subPhase = nextPlayer.isBot ? 'ai_thinking' : 'idle';

      console.log(
        '[handleConfirmPeek] All toss-in actions processed, turn advanced'
      );
    }
  } else if (newState.activeTossIn !== null) {
    // Return to toss-in phase (action was from toss-in participation but no queue)
    newState.subPhase = 'toss_queue_active';
    newState.activeTossIn.waitingForInput = true;

    console.log(
      '[handleConfirmPeek] Peek confirmed during toss-in, returning to toss-in phase'
    );
  } else {
    // Initialize new toss-in phase (normal turn flow)
    if (peekCard) {
      // Players who called VINTO are automatically marked as ready (can't participate in toss-in)
      const playersAlreadyReady = newState.players
        .filter((p) => p.isVintoCaller)
        .map((p) => p.id);

      newState.activeTossIn = {
        rank: peekCard.rank,
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

    console.log('[handleConfirmPeek] Peek confirmed, toss-in active');
  }

  return newState;
}

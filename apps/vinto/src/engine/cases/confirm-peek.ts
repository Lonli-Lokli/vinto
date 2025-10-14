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

  // Initialize toss-in phase
  if (peekCard) {
    // Players who called VINTO are automatically marked as ready (can't participate in toss-in)
    const playersAlreadyReady = newState.players
      .filter((p) => p.isVintoCaller)
      .map((p) => p.id);

    newState.activeTossIn = {
      rank: peekCard.rank,
      initiatorId: _action.payload.playerId,
      participants: [],
      queuedActions: [],
      waitingForInput: true,
      playersReadyForNextTurn: playersAlreadyReady,
    };
  }

  // Transition to toss-in phase
  newState.subPhase = 'toss_queue_active';

  console.log('[handleConfirmPeek] Peek confirmed, toss-in active');

  return newState;
}

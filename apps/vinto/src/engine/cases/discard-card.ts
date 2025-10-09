import { GameState, DiscardCardAction } from '@/shared';
import copy from 'fast-copy';

/**
 * DISCARD_CARD Handler
 *
 * Flow:
 * 1. Player has drawn a card and decided to discard it (or swapped it)
 * 2. The card to discard is in pendingAction
 * 3. Add that card to discard pile
 * 4. Clear pendingAction
 * 5. Initialize toss-in phase
 */
export function handleDiscardCard(
  state: GameState,
  action: DiscardCardAction
): GameState {
  const { playerId } = action.payload;

  // Create new state (deep copy for safety)
  const newState = copy(state);

  // The card to discard is in pendingAction
  if (!newState.pendingAction?.card) {
    console.warn('[handleDiscardCard] No card in pendingAction to discard');
    return state;
  }

  const discardedCard = newState.pendingAction.card;
  newState.discardPile.unshift(discardedCard);

  // Clear pending action
  newState.pendingAction = null;

  // Initialize toss-in phase
  newState.activeTossIn = {
    rank: discardedCard.rank,
    initiatorId: playerId,
    participants: [],
    queuedActions: [],
    waitingForInput: true,
    playersReadyForNextTurn: [],
  };

  // Transition to toss-in phase
  newState.subPhase = 'toss_queue_active';

  console.log('[handleDiscardCard] Card discarded, toss-in active:', {
    discardedRank: discardedCard.rank,
    newSubPhase: newState.subPhase,
  });

  return newState;
}

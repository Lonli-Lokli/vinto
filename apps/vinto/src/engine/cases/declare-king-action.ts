import { GameState, DeclareKingActionAction } from '../types';
import copy from 'fast-copy';

/**
 * DECLARE_KING_ACTION Handler
 *
 * Flow:
 * 1. Player has used a King card (via USE_CARD_ACTION)
 * 2. Player declares a rank (A, 7, 8, 9, 10, J, Q - not K)
 * 3. A toss-in is triggered for that rank
 * 4. All other players must discard any cards of the declared rank
 * 5. Move King card to discard pile
 * 6. Complete turn (increment turn count, transition to idle)
 *
 * Note: King's ability triggers a toss-in, forcing others to discard matching ranks
 * In this MVP, we'll just complete the turn. Full toss-in logic will be in PARTICIPATE_IN_TOSS_IN
 */
export function handleDeclareKingAction(
  state: GameState,
  action: DeclareKingActionAction
): GameState {
  const { playerId, declaredRank } = action.payload;

  // Create new state (deep copy for safety)
  const newState = copy(state);

  // Move King card to discard pile
  if (newState.pendingAction?.card) {
    newState.discardPile.push(newState.pendingAction.card);
  }

  // Trigger toss-in for the declared rank
  // In a full implementation, this would set up activeTossIn
  // For MVP: we'll create the toss-in structure but not fully process it
  newState.activeTossIn = {
    rank: declaredRank, // Note: property is 'rank' not 'declaredRank'
    initiatorId: playerId,
    participants: [], // Other players will participate via PARTICIPATE_IN_TOSS_IN
    queuedActions: [],
    waitingForInput: true,
  };

  // Clear pending action
  newState.pendingAction = null;

  // Increment turn count
  newState.turnCount += 1;

  // Transition to idle (turn complete)
  // In full game, might transition to 'toss_in' phase
  newState.subPhase = 'idle';

  return newState;
}

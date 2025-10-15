import { GameState, DeclareKingActionAction } from '@/shared';
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
    newState.discardPile.addToTop(newState.pendingAction.card);
  }

  // Clear pending action
  newState.pendingAction = null;

  // Check if this action was part of a toss-in

  if (newState.activeTossIn !== null) {
    // Return to toss-in phase (action was from toss-in participation)
    newState.subPhase = 'toss_queue_active';
    newState.activeTossIn.waitingForInput = true;
    console.log(
      '[handleDeclareKingAction] King action during toss-in complete, returning to toss-in phase'
    );
  } else {
    // Trigger NEW toss-in for the declared rank (normal turn flow)
    // King forces all players to toss in cards matching the declared rank
    // Players who called VINTO are automatically marked as ready (can't participate in toss-in)
    const playersAlreadyReady = newState.players
      .filter((p) => p.isVintoCaller)
      .map((p) => p.id);

    newState.activeTossIn = {
      rank: declaredRank,
      initiatorId: playerId,
      originalPlayerIndex: newState.currentPlayerIndex,
      participants: [],
      queuedActions: [],
      waitingForInput: true,
      playersReadyForNextTurn: playersAlreadyReady,
    };

    // Transition to toss-in phase
    newState.subPhase = 'toss_queue_active';

    console.log(
      '[handleDeclareKingAction] King action complete, toss-in active:',
      {
        declaredRank,
        newSubPhase: newState.subPhase,
      }
    );
  }

  return newState;
}

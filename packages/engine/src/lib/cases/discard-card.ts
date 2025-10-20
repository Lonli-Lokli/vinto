import { GameState, DiscardCardAction, logger } from '@vinto/shapes';
import copy from 'fast-copy';
import {
  getAutomaticallyReadyPlayers,
  areAllHumansReady,
} from '../utils/toss-in-utils';

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
    logger.warn('[handleDiscardCard] No card in pendingAction to discard', {
      playerId,
      phase: state.phase,
      subPhase: state.subPhase,
      hasPendingAction: !!state.pendingAction,
    });
    return state;
  }

  const discardedCard = newState.pendingAction.card;
  newState.discardPile.addToTop(discardedCard);

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
      '[handleDiscardCard] Action discarded during toss-in queue processing',
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

      console.log('[handleDiscardCard] Processing next queued action:', {
        playerId: nextAction.playerId,
        card: nextAction.card.rank,
      });
    } else {
      // No more queued actions - clear pendingAction
      // GameEngine will handle turn advancement automatically
      newState.pendingAction = null;

      console.log(
        '[handleDiscardCard] All toss-in actions processed, turn will advance'
      );
    }
  } else {
    // Normal discard flow - initialize new toss-in phase
    newState.activeTossIn = {
      rank: discardedCard.rank,
      initiatorId: playerId,
      originalPlayerIndex: newState.currentPlayerIndex,
      participants: [],
      queuedActions: [],
      waitingForInput: true,
      playersReadyForNextTurn: getAutomaticallyReadyPlayers(newState.players),
    };

    // Check if all human players are already ready (e.g., Vinto caller is the only human)
    const allHumansReady = areAllHumansReady(newState);

    if (allHumansReady) {
      // All humans are ready immediately, skip toss-in and advance turn
      // Clear pendingAction - GameEngine will handle turn advancement automatically
      console.log(
        '[handleDiscardCard] All humans already ready (Vinto caller), toss-in will be skipped'
      );

      newState.pendingAction = null;
    } else {
      // Some humans need to decide on toss-in participation
      newState.subPhase = 'toss_queue_active';

      console.log('[handleDiscardCard] Card discarded, toss-in active:', {
        discardedRank: discardedCard.rank,
        newSubPhase: newState.subPhase,
      });
    }
  }

  return newState;
}

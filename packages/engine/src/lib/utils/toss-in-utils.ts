// utils/toss-in-utils.ts
// Utility functions for managing toss-in phase state

import {
  Card,
  GameState,
  getCardShortDescription,
  getCardValue,
  PlayerState,
  Rank,
} from '@vinto/shapes';
import { getTargetTypeFromRank } from './action-utils';

/**
 * Get the list of player IDs who should be automatically marked as ready for toss-in.
 * This includes:
 * - Players who have called Vinto (they cannot participate in toss-in after calling Vinto)
 * - Players in a coalition who are not the leader (they don't control their own turns)
 *
 * @param players - Array of all players in the game
 * @param coalitionLeaderId - ID of the coalition leader (if any)
 * @returns Array of player IDs who are automatically ready
 */
export function getAutomaticallyReadyPlayers(
  players: PlayerState[],
  coalitionLeaderId: string | null
): string[] {
  return players
    .filter((p) => {
      // Vinto callers are automatically ready
      if (p.isVintoCaller) return true;

      // Coalition members who are not the leader are automatically ready
      if (
        coalitionLeaderId &&
        p.coalitionWith.length > 0 &&
        p.id !== coalitionLeaderId
      ) {
        return true;
      }

      return false;
    })
    .map((p) => p.id);
}

/**
 * Clear the ready list for a toss-in, resetting it to only include
 * players who should be automatically marked as ready (e.g., Vinto callers, coalition members).
 *
 * This should be called when:
 * 1. A new toss-in round starts after a queued action completes
 * 2. Returning to toss-in phase after processing an action during toss-in
 *
 * @param state - Current game state (will be mutated)
 */
export function clearTossInReadyList(state: GameState): void {
  if (state.activeTossIn) {
    state.activeTossIn.playersReadyForNextTurn = getAutomaticallyReadyPlayers(
      state.players,
      state.coalitionLeaderId
    );
  }
}

export function areAllPlayersReady(state: GameState): boolean {
  if (!state.activeTossIn) {
    return false;
  }

  return (
    state.players.length === state.activeTossIn.playersReadyForNextTurn.length
  );
}

/**
 * Advance turn after toss-in completes
 *
 * This function handles turn advancement when all toss-in actions have been processed.
 * It clears toss-in participation data while preserving the current ranks.
 *
 * IMPORTANT: The ranks are NOT reset here because they were set correctly when cards
 * were discarded. For example, if King declared Ace correctly, activeTossIn.ranks
 * contains ['K', 'A'], and we must preserve both ranks for the next turn.
 *
 * @param state - Current game state (will be mutated)
 * @param context - Context string for logging (e.g., handler name)
 */
export function advanceTurnAfterTossIn(
  state: GameState,
  context: string
): void {
  if (!state.activeTossIn) {
    console.warn(`[${context}] No active toss-in to complete`);
    return;
  }

  // Save who started this turn before refreshing toss-in
  const originalPlayerIndex = state.activeTossIn.originalPlayerIndex;

  // Calculate what the next player index would be
  const nextPlayerIndex = (originalPlayerIndex + 1) % state.players.length;

    // Check if game should end BEFORE advancing (after vinto call, when we return to the vinto caller)
  if (
    state.phase === 'final' &&
    state.players[nextPlayerIndex].id === state.vintoCallerId
  ) {
    // Final round complete - end the game
    state.phase = 'scoring';
    state.subPhase = 'idle';
    state.activeTossIn = null; // Clear toss-in state

    console.log(`[${context}] Final round complete, game finished`);
    return;
  }

  // Advance to next player from the ORIGINAL player who initiated the turn (circular)
  state.currentPlayerIndex = nextPlayerIndex;

  state.turnNumber++;

  // Increment turn count when wrapping back to first player
  if (state.currentPlayerIndex === 0) {
    state.roundNumber++;
    state.roundFailedAttempts = [];
  }

  if (state.drawPile.length === 1) {
    state.drawPile.reshuffleFrom(state.discardPile);
  }

  if (state.pendingAction) {
    state.subPhase = 'awaiting_action';

    // Mark toss-in as not waiting for input (processing queue)
    state.activeTossIn.waitingForInput = false;
  }

  // Clear toss-in participation data (but preserve ranks)
  // The ranks stay the same because they represent the cards currently in discard pile
  // For example: King + Ace declared correctly = ['K', 'A'] should persist
  state.activeTossIn.participants = [];
  state.activeTossIn.queuedActions = [];
  state.activeTossIn.waitingForInput = false;
  state.activeTossIn.playersReadyForNextTurn = getAutomaticallyReadyPlayers(
    state.players,
    state.coalitionLeaderId
  );
  state.activeTossIn.failedAttempts = [];
  state.activeTossIn.originalPlayerIndex = state.currentPlayerIndex;


  // Get the new current player
  const nextPlayer = state.players[state.currentPlayerIndex];

  // Transition to appropriate phase based on player type
  if (nextPlayer.isBot) {
    state.subPhase = 'ai_thinking';
  } else {
    state.subPhase = 'idle';
  }

  console.log(`[${context}] Toss-in complete, turn advanced to:`, {
    originalPlayerIndex,
    nextPlayerIndex: state.currentPlayerIndex,
    nextPlayer: nextPlayer.name,
    subPhase: state.subPhase,
    turnCount: state.turnNumber,
    tossInRefreshedWithRanks: state.activeTossIn?.ranks || null,
  });
}

export function addTossInCard(
  currentTossInRanks: [Rank, ...Rank[]],
  rank?: Rank
): [Rank, ...Rank[]] {
  if (rank && !currentTossInRanks.includes(rank)) {
    return currentTossInRanks.includes('K')
      ? [...currentTossInRanks, rank]
      : [rank];
  } else return currentTossInRanks;
}

export function clearTossInAfterActionableCard(
  pendingCard: Card | undefined,
  newState: GameState,
  playerId: string
): void {
  if (newState.activeTossIn && newState.activeTossIn.queuedActions.length > 0) {
    // card is from toss in queue, move it to discard pile to be below top, possible unplayed card
    if (pendingCard) {
      newState.discardPile.addBeforeTop(pendingCard);
    }
  } else {
    if (pendingCard) {
      newState.discardPile.addToTop(pendingCard);
    }
  }
  if (newState.activeTossIn === null) {
    // Initialize new toss-in phase (normal turn flow)
    if (pendingCard?.rank) {
      newState.activeTossIn = {
        ranks: [pendingCard.rank],
        initiatorId: playerId,
        originalPlayerIndex: newState.currentPlayerIndex,
        participants: [],
        queuedActions: [],
        waitingForInput: true,
        playersReadyForNextTurn: getAutomaticallyReadyPlayers(
          newState.players,
          newState.coalitionLeaderId
        ),
      };
    }

    newState.pendingAction = null;
    // Transition to toss-in phase
    newState.subPhase = 'toss_queue_active';

    console.log(
      '[clearTossInAfterActionableCard] Peek confirmed, toss-in active'
    );
  } else if (newState.activeTossIn.queuedActions.length === 0) {
    // ADD or REPLACE this card's rank to toss-in ranks if not already present if same turn
    newState.activeTossIn.ranks =
      newState.pendingAction?.from === 'drawing'
        ? [newState.pendingAction.card.rank]
        : addTossInCard(
            newState.activeTossIn.ranks,
            newState.pendingAction?.card.rank
          );

    newState.pendingAction = null;
    // Clear the ready list so players can confirm again for this new toss-in round
    clearTossInReadyList(newState);

    newState.subPhase = 'toss_queue_active';
    newState.activeTossIn.waitingForInput = true;

    console.log(
      '[clearTossInAfterActionableCard] Peek confirmed during toss-in, rank added, returning to toss-in phase (ready list cleared)',
      {
        ranks: newState.activeTossIn.ranks,
        restoredCurrentPlayerIndex: newState.currentPlayerIndex,
      }
    );
  } else if (newState.activeTossIn.queuedActions.length > 0) {
    // Clear pending action
    newState.pendingAction = null;

    // Remove the processed action from the queue
    newState.activeTossIn.queuedActions.shift();

    console.log(
      '[clearTossInAfterActionableCard] Action completed during toss-in queue processing',
      {
        remainingActions: newState.activeTossIn.queuedActions.length,
      }
    );

    // Check if there are more queued actions
    if (newState.activeTossIn.queuedActions.length > 0) {
      // Process next queued action
      const nextAction = newState.activeTossIn.queuedActions[0];

      newState.pendingAction = {
        card: {
          // todo: consider using real card from player
          rank: nextAction.rank,
          played: false,
          id: `[toss-in-utils]_${Date.now().toString()}`,
          value: getCardValue(nextAction.rank),
          actionText: getCardShortDescription(nextAction.rank),
        },
        from: 'hand',
        playerId: nextAction.playerId,
        actionPhase: 'choosing-action',
        targetType: getTargetTypeFromRank(nextAction.rank),
        targets: [],
      };

      // Set currentPlayerIndex to the player executing this queued action
      const actionPlayerIndex = newState.players.findIndex(
        (p) => p.id === nextAction.playerId
      );
      if (actionPlayerIndex !== -1) {
        newState.currentPlayerIndex = actionPlayerIndex;
      }

      newState.subPhase = 'awaiting_action';

      console.log(
        '[clearTossInAfterActionableCard] Processing next queued action:',
        {
          playerId: nextAction.playerId,
          rank: nextAction.rank,
          currentPlayerIndex: newState.currentPlayerIndex,
        }
      );
    } else {
      // Clear the ready list so players can confirm again for this new toss-in round
      clearTossInReadyList(newState);
      newState.subPhase = 'toss_queue_active'; // Return to toss-in phase to allow human call vinto
    }
  }
}

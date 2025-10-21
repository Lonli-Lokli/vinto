// utils/toss-in-utils.ts
// Utility functions for managing toss-in phase state

import { GameState, PlayerState, Rank } from '@vinto/shapes';

/**
 * Get the list of player IDs who should be automatically marked as ready for toss-in.
 * Currently, only players who have called Vinto are automatically marked as ready
 * (they cannot participate in toss-in after calling Vinto).
 *
 * @param players - Array of all players in the game
 * @returns Array of player IDs who are automatically ready
 */
export function getAutomaticallyReadyPlayers(players: PlayerState[]): string[] {
  return players.filter((p) => p.isVintoCaller).map((p) => p.id);
}

/**
 * Clear the ready list for a toss-in, resetting it to only include
 * players who should be automatically marked as ready (e.g., Vinto callers).
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
      state.players
    );
  }
}

/**
 * Check if all human players have confirmed they're ready for the next turn.
 *
 * @param state - Current game state
 * @returns True if all human players are marked as ready
 */
export function areAllHumansReady(state: GameState): boolean {
  if (!state.activeTossIn) {
    return false;
  }

  const humanPlayers = state.players.filter((p) => p.isHuman);
  return humanPlayers.every((p) =>
    state.activeTossIn!.playersReadyForNextTurn.includes(p.id)
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

  // Clear toss-in participation data (but preserve ranks)
  // The ranks stay the same because they represent the cards currently in discard pile
  // For example: King + Ace declared correctly = ['K', 'A'] should persist
  state.activeTossIn.participants = [];
  state.activeTossIn.queuedActions = [];
  state.activeTossIn.waitingForInput = false;
  state.activeTossIn.playersReadyForNextTurn = getAutomaticallyReadyPlayers(
    state.players
  );
  state.activeTossIn.failedAttempts = [];

  // Advance to next player from the ORIGINAL player who initiated the turn (circular)
  state.currentPlayerIndex = (originalPlayerIndex + 1) % state.players.length;

  // Increment turn count when wrapping back to first player
  if (state.currentPlayerIndex === 0) {
    state.turnCount++;
  }

  // Check if game should end (after vinto call, when we return to the vinto caller)
  if (
    state.phase === 'final' &&
    state.players[state.currentPlayerIndex].id === state.vintoCallerId
  ) {
    // Final round complete - end the game
    state.phase = 'scoring';
    state.subPhase = 'idle';

    console.log(`[${context}] Final round complete, game finished`);
    return;
  }

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
    turnCount: state.turnCount,
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

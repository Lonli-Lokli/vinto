// utils/toss-in-utils.ts
// Utility functions for managing toss-in phase state

import { GameState, PlayerState } from '@vinto/shapes';

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

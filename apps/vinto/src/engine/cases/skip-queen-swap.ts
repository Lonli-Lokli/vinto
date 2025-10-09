import { GameState, SkipQueenSwapAction } from "../types";
import copy from 'fast-copy';

  /**
   * SKIP_QUEEN_SWAP Handler
   *
   * Flow:
   * 1. Player has used a Queen card action
   * 2. Player has selected 2 cards to peek at (stored in pendingAction.targets)
   * 3. Player chooses NOT to swap those two cards (declined Queen's ability)
   * 4. Move Queen card to discard pile (without swapping)
   * 5. Complete turn (increment turn count, transition to idle)
   *
   * Note: Same as EXECUTE_QUEEN_SWAP but skips the actual swap
   */
  export function handleSkipQueenSwap(
    state: GameState,
    _action: SkipQueenSwapAction
  ): GameState {

    // Create new state (deep copy for safety)
    const newState = copy(state);

    // Move Queen card to discard pile (skip the swap)
    if (newState.pendingAction?.card) {
      newState.discardPile.push(newState.pendingAction.card);
    }

    // Clear pending action
    newState.pendingAction = null;

    // Increment turn count
    newState.turnCount += 1;

    // Transition to idle (turn complete)
    newState.subPhase = 'idle';

    return newState;
  }
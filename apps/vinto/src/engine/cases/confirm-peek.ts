import { ConfirmPeekAction, GameState } from "../types";
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

    // Move action card to discard pile
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

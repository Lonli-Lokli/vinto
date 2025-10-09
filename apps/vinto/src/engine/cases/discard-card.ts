import { GameState, DiscardCardAction } from "../types";
import copy from 'fast-copy';

  /**
   * DISCARD_CARD Handler
   *
   * Flow:
   * 1. Player has drawn a card and swapped it into their hand
   * 2. The card they removed is in pendingAction (from SWAP_CARD)
   * 3. Add that card to discard pile
   * 4. Clear pendingAction
   * 5. Transition to idle, ready to advance turn
   */
  export function handleDiscardCard(
    state: GameState,
    _action: DiscardCardAction
  ): GameState {
    // Create new state (deep copy for safety)
    const newState = copy(state);

    // The card to discard is in pendingAction (set by SWAP_CARD)
    if (newState.pendingAction?.card) {
      newState.discardPile.push(newState.pendingAction.card);
    }

    // Clear pending action
    newState.pendingAction = null;

    // Transition to idle - turn is complete
    newState.subPhase = 'idle';

    // Increment turn count (turn completed)
    newState.turnCount += 1;

    return newState;
  }
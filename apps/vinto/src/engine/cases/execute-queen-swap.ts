import { ExecuteQueenSwapAction, GameState } from "../types";
import copy from 'fast-copy';

  /**
   * EXECUTE_QUEEN_SWAP Handler
   *
   * Flow:
   * 1. Player has used a Queen card action
   * 2. Player has selected 2 cards to peek at (stored in pendingAction.targets)
   * 3. Player chooses to swap those two cards (Queen's special ability)
   * 4. Swap the two target cards between players/positions
   * 5. Move Queen card to discard pile
   * 6. Complete turn (increment turn count, transition to idle)
   *
   * Note: Queen allows peeking at 2 cards then optionally swapping them
   * This handler executes the swap; SKIP_QUEEN_SWAP skips it
   */
 export function handleExecuteQueenSwap(
    state: GameState,
    _action: ExecuteQueenSwapAction
  ): GameState {

    // Create new state (deep copy for safety)
    const newState = copy(state);

    // Get the two targets from pending action
    const targets = newState.pendingAction!.targets;
    const [target1, target2] = targets;

    // Find the two target players
    const player1 = newState.players.find((p) => p.id === target1.playerId);
    const player2 = newState.players.find((p) => p.id === target2.playerId);

    if (!player1 || !player2) {
      // Should never happen due to validation
      return state;
    }

    // Swap the two cards
    const card1 = player1.cards[target1.position];
    const card2 = player2.cards[target2.position];

    player1.cards[target1.position] = card2;
    player2.cards[target2.position] = card1;

    // Move Queen card to discard pile
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

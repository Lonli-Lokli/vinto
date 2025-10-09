import { GameState, FinishTossInPeriodAction } from "../types";
import copy from 'fast-copy';

  /**
   * FINISH_TOSS_IN_PERIOD Handler
   *
   * Flow:
   * 1. Toss-in period is complete
   * 2. All players have had a chance to participate
   * 3. Clear the active toss-in
   * 4. Continue normal game flow
   */
export function handleFinishTossInPeriod(
    state: GameState,
    _action: FinishTossInPeriodAction
  ): GameState {

    // Create new state (deep copy for safety)
    const newState = copy(state);

    // Clear the active toss-in
    newState.activeTossIn = null;

    // Game continues in same phase
    return newState;
  }

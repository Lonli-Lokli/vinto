import { GameState, FinishTossInPeriodAction } from '@vinto/shapes';
import copy from 'fast-copy';
import { getAutomaticallyReadyPlayers } from '../utils/toss-in-utils';

/**
 * FINISH_TOSS_IN_PERIOD Handler
 *
 * Flow:
 * 1. Toss-in period is complete
 * 2. All players have had a chance to participate
 * 3. Clear toss-in participation data (preserve ranks)
 * 4. Transition to idle phase (ready for next turn)
 * 5. Turn will be advanced by ADVANCE_TURN action (dispatched separately)
 *
 * IMPORTANT: The ranks are NOT reset here because they were set correctly when cards
 * were discarded. For example, if King declared Ace correctly, activeTossIn.ranks
 * contains ['K', 'A'], and we must preserve both ranks.
 */
export function handleFinishTossInPeriod(
  state: GameState,
  _action: FinishTossInPeriodAction
): GameState {
  // Create new state (deep copy for safety)
  const newState = copy(state);

  // Clear toss-in participation data (but preserve ranks)
  if (newState.activeTossIn) {
    newState.activeTossIn.participants = [];
    newState.activeTossIn.queuedActions = [];
    newState.activeTossIn.waitingForInput = false;
    newState.activeTossIn.playersReadyForNextTurn = getAutomaticallyReadyPlayers(newState.players);
    newState.activeTossIn.failedAttempts = [];

    console.log(
      '[handleFinishTossInPeriod] Toss-in participation cleared, ranks preserved:',
      { ranks: newState.activeTossIn.ranks }
    );
  }

  // Transition to idle phase - ready for turn advancement
  newState.subPhase = 'idle';

  console.log(
    '[handleFinishTossInPeriod] Toss-in complete, ready for turn advancement'
  );

  return newState;
}

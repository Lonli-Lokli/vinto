import { GameState, FinishTossInPeriodAction } from '@/shared';
import copy from 'fast-copy';

/**
 * FINISH_TOSS_IN_PERIOD Handler
 *
 * Flow:
 * 1. Toss-in period is complete
 * 2. All players have had a chance to participate
 * 3. Clear the active toss-in
 * 4. Transition to idle phase (ready for next turn)
 * 5. Turn will be advanced by ADVANCE_TURN action (dispatched separately)
 */
export function handleFinishTossInPeriod(
  state: GameState,
  _action: FinishTossInPeriodAction
): GameState {
  // Create new state (deep copy for safety)
  const newState = copy(state);

  // Clear the active toss-in
  newState.activeTossIn = null;

  // Transition to idle phase - ready for turn advancement
  newState.subPhase = 'idle';

  console.log('[handleFinishTossInPeriod] Toss-in complete, ready for turn advancement');

  return newState;
}

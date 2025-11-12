import { GameState, SkipJackSwapAction } from '@vinto/shapes';
import copy from 'fast-copy';
import { clearTossInAfterActionableCard } from '../utils/toss-in-utils';

/**
 * SKIP_JACK_SWAP Handler
 *
 * Flow:
 * 1. Player has used a Jack card action
 * 2. Player has selected 2 cards (stored in pendingAction.targets)
 * 3. Player chooses NOT to swap those two cards (declined Jack's ability)
 * 4. Move Jack card to discard pile (without swapping)
 * 5. Complete turn (increment turn count, transition to idle)
 *
 * Note: Same as EXECUTE_JACK_SWAP but skips the actual swap
 */
export function handleSkipJackSwap(
  state: GameState,
  action: SkipJackSwapAction
): GameState {
  // Create new state (deep copy for safety)
  const newState = copy(state);

  const pendingCard = newState.pendingAction?.card;

  clearTossInAfterActionableCard(
    pendingCard ? { ...copy(pendingCard), played: true } : pendingCard,
    newState,
    action.payload.playerId
  );

  return newState;
}

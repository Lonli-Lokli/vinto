import { GameState, SetCoalitionLeaderAction } from '@vinto/shapes';
import { copy } from 'fast-copy';

/**
 * SET_COALITION_LEADER Handler
 *
 * Flow:
 * 1. Set the coalition leader for the current round
 * 2. Coalition leader is the player with the lowest score
 * 3. Other players can team up with them
 */
export function handleSetCoalitionLeader(
  state: GameState,
  action: SetCoalitionLeaderAction
): GameState {
  const { leaderId } = action.payload;

  // Create new state (deep copy for safety)
  const newState = copy(state);

  // Set the coalition leader
  newState.coalitionLeaderId = leaderId;

  return newState;
}

import { ExecuteJackSwapAction, GameState } from '@vinto/shapes';
import copy from 'fast-copy';
import { clearTossInAfterActionableCard } from '../utils/toss-in-utils';

/**
 * EXECUTE_JACK_SWAP Handler
 *
 * Flow:
 * 1. Player has used a Jack card action
 * 2. Player has selected 2 cards without peeking (stored in pendingAction.targets)
 * 3. Player chooses to swap those two cards (Jack's special ability)
 * 4. Swap the two target cards between players/positions
 * 5. Move Jack card to discard pile
 * 6. Complete turn (increment turn count, transition to idle)
 *
 * Note: Jack does not allow peeking at cards before swapping them
 * This handler executes the swap; SKIP_JACK_SWAP skips it
 */
export function handleExecuteJackSwap(
  state: GameState,
  action: ExecuteJackSwapAction
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

  // Update known card positions after swap
  // Jack swap is BLIND: nobody peeks at the cards before swapping
  // Players lose knowledge of their swapped positions (cards changed)
  player1.knownCardPositions = player1.knownCardPositions.filter(
    (pos) => pos !== target1.position
  );

  player2.knownCardPositions = player2.knownCardPositions.filter(
    (pos) => pos !== target2.position
  );

  // Acting player (who used Jack) doesn't gain knowledge - Jack is blind swap
  // All opponents also don't gain knowledge - swap happened without revealing cards

  const jackCard = newState.pendingAction?.card;

  // Move Jack card to discard pile
  if (jackCard) {
    newState.discardPile.addToTop({
      ...copy(jackCard),
      played: true,
    });
  }

  clearTossInAfterActionableCard(newState, action.payload.playerId, 'J');
  return newState;
}

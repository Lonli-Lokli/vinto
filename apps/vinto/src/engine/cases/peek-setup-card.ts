import { GameState, PeekSetupCardAction } from '@/shared';
import copy from 'fast-copy';

/**
 * PEEK_SETUP_CARD Handler
 *
 * Flow:
 * 1. During setup phase, player peeks at their own cards
 * 2. Update player's known card positions
 * 3. Track which cards the player has seen
 *
 * Note: In Vinto, players start by peeking at 2 of their 4 cards
 */
export function handlePeekSetupCard(
  state: GameState,
  action: PeekSetupCardAction
): GameState {
  const { playerId, position } = action.payload;

  // Create new state (deep copy for safety)
  const newState = copy(state);

  // Find the player
  const player = newState.players.find((p) => p.id === playerId);
  if (!player) {
    return state;
  }

  // Get the card at position
  const card = player.cards[position];
  if (!card) {
    return state;
  }

  // Add to known card positions (player has peeked at this card)
  const existingKnown = player.knownCardPositions.includes(position);
  if (!existingKnown) {
    player.knownCardPositions.push(position);
  }

  return newState;
}

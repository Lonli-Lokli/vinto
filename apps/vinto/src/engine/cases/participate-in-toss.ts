import { GameState, ParticipateInTossInAction } from '@/shared';
import copy from 'fast-copy';

/**
 * PARTICIPATE_IN_TOSS_IN Handler
 *
 * Flow:
 * 1. A toss-in has been triggered (via DECLARE_KING_ACTION)
 * 2. Other players participate by discarding matching cards
 * 3. Add player to participants list
 * 4. Move matching card from hand to discard pile
 *
 * Note: Toss-in is when a King declares a rank, forcing others to discard matching cards
 */
export function handleParticipateInTossIn(
  state: GameState,
  action: ParticipateInTossInAction
): GameState {
  const { playerId, position } = action.payload;

  // Create new state (deep copy for safety)
  const newState = copy(state);

  // Find the player
  const player = newState.players.find((p) => p.id === playerId);
  if (!player || !newState.activeTossIn) {
    return state;
  }

  // Get the card at the specified position
  const card = player.cards[position];
  if (!card) {
    return state;
  }

  // Verify card matches declared rank (optional validation)
  if (card.rank !== newState.activeTossIn.rank) {
    console.warn(
      `Card rank ${card.rank} doesn't match toss-in rank ${newState.activeTossIn.rank}`
    );
  }

  // Add player to participants
  if (!newState.activeTossIn.participants.includes(playerId)) {
    newState.activeTossIn.participants.push(playerId);
  }

  // Move card to discard pile
  player.cards.splice(position, 1);
  newState.discardPile.addToTop(card);

  return newState;
}

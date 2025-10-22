// engine/cases/swap-hand-with-deck.ts
// Handle SWAP_HAND_WITH_DECK action (debug/testing only)

import { GameState, SwapHandWithDeckAction } from '@vinto/shapes';
import copy from 'fast-copy';

/**
 * Handle SWAP_HAND_WITH_DECK action
 *
 * Debug action that swaps a card in player's hand with a card from the draw pile
 * This allows testing specific hand configurations during development
 *
 * @param state Current game state
 * @param action SwapHandWithDeckAction
 * @returns New game state with swapped cards
 */
export function handleSwapHandWithDeck(
  state: GameState,
  action: SwapHandWithDeckAction
): GameState {
  const { playerId, handPosition, deckCardRank } = action.payload;

  const newState = copy(state);

  // Find the player
  const player = newState.players.find((p) => p.id === playerId);
  if (!player) {
    console.warn(`Player ${playerId} not found`, { playerId });
    return state;
  }

  // Validate hand position
  if (handPosition < 0 || handPosition >= player.cards.length) {
    console.warn(`Invalid hand position ${handPosition}`, {
      handPosition,
      cardCount: player.cards.length,
    });
    return state;
  }

  // Find a card with the specified rank in the draw pile
  let deckCardIndex = -1;
  for (let i = 0; i < newState.drawPile.length; i++) {
    const card = newState.drawPile.at(i);
    if (card?.rank === deckCardRank) {
      deckCardIndex = i;
      break;
    }
  }

  if (deckCardIndex === -1) {
    console.warn(`No card with rank ${deckCardRank} found in draw pile`, {
      deckCardRank,
      drawPileSize: state.drawPile.length,
    });
    return state;
  }

  // Perform the swap
  const handCard = player.cards[handPosition];
  const deckCard = newState.drawPile.takeAt(deckCardIndex);

  if (!deckCard) {
    return state;
  }

  // Replace hand card with deck card
  player.cards[handPosition] = deckCard;

  // Put hand card back in draw pile (add to bottom to avoid disrupting order)
  newState.drawPile.addToTop(handCard);

  console.log(
    `Swapped ${handCard.rank} at position ${handPosition} with ${deckCard.rank} from deck`,
    {
      playerId,
      handPosition,
      oldCard: handCard.rank,
      newCard: deckCard.rank,
    }
  );

  return newState;
}

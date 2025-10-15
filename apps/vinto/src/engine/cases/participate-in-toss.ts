import { GameState, ParticipateInTossInAction } from '@/shared';
import copy from 'fast-copy';

/**
 * PARTICIPATE_IN_TOSS_IN Handler
 *
 * Flow:
 * 1. A toss-in has been triggered (by discarding a card)
 * 2. Player tosses in a matching card
 * 3. Remove card from player's hand
 * 4. If card has an action (7-K, A), queue it for execution
 * 5. If no action, move directly to discard pile
 *
 * Note: Toss-in actions must be resolved before turn advances
 * The queuedActions will be processed sequentially in toss_queue_processing phase
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

  // Remove card from hand
  player.cards.splice(position, 1);

  // Check if card has an action
  if (card.actionText) {
    console.log(
      `[handleParticipateInTossIn] Action card ${card.rank} tossed in by ${playerId}, queuing for action use`
    );

    // Queue this action card for later execution
    // Cards will be processed in order: first tossed-in, first processed
    newState.activeTossIn.queuedActions.push({
      playerId,
      card,
      position, // Original position (for bot memory)
    });
  } else {
    console.log(
      `[handleParticipateInTossIn] Non-action card ${card.rank}, moving directly to discard`
    );
    // Non-action card goes directly to discard pile
    newState.discardPile.addToTop(card);
  }

  return newState;
}

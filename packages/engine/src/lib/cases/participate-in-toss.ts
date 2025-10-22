import { GameState, ParticipateInTossInAction, Card } from '@vinto/shapes';
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

  // Verify card matches declared rank - STRICT validation now
  if (newState.activeTossIn.ranks.includes(card.rank) === false) {
    // Invalid toss-in penalty:
    // 1. Card stays in hand (no removal)
    // 2. Draw 1 penalty card from draw pile
    if (newState.drawPile.length > 0) {
      const penaltyCard = newState.drawPile.drawTop();
      if (penaltyCard) {
        player.cards.push(penaltyCard);
        console.log(
          `[handleParticipateInTossIn] Penalty card ${penaltyCard.rank} added to ${playerId}'s hand`
        );
      }
    }

    // Mark this as a failed toss-in attempt (for UI feedback)
    newState.activeTossIn.failedAttempts =
      newState.activeTossIn.failedAttempts || [];
    newState.activeTossIn.failedAttempts.push({
      playerId,
      cardRank: card.rank,
      position,
      expectedRanks: newState.activeTossIn.ranks,
    });

    // When toss-in fails, the card is revealed to ALL players
    for (const p of newState.players) {
      if (p.id === playerId) {
        // Acting player marks own position as known
        if (!p.knownCardPositions.includes(position)) {
          p.knownCardPositions.push(position);
        }
      } else {
        // Opponents learn about the revealed card
        if (!p.opponentKnowledge) p.opponentKnowledge = {};
        if (!p.opponentKnowledge[playerId]) {
          p.opponentKnowledge[playerId] = { knownCards: {} };
        }
        p.opponentKnowledge[playerId].knownCards[position] = card;
      }
    }

    return newState;
  }

  // Valid toss-in - proceed normally
  console.log(
    `[handleParticipateInTossIn] Valid toss-in: ${card.rank} matches ${newState.activeTossIn.ranks}`
  );

  // Add player to participants
  if (!newState.activeTossIn.participants.includes(playerId)) {
    newState.activeTossIn.participants.push(playerId);
  }

  // Remove card from hand
  player.cards.splice(position, 1);

  // Adjust known positions: all positions after the removed card shift down
  player.knownCardPositions = player.knownCardPositions
    .filter((pos) => pos !== position) // Remove the tossed-in position
    .map((pos) => (pos > position ? pos - 1 : pos)); // Shift down positions after it

  // Also update opponent knowledge for this player across ALL other players
  for (const p of newState.players) {
    if (p.id !== playerId && p.opponentKnowledge?.[playerId]) {
      const oppKnowledge = p.opponentKnowledge[playerId].knownCards;
      const updatedKnowledge: Record<number, Card> = {};

      for (const [posStr, card] of Object.entries(oppKnowledge)) {
        const pos = parseInt(posStr, 10);
        if (pos < position) {
          // Position unchanged
          updatedKnowledge[pos] = card;
        } else if (pos > position) {
          // Position shifts down
          updatedKnowledge[pos - 1] = card;
        }
        // pos === position is removed (card was tossed in)
      }

      p.opponentKnowledge[playerId].knownCards = updatedKnowledge;
    }
  }

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

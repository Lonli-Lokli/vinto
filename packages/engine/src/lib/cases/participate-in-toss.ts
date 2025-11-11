import { GameState, ParticipateInTossInAction, Card } from '@vinto/shapes';
import copy from 'fast-copy';

/**
 * PARTICIPATE_IN_TOSS_IN Handler
 *
 * Flow:
 * 1. A toss-in has been triggered (by discarding a card)
 * 2. Player tosses in one or more matching cards simultaneously
 * 3. Validate ALL cards match toss-in ranks (if any fail, apply penalty)
 * 4. Remove cards from player's hand (sorted descending to avoid index shifts)
 * 5. Queue action cards for execution, move non-action cards to discard
 *
 * Multi-card toss-in:
 * - Player can toss in multiple matching cards at once
 * - ALL cards must match the toss-in ranks
 * - If ANY card doesn't match, ALL cards stay in hand and penalty is applied
 * - Cards are processed together to avoid multiple position adjustments
 *
 * Note: Toss-in actions must be resolved before turn advances
 * The queuedActions will be processed sequentially in toss_queue_processing phase
 */
export function handleParticipateInTossIn(
  state: GameState,
  action: ParticipateInTossInAction
): GameState {
  const { playerId, positions } = action.payload;

  // Create new state (deep copy for safety)
  const newState = copy(state);

  // Find the player
  const player = newState.players.find((p) => p.id === playerId);
  if (!player || !newState.activeTossIn) {
    return state;
  }

  // Validate positions array
  if (!positions || positions.length === 0) {
    console.log(
      `[handleParticipateInTossIn] No positions provided for ${playerId}`
    );
    return state;
  }

  // Get the cards at the specified positions
  const cardsToTossIn: { card: Card; position: number }[] = [];
  for (const position of positions) {
    const card = player.cards[position];
    if (!card) {
      console.log(
        `[handleParticipateInTossIn] Invalid position ${position} for ${playerId}`
      );
      return state;
    }
    cardsToTossIn.push({ card, position });
  }

  // STRICT VALIDATION: ALL cards must match the toss-in ranks
  const invalidCards = cardsToTossIn.filter(
    ({ card }) => !newState.activeTossIn!.ranks.includes(card.rank)
  );

  if (invalidCards.length > 0) {
    // Invalid toss-in penalty:
    // 1. ALL cards stay in hand (no removal)
    // 2. Draw penalty cards (one per invalid card) from draw pile
    console.log(
      `[handleParticipateInTossIn] Invalid toss-in by ${playerId}: ${
        invalidCards.length
      } card(s) don't match ranks ${newState.activeTossIn.ranks.join(', ')}`
    );

    const penaltyCards: Card[] = [];
    for (let i = 0; i < invalidCards.length; i++) {
      if (newState.drawPile.length > 0) {
        const penaltyCard = newState.drawPile.drawTop();
        if (penaltyCard) {
          player.cards.push(penaltyCard);
          penaltyCards.push(penaltyCard);
        }
      }
    }

    if (penaltyCards.length > 0) {
      console.log(
        `[handleParticipateInTossIn] Penalty cards added to ${playerId}'s hand: ${penaltyCards
          .map((c) => c.rank)
          .join(', ')}`
      );
    }

    // Mark this as a failed toss-in attempt (for UI feedback)
    if (!newState.activeTossIn.failedAttempts) {
      newState.activeTossIn.failedAttempts = [];
    }

    for (const { card, position } of invalidCards) {
      newState.activeTossIn.failedAttempts.push({
        playerId,
        cardRank: card.rank,
        position,
        expectedRanks: newState.activeTossIn.ranks,
      });
        newState.roundFailedAttempts.push({
        playerId,
        cardRank: card.rank,
        position,
        expectedRanks: newState.activeTossIn.ranks,
      });
    }

    // When toss-in fails, ALL attempted cards are revealed to ALL players
    for (const { card, position } of cardsToTossIn) {
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
    }

    return newState;
  }

  // Valid toss-in - proceed normally
  console.log(
    `[handleParticipateInTossIn] Valid toss-in by ${playerId}: ${
      cardsToTossIn.length
    } card(s) [${cardsToTossIn.map(({ card }) => card.rank).join(', ')}]`
  );

  // Add player to participants
  if (!newState.activeTossIn.participants.includes(playerId)) {
    newState.activeTossIn.participants.push(playerId);
  }

  // Sort positions in DESCENDING order to avoid index shift issues
  // (removing from highest index first doesn't affect lower indices)
  const sortedPositions = [...positions].sort((a, b) => b - a);

  // Process each card
  for (const position of sortedPositions) {
    const card = player.cards[position];
    if (!card) continue; // Safety check

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

        for (const [posStr, knownCard] of Object.entries(oppKnowledge)) {
          const pos = parseInt(posStr, 10);
          if (pos < position) {
            // Position unchanged
            updatedKnowledge[pos] = knownCard;
          } else if (pos > position) {
            // Position shifts down
            updatedKnowledge[pos - 1] = knownCard;
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
        rank: card.rank,
        position, // Original position (for bot memory)
      });
    } else {
      console.log(
        `[handleParticipateInTossIn] Non-action card ${card.rank}, moving directly to discard`
      );
      // Non-action card goes directly to discard pile
      newState.discardPile.addToTop(card);
    }
  }

  return newState;
}

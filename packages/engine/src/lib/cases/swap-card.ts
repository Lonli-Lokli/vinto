import { GameState, SwapCardAction } from '@vinto/shapes';
import copy from 'fast-copy';
import { getTargetTypeFromRank } from '../utils/action-utils';
import { getAutomaticallyReadyPlayers } from '../utils/toss-in-utils';

/**
 * SWAP_CARD Handler
 *
 * Flow:
 * 1. Get the card from pendingAction
 * 2. Swap it with the card at the specified position in player's hand
 * 3. Update player's known cards if declaration was made
 * 4. Clear pending action
 * 5. Transition to toss_queue_active
 */
export function handleSwapCard(
  state: GameState,
  action: SwapCardAction
): GameState {
  const { playerId, position, declaredRank } = action.payload;

  console.log('[handleSwapCard] Starting swap:', {
    playerId,
    position,
    declaredRank,
    subPhase: state.subPhase,
    hasPendingAction: !!state.pendingAction,
  });

  // Create new state (deep copy for safety)
  const newState = copy(state);

  // Get the pending card (from draw)
  const pendingCard = newState.pendingAction!.card;

  // Find the player
  const playerIndex = newState.players.findIndex((p) => p.id === playerId);
  if (playerIndex === -1) {
    return state; // Should never happen due to validation
  }

  const player = newState.players[playerIndex];

  // Swap: take card from hand, put pending card in its place
  const cardFromHand = player.cards[position];
  player.cards[position] = pendingCard;

  // Update known card positions:
  // After swapping, the player now knows what's at this position (the drawn card)
  if (!player.knownCardPositions.includes(position)) {
    player.knownCardPositions.push(position);
  }

  // Validate rank declaration if provided
  let declarationCorrect = false;
  if (declaredRank) {
    declarationCorrect = cardFromHand.rank === declaredRank;

    console.log('[handleSwapCard] Rank declaration:', {
      declared: declaredRank,
      actual: cardFromHand.rank,
      correct: declarationCorrect,
    });

    // If declaration was incorrect, issue penalty card
    if (!declarationCorrect && newState.drawPile.length > 0) {
      const penaltyCard = newState.drawPile.drawTop();
      if (penaltyCard) {
        player.cards.push(penaltyCard);
        console.log('[handleSwapCard] Penalty card issued:', penaltyCard.rank);
      }
    }

    // If declaration was correct, set up the card action based on the card's rank
    if (declarationCorrect) {
      newState.pendingAction = {
        card: cardFromHand,
        playerId,
        actionPhase: 'selecting-target',
        targetType: getTargetTypeFromRank(cardFromHand.rank),
        targets: [],
        swapPosition: position, // Store the position for animation later
      };
      newState.subPhase = 'awaiting_action';

      console.log(
        '[handleSwapCard] Correct declaration - card action available:',
        {
          rank: cardFromHand.rank,
          targetType: getTargetTypeFromRank(cardFromHand.rank),
          swapPosition: position,
        }
      );
      return newState;
    }
  }

  // If no declaration or incorrect declaration, discard the swapped-out card
  newState.discardPile.addToTop(cardFromHand);

  // Clear pending action
  newState.pendingAction = null;

  // Initialize toss-in phase
  // Players who called VINTO are automatically marked as ready (can't participate in toss-in)
  newState.activeTossIn = {
    ranks: [cardFromHand.rank],
    initiatorId: playerId,
    originalPlayerIndex: newState.currentPlayerIndex,
    participants: [],
    queuedActions: [],
    waitingForInput: true,
    playersReadyForNextTurn: getAutomaticallyReadyPlayers(newState.players),
  };

  // Transition to toss-in phase
  newState.subPhase = 'toss_queue_active';

  console.log('[handleSwapCard] Swap complete, toss-in active:', {
    newSubPhase: newState.subPhase,
    swappedCard: cardFromHand.rank,
    newCardAtPosition: pendingCard.rank,
    knownPositions: player.knownCardPositions,
    declarationCorrect,
  });

  return newState;
}

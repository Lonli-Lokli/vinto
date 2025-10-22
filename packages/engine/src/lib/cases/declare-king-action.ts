import { GameState, DeclareKingActionAction, Rank } from '@vinto/shapes';
import copy from 'fast-copy';
import { getTargetTypeFromRank } from '../utils/action-utils';
import {
  addTossInCard,
  clearTossInReadyList,
  getAutomaticallyReadyPlayers,
} from '../utils/toss-in-utils';

/**
 * DECLARE_KING_ACTION Handler
 *
 * Flow:
 * 1. Player has used a King card (via USE_CARD_ACTION)
 * 2. Player has selected a card from their hand or an opponent's hand (via SELECT_ACTION_TARGET)
 * 3. Player declares a rank (any rank including K is allowed)
 * 4. Validate declared rank against the selected card's actual rank
 * 5a. If correct:
 *     - King card goes to discard pile first
 *     - Selected card goes to discard pile
 *     - Trigger toss-in for the declared rank
 * 5b. If incorrect:
 *     - King card goes to discard pile
 *     - Selected card stays visible in hand (marked for animation)
 *     - Player gets a penalty card
 *     - No toss-in is triggered
 * 6. Complete turn (increment turn count, transition to idle)
 *
 * Note: King's ability triggers a toss-in only if the declaration is correct
 */
export function handleDeclareKingAction(
  state: GameState,
  action: DeclareKingActionAction
): GameState {
  const { playerId, declaredRank } = action.payload;

  console.log('[handleDeclareKingAction] Declaring King action:', {
    playerId,
    declaredRank,
  });

  // Create new state (deep copy for safety)
  const newState = copy(state);

  // Get the selected card from pendingAction.targets[0]
  const selectedTarget = newState.pendingAction?.targets?.[0];
  if (!selectedTarget) {
    console.error(
      '[handleDeclareKingAction] No target was selected for King action'
    );
    return state;
  }

  const {
    playerId: targetPlayerId,
    position
  } = selectedTarget;

  const selectedCard = newState.players.find(p => p.id === targetPlayerId)?.cards[position];

   if (!selectedCard) {
    console.error(
      '[handleDeclareKingAction] No selected card found for King action'
    );
    return state;
  }

  const actualRank = selectedCard.rank;
  const isCorrect = actualRank === declaredRank;

  console.log('[handleDeclareKingAction] Validating declaration:', {
    declaredRank,
    actualRank,
    isCorrect,
    targetPlayerId,
    position,
  });

  // Move King card to discard pile first (always happens)
  if (newState.pendingAction?.card) {
    newState.discardPile.addToTop({
      ...copy(newState.pendingAction.card),
      played: true,
    });
  }

  // Find the target player
  const targetPlayer = newState.players.find((p) => p.id === targetPlayerId);
  if (!targetPlayer) {
    console.error('[handleDeclareKingAction] Target player not found');
    return state;
  }

  if (isCorrect) {
    // Correct declaration: check if the card has an action
    const targetType = getTargetTypeFromRank(selectedCard.rank);

    // Remove the card from the target player's hand
    const [removedCard] = targetPlayer.cards.splice(position, 1);

    if (newState.activeTossIn) {
      newState.activeTossIn.ranks = addTossInCard(
        newState.activeTossIn?.ranks,
        removedCard.rank
      );
    }
    // If the card has an action, set up pendingAction for it
    if (targetType !== undefined) {
      // Update known card positions (remove the position that was removed)
      targetPlayer.knownCardPositions = targetPlayer.knownCardPositions
        .filter((pos) => pos !== position)
        .map((pos) => (pos > position ? pos - 1 : pos));

      // Set up pending action for the correctly declared action card
      newState.pendingAction = {
        card: removedCard,
        playerId,
        actionPhase: 'selecting-target',
        targetType,
        targets: [],
      };

      newState.subPhase = 'awaiting_action';

      console.log(
        '[handleDeclareKingAction] Correct declaration - card action available:',
        {
          rank: removedCard.rank,
          targetType,
        }
      );
      return newState;
    } else {
      // Non-action card: discard it

      newState.discardPile.addToTop(removedCard);

      // Update known card positions (remove the position that was removed)
      targetPlayer.knownCardPositions = targetPlayer.knownCardPositions
        .filter((pos) => pos !== position)
        .map((pos) => (pos > position ? pos - 1 : pos));

      console.log(
        '[handleDeclareKingAction] Correct declaration - card moved to discard'
      );
    }
  } else {
    // Incorrect declaration: card stays in hand, mark it as known to ALL players
    // and issue a penalty card
    const player = newState.players.find((p) => p.id === playerId);
    if (!player) {
      console.error('[handleDeclareKingAction] Player not found');
      return state;
    }

    // Issue penalty card if draw pile is not empty
    if (newState.drawPile.length > 0) {
      const penaltyCard = newState.drawPile.drawTop();
      if (penaltyCard) {
        player.cards.push(penaltyCard);
        console.log(
          '[handleDeclareKingAction] Incorrect declaration - penalty card issued:',
          penaltyCard.rank
        );
      }
    }

    // When declaration fails, the card is revealed (flash animation) to ALL players
    for (const p of newState.players) {
      if (p.id === targetPlayerId) {
        // Target player marks own card as known
        if (!p.knownCardPositions.includes(position)) {
          p.knownCardPositions.push(position);
        }
      } else {
        // All other players learn about target player's revealed card
        if (!p.opponentKnowledge) p.opponentKnowledge = {};
        if (!p.opponentKnowledge[targetPlayerId]) {
          p.opponentKnowledge[targetPlayerId] = { knownCards: {} };
        }
        p.opponentKnowledge[targetPlayerId].knownCards[position] = selectedCard;
      }
    }
  }

  // Clear pending action
  newState.pendingAction = null;

  // Check if this action was part of a toss-in
  if (newState.activeTossIn !== null) {
    // Return to toss-in phase (action was from toss-in participation)
    // Clear the ready list so players can confirm again for this new toss-in round
    clearTossInReadyList(newState);
    newState.subPhase = 'toss_queue_active';
    newState.activeTossIn.waitingForInput = true;
    console.log(
      '[handleDeclareKingAction] King action during toss-in complete, returning to toss-in phase (ready list cleared)'
    );
  } else {
    // Determine which rank triggers the toss-in:
    // - If declaration is CORRECT: toss-in for King as well as the declared rank
    // - If declaration is INCORRECT: toss-in for King rank (because only King went to discard)
    const tossInRanks: [Rank, ...Rank[]] = isCorrect
      ? ['K', declaredRank]
      : ['K'];

    // Players who called VINTO are automatically marked as ready (can't participate in toss-in)
    newState.activeTossIn = {
      ranks: tossInRanks,
      initiatorId: playerId,
      originalPlayerIndex: newState.currentPlayerIndex,
      participants: [],
      queuedActions: [],
      waitingForInput: true,
      playersReadyForNextTurn: getAutomaticallyReadyPlayers(newState.players),
    };

    // Transition to toss-in phase
    newState.subPhase = 'toss_queue_active';

    console.log(
      '[handleDeclareKingAction] King action complete, toss-in active:',
      {
        declaredRank,
        isCorrect,
        tossInRanks,
        newSubPhase: newState.subPhase,
      }
    );
  }

  return newState;
}

import {
  GameState,
  DeclareKingActionAction,
  Rank,
  logger,
} from '@vinto/shapes';
import copy from 'fast-copy';
import { getTargetTypeFromRank } from '../utils/action-utils';
import {
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

  const newState = copy(state);

  // Get selected target
  const selectedTarget = newState.pendingAction?.targets?.[0];
  if (!selectedTarget) {
    logger.error('[handleDeclareKingAction] No target selected');
    return state;
  }

  const { playerId: targetPlayerId, position } = selectedTarget;

  // Find target player and card
  const targetPlayer = newState.players.find((p) => p.id === targetPlayerId);
  if (!targetPlayer) {
    logger.error('[handleDeclareKingAction] Target player not found');
    return state;
  }

  const selectedCard = targetPlayer.cards[position];
  if (!selectedCard) {
    logger.error('[handleDeclareKingAction] Selected card not found');
    return state;
  }

  const actualRank = selectedCard.rank;
  const isCorrect = actualRank === declaredRank;

  console.log('[handleDeclareKingAction] Declaration:', {
    declaredRank,
    actualRank,
    isCorrect,
    targetPlayerId,
    position,
  });

  const isTossInPhase =
    newState.activeTossIn !== null && newState.activeTossIn.queuedActions.length > 0;
  // STEP 1: Move King to discard (ALWAYS happens)
  if (newState.pendingAction?.card) {
    const discardedCard = {
      ...copy(newState.pendingAction.card),
      played: true,
    };
    if (isTossInPhase) {
      // we are in toss-in phase, add card to one below top
      newState.discardPile.addBeforeTop(discardedCard);
    } else {
      newState.discardPile.addToTop(discardedCard);
    }
  }

  // STEP 2: Handle correct vs incorrect
  if (isCorrect) {
    handleCorrectDeclaration(
      newState,
      targetPlayer,
      position,
      selectedCard,
      playerId,
      declaredRank,
      isTossInPhase
    );
  } else {
    handleIncorrectDeclaration(
      newState,
      targetPlayer,
      position,
      selectedCard,
      playerId
    );
  }

  return newState;
}

/**
 * CORRECT Declaration Handler
 *
 * FIX: Properly creates multi-rank toss-in ['K', declaredRank]
 */
function handleCorrectDeclaration(
  newState: GameState,
  targetPlayer: any,
  position: number,
  selectedCard: any,
  playerId: string,
  declaredRank: Rank,
  isTossInPhase: boolean
): void {
  // Remove declared card from hand
  const [removedCard] = targetPlayer.cards.splice(position, 1);

  // Update known positions (shift indices after removal)
  targetPlayer.knownCardPositions = targetPlayer.knownCardPositions
    .filter((pos: number) => pos !== position)
    .map((pos: number) => (pos > position ? pos - 1 : pos));

  // Check if declared card is actionable
  const targetType = getTargetTypeFromRank(selectedCard.rank);

  if (targetType !== undefined) {
    // ACTIONABLE CARD - Setup pending action
    console.log('[handleDeclareKingAction] Correct - actionable card:', {
      rank: removedCard.rank,
      targetType,
    });

    newState.pendingAction = {
      card: removedCard,
      from: 'hand',
      playerId,
      actionPhase: 'choosing-action',
      targetType,
      targets: [],
    };

    // FIX BUG 1 & 3: Properly setup multi-rank toss-in
    setupKingTossInMultiRank(newState, playerId, declaredRank, true);
  } else {
    // NON-ACTIONABLE CARD - Just discard it
    console.log(
      '[handleDeclareKingAction] Correct - non-actionable, discarding'
    );

    if (isTossInPhase) {
      // we are in toss-in phase, add card to one below top
      newState.discardPile.addBeforeTop(removedCard);
    } else {
      newState.discardPile.addToTop(removedCard);
    }
    newState.pendingAction = null;

    // FIX BUG 1 & 3: Properly setup multi-rank toss-in
    setupKingTossInMultiRank(newState, playerId, declaredRank, false);
  }
}

/**
 * INCORRECT Declaration Handler
 */
function handleIncorrectDeclaration(
  newState: GameState,
  targetPlayer: any,
  position: number,
  selectedCard: any,
  playerId: string
): void {
  console.log('[handleDeclareKingAction] INCORRECT declaration');

  const player = newState.players.find((p) => p.id === playerId);
  if (!player) {
    logger.error('[handleDeclareKingAction] Player not found');
    return;
  }

  // Card stays in hand, revealed to ALL players
  for (const p of newState.players) {
    if (p.id === targetPlayer.id) {
      if (!p.knownCardPositions.includes(position)) {
        p.knownCardPositions.push(position);
      }
    } else {
      if (!p.opponentKnowledge) p.opponentKnowledge = {};
      if (!p.opponentKnowledge[targetPlayer.id]) {
        p.opponentKnowledge[targetPlayer.id] = { knownCards: {} };
      }
      p.opponentKnowledge[targetPlayer.id].knownCards[position] = selectedCard;
    }
  }

  // Issue penalty card
  if (newState.drawPile.length > 0) {
    const penaltyCard = newState.drawPile.drawTop();
    if (penaltyCard) {
      player.cards.push(penaltyCard);
      console.log(
        '[handleDeclareKingAction] Penalty card issued:',
        penaltyCard.rank
      );
    }
  }

  newState.pendingAction = null;

  // Setup toss-in for KING ONLY (no declared rank)
  setupKingTossInMultiRank(newState, playerId, undefined, false);
}

/**
 * Setup King multi-rank toss-in
 *
 * FIXES:
 * - BUG 1: Always creates ['K', declaredRank] array (not just ['K'])
 * - BUG 3: APPENDS to existing ranks instead of overwriting
 *
 * @param declaredRank - undefined for incorrect declaration
 * @param hasAction - true if declared card is actionable
 */
function setupKingTossInMultiRank(
  newState: GameState,
  playerId: string,
  declaredRank: Rank | undefined,
  hasAction: boolean
): void {
  // Build rank array: ['K'] or ['K', declaredRank]
  const kingRanks: Rank[] = declaredRank ? ['K', declaredRank] : ['K'];

  if (newState.activeTossIn === null) {
    // START NEW TOSS-IN
    console.log('[setupKingTossInMultiRank] Starting new King toss-in:', {
      ranks: kingRanks,
      hasAction,
    });

    newState.activeTossIn = {
      ranks: kingRanks as [Rank, ...Rank[]],
      initiatorId: playerId,
      originalPlayerIndex: newState.currentPlayerIndex,
      participants: [],
      queuedActions: [],
      waitingForInput: !hasAction,
      playersReadyForNextTurn: getAutomaticallyReadyPlayers(
        newState.players,
        newState.coalitionLeaderId
      ),
    };

    newState.subPhase = hasAction ? 'awaiting_action' : 'toss_queue_active';
  } else {
    // ALREADY IN TOSS-IN - Append King ranks
    console.log(
      '[setupKingTossInMultiRank] Adding King ranks to existing toss-in:',
      {
        existingRanks: newState.activeTossIn.ranks,
        addingRanks: kingRanks,
      }
    );

    // FIX BUG 3: APPEND ranks instead of overwriting
    kingRanks.forEach((rank) => {
      if (!newState.activeTossIn!.ranks.includes(rank)) {
        newState.activeTossIn!.ranks.push(rank);
      }
    });

    // Remove processed King action from queue if applicable
    if (newState.activeTossIn.queuedActions.length > 0) {
      const firstAction = newState.activeTossIn.queuedActions[0];
      if (firstAction.rank === 'K' && firstAction.playerId === playerId) {
        newState.activeTossIn.queuedActions.shift();
        console.log('[setupKingTossInMultiRank] Removed King from queue');
      }
    }

    // Clear ready list (new ranks added)
    clearTossInReadyList(newState);

    newState.subPhase = hasAction ? 'awaiting_action' : 'toss_queue_active';
    newState.activeTossIn.waitingForInput = !hasAction;

    console.log('[setupKingTossInMultiRank] Updated toss-in:', {
      finalRanks: newState.activeTossIn.ranks,
      subPhase: newState.subPhase,
    });
  }
}

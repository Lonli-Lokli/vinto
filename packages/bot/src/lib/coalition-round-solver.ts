/**
 * Coalition Round Solver
 *
 * Generates ALL possible move sequences for coalition and finds optimal combination
 * to minimize champion's score. Uses exhaustive search with known cards and
 * probabilistic evaluation for unknown cards.
 *
 * Game Actions Available:
 * - 7, 8: Peek ONE of your own cards
 * - 9, 10: Peek ONE opponent card
 * - J (Jack): Swap TWO cards from different players
 * - Q (Queen): Peek TWO cards from different players, then optionally swap them
 * - K (King): Declare ANY card action (7-A) OR declare non-action (2-6, K, Joker) to cascade/discard
 * - A (Ace): Force opponent to draw penalty card (AVOID in coalition - harmful!)
 *
 * Strategy:
 * 1. Generate all possible move sequences using available cards
 * 2. Simulate each sequence's effect on champion's score
 * 3. Use 9/10 and Queen to reveal unknown cards when beneficial
 * 4. Use Jack and Queen-swap to transfer bad cards or receive good cards
 * 5. Use King to cascade (declare K/2-6/Joker) or amplify other actions
 * 6. Select sequence leading to lowest champion score
 */

import type { GameState, Card, Rank } from '@vinto/shapes';
import { getCardValue } from '@vinto/shapes';

export interface CoalitionPlan {
  championId: string;
  targetScore: number; // Best achievable score for champion
  steps: CoalitionAction[];
  confidence: number; // 0-1: certainty based on known/unknown cards ratio
}

export interface CoalitionAction {
  playerId: string;
  // Turn structure: draw -> use action -> optional toss-in
  drawnCardSwapWith?: number; // Swap drawn card with this index before using it
  cardIndex: number; // Which card to use for action (-1 for discard)
  actionType:
    | 'peek-own' // 7, 8: Peek one of your cards
    | 'peek-opponent' // 9, 10: Peek one opponent card
    | 'swap-jack' // J: Swap two cards from different players
    | 'peek-swap-queen' // Q: Peek two cards, optionally swap
    | 'declare-king' // K: Declare any action or cascade
    | 'discard'; // Discard drawn card
  targets?: { playerId: string; cardIndex: number }[]; // For peeks/swaps
  declaredRank?: Rank; // For King declarations
  tossInCards?: number[]; // Additional cards to toss in during declare
  shouldSwap?: boolean; // For Queen: whether to swap after peeking
  description: string;
}

export interface CoalitionState {
  players: Map<string, PlayerHand>;
  championId: string;
  vintoCallerId: string;
  drawPileSize: number; // Number of cards remaining in draw pile
  knownDrawCard?: Card; // If we know the top card of draw pile
}

export interface PlayerHand {
  playerId: string;
  cards: CardInfo[];
}

export interface CardInfo {
  card?: Card; // Known card
  possibleRanks: Rank[]; // Unknown card possibilities
  isKnown: boolean;
}

/**
 * Build coalition state from game state and known cards
 */
function buildCoalitionState(
  state: GameState,
  knownCards: Map<string, Card>,
  pendingCard?: Card,
  overrideChampionId?: string | null
): CoalitionState {
  const vintoCallerId = state.vintoCallerId!;
  const coalitionLeaderId = state.coalitionLeaderId!;
  const players = new Map<string, PlayerHand>();

  for (const player of state.players) {
    if (player.id === vintoCallerId) continue;

    const cards: CardInfo[] = player.cards.map((_card, idx) => {
      const key = `${player.id}[${idx}]`;
      const known = knownCards.get(key);

      if (known) {
        return { card: known, isKnown: true, possibleRanks: [known.rank] };
      } else {
        // Unknown card - could be any rank
        return {
          possibleRanks: [
            'A',
            '2',
            '3',
            '4',
            '5',
            '6',
            '7',
            '8',
            '9',
            '10',
            'J',
            'Q',
            'K',
            'Joker',
          ] as Rank[],
          isKnown: false,
        };
      }
    });

    // Add pending card if this is current player
    if (
      pendingCard &&
      player.id === state.players[state.currentPlayerIndex].id
    ) {
      cards.push({
        card: pendingCard,
        isKnown: true,
        possibleRanks: [pendingCard.rank],
      });
    }

    players.set(player.id, { playerId: player.id, cards });
  }

  // Use overridden champion ID if provided, otherwise identify champion fresh
  // This ensures champion stays consistent throughout the final round
  const championId =
    overrideChampionId || identifyChampion(players, coalitionLeaderId);

  return {
    players,
    championId,
    vintoCallerId,
    drawPileSize: state.drawPile.length,
    knownDrawCard: state.drawPile.peekTop(),
  };
}

/**
 * Identify which coalition member has best improvement potential
 * Returns player with lowest estimated score (best chance to win)
 *
 * @param players - All non-Vinto-caller players (including leader)
 * @param coalitionLeaderId - ID of the coalition leader (excluded from consideration)
 */
function identifyChampion(
  players: Map<string, PlayerHand>,
  _coalitionLeaderId: string
): string {
  let bestPlayerId = '';
  let bestScore = Infinity; // Lower is better in this game!

  for (const [playerId, hand] of players) {
    // Coalition leader CAN be the champion! They coordinate to help themselves or others
    // (only skip Vinto caller, not coalition leader)

    // Calculate current guaranteed score (known cards only)
    const knownScore = hand.cards
      .filter((c) => c.isKnown)
      .reduce((sum, c) => sum + (c.card?.value ?? 0), 0);

    // Count unknown cards
    const unknownCount = hand.cards.filter((c) => !c.isKnown).length;

    // For unknown cards, assume average value of 5 (cards range from -1 to 13)
    // This gives us a reasonable estimate of their total score
    const estimatedScore = knownScore + unknownCount * 5;

    // Champion is the player with the LOWEST estimated score
    // They have the best chance of winning, so coalition should help them
    if (estimatedScore < bestScore) {
      bestScore = estimatedScore;
      bestPlayerId = playerId;
    }
  }

  return bestPlayerId;
}

/**
 * Generate comprehensive coalition plan
 * Generates ALL possible action sequences and selects best champion + plan
 */
export function createCoalitionPlan(
  state: GameState,
  knownCards: Map<string, Card>,
  pendingCard?: Card,
  overrideChampionId?: string | null
): CoalitionPlan {
  // If champion is already locked in, use that champion
  if (overrideChampionId) {
    const coalitionState = buildCoalitionState(
      state,
      knownCards,
      pendingCard,
      overrideChampionId
    );
    console.log(`[Coalition] Using locked champion: ${overrideChampionId}`);
    return buildPlanForChampion(coalitionState);
  }

  // Otherwise, evaluate ALL potential champions and pick the best one
  const vintoCallerId = state.vintoCallerId!;

  // Build base coalition state without champion selected
  const baseState = buildCoalitionState(state, knownCards, pendingCard, null);

  let bestPlan: CoalitionPlan | null = null;
  let bestScore = Infinity;

  // Evaluate each potential champion
  for (const playerId of baseState.players.keys()) {
    // Skip only Vinto caller (leader CAN be champion!)
    if (playerId === vintoCallerId) continue;

    // Build plan with this player as champion
    const testState = { ...baseState, championId: playerId };
    const plan = buildPlanForChampion(testState);

    console.log(
      `[Coalition] Evaluated ${playerId} as champion: achievable score ${plan.targetScore}`
    );

    // Pick champion with lowest achievable score
    if (plan.targetScore < bestScore) {
      bestScore = plan.targetScore;
      bestPlan = plan;
    }
  }

  if (!bestPlan) {
    throw new Error('Failed to find any valid champion');
  }

  console.log(
    `[Coalition] Selected champion: ${bestPlan.championId} with target score ${bestPlan.targetScore}`
  );
  return bestPlan;
}

/**
 * Build the best plan for a specific champion
 */
function buildPlanForChampion(coalitionState: CoalitionState): CoalitionPlan {
  console.log(
    `[Coalition] Building plan for champion: ${coalitionState.championId}`
  );
  console.log(
    `[Coalition] Coalition members: ${Array.from(
      coalitionState.players.keys()
    ).join(', ')}`
  );

  // Generate all possible action sequences
  const sequences = generateAllSequences(coalitionState);
  console.log(`[Coalition] Generated ${sequences.length} possible sequences`);

  // Evaluate each sequence and find best
  let bestSequence: CoalitionAction[] = [];
  let bestScore = Infinity;
  let bestConfidence = 0;

  for (const sequence of sequences) {
    const evaluation = evaluateSequence(coalitionState, sequence);

    if (
      evaluation.finalScore < bestScore ||
      (evaluation.finalScore === bestScore &&
        evaluation.confidence > bestConfidence)
    ) {
      bestScore = evaluation.finalScore;
      bestConfidence = evaluation.confidence;
      bestSequence = sequence;
    }
  }

  console.log(
    `[Coalition] Champion: ${
      coalitionState.championId
    }, target: ${bestScore}, confidence: ${(bestConfidence * 100).toFixed(0)}%`
  );
  console.log(`[Coalition] Best sequence has ${bestSequence.length} actions`);

  // Debug: log all actions in best sequence
  if (bestSequence.length > 0) {
    console.log(`[Coalition] Plan details for ${coalitionState.championId}:`);
    bestSequence.forEach((action, idx) => {
      console.log(`  ${idx + 1}. ${action.description}`);
    });
  }

  return {
    championId: coalitionState.championId,
    targetScore: bestScore,
    steps: bestSequence,
    confidence: bestConfidence,
  };
}

/**
 * Generate ALL possible action sequences for coalition
 * Uses breadth-first search with pruning
 */
function generateAllSequences(
  coalitionState: CoalitionState
): CoalitionAction[][] {
  const sequences: CoalitionAction[][] = [];
  const maxDepth = 4; // Each coalition member gets one action
  const maxSequences = 10000; // Limit to prevent timeout

  function explore(
    currentState: CoalitionState,
    currentSequence: CoalitionAction[],
    depth: number,
    usedPlayers: Set<string>
  ) {
    // Hard limits
    if (sequences.length >= maxSequences) return;
    if (depth >= maxDepth) {
      sequences.push([...currentSequence]);
      return;
    }

    // Generate all possible actions for players who haven't acted yet
    const possibleActions = getAllPossibleActions(currentState, usedPlayers);

    // Always include option to stop here (no more actions)
    sequences.push([...currentSequence]);

    // Try each action
    for (const action of possibleActions) {
      if (sequences.length >= maxSequences) return;

      const newState = applyAction(currentState, action);
      const newUsedPlayers = new Set(usedPlayers);
      newUsedPlayers.add(action.playerId);

      explore(
        newState,
        [...currentSequence, action],
        depth + 1,
        newUsedPlayers
      );
    }
  }

  explore(coalitionState, [], 0, new Set());

  return sequences;
}

/**
 * Get ALL possible actions for all coalition members who haven't acted
 */
function getAllPossibleActions(
  state: CoalitionState,
  usedPlayers: Set<string>
): CoalitionAction[] {
  const actions: CoalitionAction[] = [];

  for (const [playerId, hand] of state.players) {
    // Skip players who already acted
    if (usedPlayers.has(playerId)) continue;

    // For each card in hand, check what actions it can perform
    hand.cards.forEach((cardInfo, cardIndex) => {
      if (!cardInfo.isKnown || !cardInfo.card) return;

      const rank = cardInfo.card.rank;

      // 7, 8: Peek own cards
      if (rank === '7' || rank === '8') {
        hand.cards.forEach((target, targetIdx) => {
          if (!target.isKnown) {
            actions.push({
              playerId,
              cardIndex,
              actionType: 'peek-own',
              targets: [{ playerId, cardIndex: targetIdx }],
              description: `${playerId} uses ${rank} to peek own card ${targetIdx}`,
            });
          }
        });
      }

      // 9, 10: Peek opponent cards
      if (rank === '9' || rank === '10') {
        for (const [targetId, targetHand] of state.players) {
          if (targetId === playerId) continue;

          targetHand.cards.forEach((target, targetIdx) => {
            if (!target.isKnown) {
              actions.push({
                playerId,
                cardIndex,
                actionType: 'peek-opponent',
                targets: [{ playerId: targetId, cardIndex: targetIdx }],
                description: `${playerId} uses ${rank} to peek ${targetId}'s card ${targetIdx}`,
              });
            }
          });
        }
      }

      // J (Jack): Swap two cards from different players
      if (rank === 'J') {
        // Can swap any two cards from different players (including self)
        const allPlayers = Array.from(state.players.entries());

        for (let i = 0; i < allPlayers.length; i++) {
          const [player1Id, player1Hand] = allPlayers[i];

          for (let j = i + 1; j < allPlayers.length; j++) {
            const [player2Id, player2Hand] = allPlayers[j];

            // Try all combinations of card positions
            player1Hand.cards.forEach((_, idx1) => {
              player2Hand.cards.forEach((_, idx2) => {
                actions.push({
                  playerId,
                  cardIndex,
                  actionType: 'swap-jack',
                  targets: [
                    { playerId: player1Id, cardIndex: idx1 },
                    { playerId: player2Id, cardIndex: idx2 },
                  ],
                  shouldSwap: true, // Jack always swaps
                  description: `${playerId} swaps ${player1Id}[${idx1}] with ${player2Id}[${idx2}]`,
                });
              });
            });
          }
        }
      }

      // Q (Queen): Peek two cards, optionally swap
      if (rank === 'Q') {
        const allPlayers = Array.from(state.players.entries());

        for (let i = 0; i < allPlayers.length; i++) {
          const [player1Id, player1Hand] = allPlayers[i];

          for (let j = i + 1; j < allPlayers.length; j++) {
            const [player2Id, player2Hand] = allPlayers[j];

            // Peek unknown cards only
            player1Hand.cards.forEach((card1, idx1) => {
              player2Hand.cards.forEach((card2, idx2) => {
                if (!card1.isKnown || !card2.isKnown) {
                  // Generate both: peek without swap, and peek with swap
                  actions.push({
                    playerId,
                    cardIndex,
                    actionType: 'peek-swap-queen',
                    targets: [
                      { playerId: player1Id, cardIndex: idx1 },
                      { playerId: player2Id, cardIndex: idx2 },
                    ],
                    shouldSwap: false,
                    description: `${playerId} peeks ${player1Id}[${idx1}] and ${player2Id}[${idx2}], no swap`,
                  });

                  actions.push({
                    playerId,
                    cardIndex,
                    actionType: 'peek-swap-queen',
                    targets: [
                      { playerId: player1Id, cardIndex: idx1 },
                      { playerId: player2Id, cardIndex: idx2 },
                    ],
                    shouldSwap: true,
                    description: `${playerId} peeks and swaps ${player1Id}[${idx1}] with ${player2Id}[${idx2}]`,
                  });
                }
              });
            });
          }
        }
      }

      if (rank === 'K') {
        // Generate declare actions for all ranks
        const allRanks: Rank[] = [
          'K',
          '2',
          '3',
          '4',
          '5',
          '6',
          '7',
          '8',
          '9',
          '10',
          'J',
          'Q',
          'A',
          'Joker',
        ];

        for (const declareRank of allRanks) {
          const countInHand = hand.cards.filter(
            (c) => c.isKnown && c.card?.rank === declareRank
          ).length;

          // Only generate cascade action if there are cards to cascade
          // For non-action cards (2-6, K, Joker): need at least 1 to cascade
          // For action cards (7-A): need at least 2 to cascade (one to use, one to cascade)
          // Exception: Can declare same rank to cascade self + others
          const isActionCard = ['7', '8', '9', '10', 'J', 'Q', 'A'].includes(
            declareRank
          );
          const isSelfDeclaration = declareRank === rank;

          const shouldGenerate =
            // Non-action cards: cascade if 1+ exists
            (!isActionCard && countInHand >= 1) ||
            // Action cards: cascade if 2+ exist (including self-declaration)
            (isActionCard && countInHand >= 2) ||
            // Self-declaration: action card declares itself to cascade with others
            (isSelfDeclaration && countInHand >= 1);

          if (shouldGenerate) {
            actions.push({
              playerId,
              cardIndex,
              actionType: 'declare-king',
              declaredRank: declareRank,
              description: `${playerId} uses ${rank} to declare ${declareRank} (cascade ${countInHand} cards)`,
            });
          }
        }
      }
    });

    // Option: Discard drawn card instead of using it
    if (hand.cards.some((c) => c.isKnown && c.card?.rank)) {
      actions.push({
        playerId,
        cardIndex: -1,
        actionType: 'discard',
        description: `${playerId} discards drawn card`,
      });
    }
  }

  // Now generate turn combinations if draw pile has cards
  if (state.drawPileSize > 0) {
    for (const [playerId, hand] of state.players) {
      if (usedPlayers.has(playerId)) continue;

      // Generate turns: draw + optional swap + use action + optional toss-in
      const drawnCard = state.knownDrawCard;
      if (drawnCard) {
        // Try swapping drawn card with each position
        hand.cards.forEach((cardInfo, swapIdx) => {
          if (!cardInfo.isKnown || !cardInfo.card) return;

          // After swap, the drawn card is at swapIdx, and original card is drawn
          const cardAfterSwap = drawnCard;
          const rank = cardAfterSwap.rank;

          // Can use the swapped-in card's action
          if (['7', '8', '9', '10', 'J', 'Q', 'K', 'A'].includes(rank)) {
            // For declare actions, check for toss-in opportunities
            if (['7', '8', '9', '10', 'J', 'Q', 'A', 'K'].includes(rank)) {
              const allRanks: Rank[] = ['K', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'A', 'Joker'];
              
              for (const declareRank of allRanks) {
                // Find cards that can be tossed in
                const tossInIndices: number[] = [];
                hand.cards.forEach((c, idx) => {
                  if (idx !== swapIdx && c.isKnown && c.card?.rank === declareRank) {
                    tossInIndices.push(idx);
                  }
                });

                if (tossInIndices.length > 0) {
                  actions.push({
                    playerId,
                    drawnCardSwapWith: swapIdx,
                    cardIndex: swapIdx, // Use the swapped-in card
                    actionType: 'declare-king',
                    declaredRank: declareRank,
                    tossInCards: tossInIndices,
                    description: `${playerId} draws ${rank}, swaps with [${swapIdx}], declares ${declareRank}, tosses in ${tossInIndices.length} cards`,
                  });
                }
              }
            }
          }
        });
      }
    }
  }

  return actions;
}

/**
 * Apply action and return new state (IMMUTABLE)
 */
function applyAction(
  state: CoalitionState,
  action: CoalitionAction
): CoalitionState {
  // Deep clone state
  const newPlayers = new Map<string, PlayerHand>();
  for (const [id, hand] of state.players) {
    newPlayers.set(id, {
      ...hand,
      cards: hand.cards.map((c) => ({
        ...c,
        possibleRanks: [...c.possibleRanks],
      })),
    });
  }

  const newState: CoalitionState = {
    players: newPlayers,
    championId: state.championId,
    vintoCallerId: state.vintoCallerId,
    drawPileSize: state.drawPileSize,
    knownDrawCard: state.knownDrawCard,
  };

  const player = newState.players.get(action.playerId)!;

  // First, handle drawn card swap if specified
  if (action.drawnCardSwapWith !== undefined && newState.drawPileSize > 0) {
    const drawnCard = newState.knownDrawCard || {
      id: 'drawn',
      rank: '5' as Rank,
      value: 5,
      played: false,
    };

    // Swap: drawn card goes to specified index, that card is now "in hand"
    player.cards[action.drawnCardSwapWith] = {
      card: drawnCard,
      isKnown: true,
      possibleRanks: [drawnCard.rank],
    };

    // The original card is now the "drawn" card that will be used/discarded
    // We don't add it back - it gets used by the action

    newState.drawPileSize--;
    newState.knownDrawCard = undefined;
  }

  // Handle toss-in cards BEFORE the main action
  if (action.tossInCards && action.tossInCards.length > 0) {
    // Remove tossed-in cards (in reverse order to preserve indices)
    const sortedIndices = [...action.tossInCards].sort((a, b) => b - a);
    for (const idx of sortedIndices) {
      if (idx !== action.cardIndex) {
        // Don't remove the card we're about to use
        player.cards.splice(idx, 1);
      }
    }
    // Adjust cardIndex if needed after removals
    if (action.cardIndex >= 0) {
      const removedBefore = action.tossInCards.filter(
        (idx) => idx < action.cardIndex
      ).length;
      action = { ...action, cardIndex: action.cardIndex - removedBefore };
    }
  }

  switch (action.actionType) {
    case 'peek-own':
    case 'peek-opponent':
      // Reveal target card (simulate as average value if unknown)
      if (action.targets && action.targets.length > 0) {
        const target = action.targets[0];
        const targetPlayer = newState.players.get(target.playerId)!;
        const cardInfo = targetPlayer.cards[target.cardIndex];

        if (!cardInfo.isKnown) {
          // Reveal card - assume average of possible ranks
          const avgRank =
            cardInfo.possibleRanks[
              Math.floor(cardInfo.possibleRanks.length / 2)
            ];
          targetPlayer.cards[target.cardIndex] = {
            card: {
              id: 'revealed',
              rank: avgRank,
              value: getCardValue(avgRank),
              played: false,
            },
            isKnown: true,
            possibleRanks: [avgRank],
          };
        }
      }
      break;

    case 'swap-jack':
    case 'peek-swap-queen':
      // Swap two cards
      if (action.targets && action.targets.length === 2) {
        const [target1, target2] = action.targets;
        const player1 = newState.players.get(target1.playerId)!;
        const player2 = newState.players.get(target2.playerId)!;

        // For Queen, reveal first if unknown
        if (action.actionType === 'peek-swap-queen') {
          if (!player1.cards[target1.cardIndex].isKnown) {
            const cardInfo = player1.cards[target1.cardIndex];
            const avgRank =
              cardInfo.possibleRanks[
                Math.floor(cardInfo.possibleRanks.length / 2)
              ];
            player1.cards[target1.cardIndex] = {
              card: {
                id: 'revealed',
                rank: avgRank,
                value: getCardValue(avgRank),
                played: false,
              },
              isKnown: true,
              possibleRanks: [avgRank],
            };
          }

          if (!player2.cards[target2.cardIndex].isKnown) {
            const cardInfo = player2.cards[target2.cardIndex];
            const avgRank =
              cardInfo.possibleRanks[
                Math.floor(cardInfo.possibleRanks.length / 2)
              ];
            player2.cards[target2.cardIndex] = {
              card: {
                id: 'revealed',
                rank: avgRank,
                value: getCardValue(avgRank),
                played: false,
              },
              isKnown: true,
              possibleRanks: [avgRank],
            };
          }
        }

        // Swap if action says to swap
        if (
          action.actionType === 'swap-jack' ||
          (action.actionType === 'peek-swap-queen' && action.shouldSwap)
        ) {
          const temp = player1.cards[target1.cardIndex];
          player1.cards[target1.cardIndex] = player2.cards[target2.cardIndex];
          player2.cards[target2.cardIndex] = temp;
        }
      }
      break;

    case 'declare-king':
      // Action card declaration removes the card being used
      if (action.cardIndex >= 0 && action.cardIndex < player.cards.length) {
        player.cards.splice(action.cardIndex, 1);
      }
      // Cascade is handled after the switch statement (affects ALL players)
      break;

    case 'discard':
      // Remove the card being discarded
      if (action.cardIndex >= 0 && action.cardIndex < player.cards.length) {
        player.cards.splice(action.cardIndex, 1);
      }
      break;
  }

  // After action, handle cascade for declared rank (already handled toss-in removal above)
  if (action.declaredRank && action.actionType === 'declare-king') {
    // Count total cards of declared rank across ALL players
    let totalCount = 0;
    for (const [, hand] of newState.players) {
      totalCount += hand.cards.filter(
        (c) => c.isKnown && c.card?.rank === action.declaredRank
      ).length;
    }

    // Determine if cascade triggers
    const isActionCard = ['7', '8', '9', '10', 'J', 'Q', 'A'].includes(
      action.declaredRank
    );
    const shouldCascade = isActionCard ? totalCount >= 2 : totalCount >= 1;

    if (shouldCascade) {
      // CASCADE AFFECTS ALL PLAYERS! Remove declared rank from everyone's hand
      for (const [, hand] of newState.players) {
        hand.cards = hand.cards.filter(
          (c) => !c.isKnown || c.card?.rank !== action.declaredRank
        );
      }
    }
  }

  return newState;
}

/**
 * Evaluate final score of a sequence
 */
function evaluateSequence(
  initialState: CoalitionState,
  sequence: CoalitionAction[]
): { finalScore: number; confidence: number } {
  let state = initialState;

  // Apply all actions in sequence
  for (const action of sequence) {
    state = applyAction(state, action);
  }

  // Calculate champion's final score
  const champion = state.players.get(state.championId)!;
  let knownScore = 0;
  let unknownCards = 0;

  for (const cardInfo of champion.cards) {
    if (cardInfo.isKnown && cardInfo.card) {
      knownScore += cardInfo.card.value;
    } else {
      unknownCards++;
      // Estimate unknown as average of possible ranks
      const avgValue =
        cardInfo.possibleRanks.reduce((sum, r) => sum + getCardValue(r), 0) /
        cardInfo.possibleRanks.length;
      knownScore += avgValue;
    }
  }

  // Confidence: ratio of known cards
  const confidence =
    champion.cards.length > 0
      ? (champion.cards.length - unknownCards) / champion.cards.length
      : 1;

  return { finalScore: knownScore, confidence };
}

/**
 * Execute next action in plan for current player
 */
export function executeCoalitionStep(
  state: GameState,
  plan: CoalitionPlan,
  currentPlayerId: string,
  drawnCard?: Card
): { action: CoalitionAction; shouldUpdatePlan: boolean } | null {
  // If drawn card revealed new information, consider updating plan
  const shouldUpdatePlan = drawnCard !== undefined;

  // Find next action for current player
  const myAction = plan.steps.find((a) => a.playerId === currentPlayerId);

  if (!myAction) {
    console.log(`[Coalition] No action for ${currentPlayerId} in plan`);
    return null;
  }

  return { action: myAction, shouldUpdatePlan };
}

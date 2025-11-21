// Coalition Round Solver
// Coordinates coalition members during final Vinto round to achieve lowest champion score

import { Card, Rank, PlayerState } from '@vinto/shapes';
import { BotActionDecision, BotDecisionContext } from './shapes';
import { calculateHandScore } from './mcts-bot-heuristics';

/**
 * Coalition member's planned action for their final turn
 */
export interface CoalitionMemberPlan {
  playerId: string;
  action: 'draw' | 'take-discard';
  useAction?: boolean; // If drawing an action card
  swapPosition?: number; // If swapping
  declaredRank?: Rank; // If declaring rank during swap
  actionDecision?: BotActionDecision; // For action execution
  tossInPositions?: number[]; // Positions to toss in (if applicable)
}

/**
 * Champion candidate evaluation
 */
interface ChampionCandidate {
  playerId: string;
  currentScore: number;
  potentialScore: number; // Best achievable score with coordination
  numUnknownCards: number;
  hasJoker: boolean;
  hasLowCards: boolean; // Has cards with value <= 1
}

/**
 * Coalition Round Solver
 * Plans coordinated actions for coalition members to minimize champion's score
 */
export class CoalitionRoundSolver {
  private coalitionLeaderId: string;
  private vintoCallerId: string;
  private coalitionMembers: PlayerState[];
  private vintoCallerScore: number;

  // Leader's perfect knowledge of all coalition cards
  private perfectKnowledge: Map<string, Map<number, Card>>;

  // Store draw pile for solver solver
  private drawPile: Card[] = [];

  // Champion ID (selected coalition member to optimize for)
  private coalitionChampionId?: string;

  // Store pending action for cross-phase coordination (choosing -> target selection)
  private pendingAction: SolverAction | null = null;

  constructor(
    leaderId: string,
    vintoCallerId: string,
    allPlayers: PlayerState[],
    perfectKnowledge: Map<string, Map<number, Card>>,
    drawPile?: Card[] // ONLY for testing! Real gameplay doesn't have access to draw pile!
  ) {
    this.coalitionLeaderId = leaderId;
    this.vintoCallerId = vintoCallerId;
    this.perfectKnowledge = perfectKnowledge;
    this.drawPile = drawPile || []; // Keep for backward compatibility with planAllTurns()

    // Identify coalition members (everyone except Vinto caller)
    this.coalitionMembers = allPlayers.filter((p) => p.id !== vintoCallerId);

    // Calculate Vinto caller's score
    const vinto = allPlayers.find((p) => p.id === vintoCallerId);
    this.vintoCallerScore = vinto ? calculateHandScore(vinto.cards) : 50;

    console.log(
      `[Coalition] Initialized: Leader=${leaderId}, Vinto=${vintoCallerId} (score: ${this.vintoCallerScore}), ` +
        `Members=${this.coalitionMembers.map((m) => m.id).join(', ')}, ` +
        `DrawPile=${this.drawPile.length} cards, ` +
        `DrawPileParam=${drawPile ? drawPile.length : 'undefined'}`
    );
  }

  /**
   * Check if player has cascade potential (3+ cards of same rank + King)
   */
  private hasCascadePotential(playerId: string): boolean {
    const memberCards = this.perfectKnowledge.get(playerId);
    if (!memberCards) return false;

    const cards = Array.from(memberCards.values());
    const hasKing = cards.some((c) => c.rank === 'K');
    if (!hasKing) return false;

    // Count ranks
    const rankCounts = new Map<string, number>();
    cards.forEach((card) => {
      rankCounts.set(card.rank, (rankCounts.get(card.rank) || 0) + 1);
    });

    // Check if any rank has 3+ cards
    return Array.from(rankCounts.values()).some((count) => count >= 3);
  }

  /**
   * Select the champion - coalition member with best chance of lowest score
   *
   * Always uses DP solver to evaluate risk-based strategies.
   * Even without a draw pile, can find strategies using cards already in hand (risk=0).
   */
  selectChampion(): string {
    // Always try DP-based selection (works even with empty draw pile for risk=0 strategies)
    console.log('[Coalition] Using DP solver to select champion...');
    const dpChampion = this.selectChampionWithDP();
    if (dpChampion) {
      return dpChampion;
    }

    // Fall back to heuristic-based selection if DP fails
    console.log('[Coalition] DP selection failed, using heuristic...');
    const candidates = this.evaluateChampionCandidates();

    // Select candidate with lowest potential score
    let champion = candidates[0];
    for (const candidate of candidates) {
      if (candidate.potentialScore < champion.potentialScore) {
        champion = candidate;
      } else if (candidate.potentialScore === champion.potentialScore) {
        // Tie-breaking: prefer better path to achieving the score
        // 1. If both can achieve -1, prefer player with cascade potential
        //    (can remove all cards in one action)
        const candidateHasCascade = this.hasCascadePotential(candidate.playerId);
        const championHasCascade = this.hasCascadePotential(champion.playerId);

        if (candidateHasCascade && !championHasCascade) {
          champion = candidate; // Prefer cascade ability
        } else if (!candidateHasCascade && championHasCascade) {
          // Keep current champion
        } else if (candidate.hasJoker && !champion.hasJoker) {
          // 2. If cascade is equal, prefer player who already has Joker (simplest path)
          champion = candidate;
        } else if (!candidate.hasJoker && champion.hasJoker) {
          // Keep current champion
        } else {
          // 3. If everything else is equal, prefer lower current score
          if (candidate.currentScore < champion.currentScore) {
            champion = candidate;
          }
        }
      }
    }

    console.log(
      `[Coalition] Selected champion: ${champion.playerId} ` +
        `(current: ${champion.currentScore}, potential: ${champion.potentialScore}, ` +
        `hasJoker: ${champion.hasJoker})`
    );

    return champion.playerId;
  }

  /**
   * Select champion using DP solver
   * Runs DP for each potential champion and picks the one with best result
   * Works even with empty draw pile by evaluating risk=0 strategies (using cards already in hand)
   */
  private selectChampionWithDP(): string | null {
    interface DPResult {
      playerId: string;
      championScore: number;
      totalRisk: number;
      actionSequence: any[];
    }

    const dpResults: DPResult[] = [];

    // Try each coalition member as champion
    for (const member of this.coalitionMembers) {
      console.log(`[Coalition DP] Evaluating ${member.id} as champion...`);

      // Build initial state with this member as champion
      const playerHands = new Map<string, Card[]>();
      const playerOrder: string[] = [];

      this.coalitionMembers.forEach((m) => {
        const memberCards = this.perfectKnowledge.get(m.id);

        const hand: Card[] = [];
        for (let i = 0; i < m.cards.length; i++) {
          // Use perfect knowledge if available, otherwise use actual card
          const card = memberCards?.get(i) || m.cards[i];
          if (card) hand.push(card);
        }

        if (hand.length > 0) {
          playerHands.set(m.id, hand);
          playerOrder.push(m.id);
        }
      });

      if (playerOrder.length === 0) {
        console.log(`[Coalition DP] No valid hands to evaluate, skipping`);
        continue;
      }

      const initialState = {
        playerHands,
        drawPile: this.drawPile.length > 0 ? [...this.drawPile] : [], // Use empty array if no draw pile
        drawnCard: null,
        discardTop: null,
        currentPlayerIndex: 0,
        playerOrder,
        championId: member.id,
      };

      // Run DP solver for this champion
      const solver = new CoalitionSolver(member.id);
      const solution = solver.findOptimalSequence(initialState);

      console.log(
        `[Coalition DP] ${member.id} result: score=${solution.championScore}, risk=${solution.totalRisk.toFixed(1)}`
      );

      dpResults.push({
        playerId: member.id,
        championScore: solution.championScore,
        totalRisk: solution.totalRisk,
        actionSequence: solution.actionSequence,
      });
    }

    if (dpResults.length === 0) {
      console.log(`[Coalition DP] No valid DP results, falling back to heuristic`);
      return null;
    }

    // Select champion with best DP result
    // Priority: lowest score, then lowest risk
    let best = dpResults[0];
    for (const result of dpResults) {
      if (
        result.championScore < best.championScore ||
        (result.championScore === best.championScore && result.totalRisk < best.totalRisk)
      ) {
        best = result;
      }
    }

    // If all champions have Infinity score, DP can't help - fall back to heuristic
    if (best.championScore === Infinity) {
      console.log(
        `[Coalition DP] All champions have Infinity score (no actions available), falling back to heuristic`
      );
      return null;
    }

    console.log(
      `[Coalition DP] Selected champion: ${best.playerId} ` +
        `(score=${best.championScore}, risk=${best.totalRisk.toFixed(1)})`
    );

    return best.playerId;
  }

  /**
   * Evaluate all coalition members as potential champions
   */
  private evaluateChampionCandidates(): ChampionCandidate[] {
    const candidates: ChampionCandidate[] = [];

    for (const member of this.coalitionMembers) {
      const memberCards = this.perfectKnowledge.get(member.id);
      if (!memberCards) {
        console.log(
          `[Coalition] No perfect knowledge for ${member.id}, skipping`
        );
        continue;
      }

      console.log(
        `[Coalition] Building cards for ${member.id}: handSize=${member.cards.length}, ` +
          `knownPositions=${Array.from(memberCards.keys()).join(',')}`
      );

      // Build complete card array with known cards
      const cards: Card[] = [];
      for (let i = 0; i < member.cards.length; i++) {
        const knownCard = memberCards.get(i);
        if (knownCard) {
          cards.push(knownCard);
          console.log(
            `[Coalition]   pos ${i}: ${knownCard.rank} (from perfect knowledge)`
          );
        } else {
          // Fallback to actual card if not in perfect knowledge
          cards.push(member.cards[i]);
          console.log(
            `[Coalition]   pos ${i}: ${member.cards[i].rank} (from actual hand)`
          );
        }
      }

      const currentScore = calculateHandScore(cards);

      console.log(
        `[Coalition] ${member.id} cards built: ${cards
          .map((c) => c.rank)
          .join(',')}, score=${currentScore}`
      );

      // Evaluate potential (best achievable with coordination)
      const potential = this.evaluatePotentialScore(member.id, cards);

      console.log(
        `[Coalition] Candidate ${member.id}: current=${currentScore}, ` +
          `potential=${potential.score}, cards=${cards
            .map((c) => c.rank)
            .join(',')}`
      );

      candidates.push({
        playerId: member.id,
        currentScore,
        potentialScore: potential.score,
        numUnknownCards: cards.filter((_, i) => !memberCards.has(i)).length,
        hasJoker: cards.some((c) => c.rank === 'Joker'),
        hasLowCards: cards.some((c) => c.value <= 1),
      });
    }

    return candidates;
  }

  /**
   * Evaluate all possible action chains using KNOWN cards only
   *
   * Returns best achievable (risk, score) where:
   * - risk = 0 means no dependency on unknown draws (pure non-draw actions)
   * - risk > 0 means depends on drawing specific cards
   *
   * We prefer:  - Lowest risk first (deterministic > speculative)
   * - Then lowest score
   */
  private evaluateActionChains(playerId: string, cards: Card[]): { risk: number; score: number; plan: string } {
    // Find all non-draw action chains available with KNOWN cards
    const chains: Array<{ risk: number; score: number; plan: string }> = [];

    // Pattern 1: King cascade → Jack swap Joker (ZERO risk, known cards only)
    const hasKing = cards.some(c => c.rank === 'K');
    const teammateHasJack = this.coalitionMembers.some(m => {
      if (m.id === playerId) return false;
      const memberCards = this.perfectKnowledge.get(m.id);
      if (!memberCards) return false;
      return Array.from(memberCards.values()).some(c => c.rank === 'J');
    });
    const teammateWithJoker = this.coalitionMembers.find(m => {
      if (m.id === playerId) return false;
      const memberCards = this.perfectKnowledge.get(m.id);
      if (!memberCards) return false;
      return Array.from(memberCards.values()).some(c => c.rank === 'Joker');
    });

    if (hasKing && teammateHasJack && teammateWithJoker) {
      // This chain uses ONLY known cards, no draws needed!
      // Risk = 0 (completely deterministic)
      const achievedScore = this.simulateKingJackJokerChain(playerId, cards);
      chains.push({
        risk: 0,
        score: achievedScore,
        plan: `King cascade → Jack swap Joker from ${teammateWithJoker.id}`,
      });
    }

    // Pattern 2: Direct Jack swap to get Joker (if player already has good cards)
    // ... (future patterns)

    // Sort by risk (ascending), then score (ascending)
    chains.sort((a, b) => {
      if (a.risk !== b.risk) return a.risk - b.risk;
      return a.score - b.score;
    });

    if (chains.length > 0) {
      const best = chains[0];
      console.log(
        `[ActionChains] ${playerId}: Best chain → risk=${best.risk}, ` +
        `score=${best.score}, plan="${best.plan}"`
      );
      return best;
    }

    // No viable chains found
    return { risk: Infinity, score: Infinity, plan: 'No viable action chain' };
  }

  /**
   * Simulate King→Jack→Joker chain using KNOWN cards
   *
   * Assumptions (based on game rules):
   * 1. Player will draw 1 unknown card
   * 2. Player swaps unknown card with King → King discarded
   * 3. Player uses King action to declare each unique rank in hand → all cards removed via toss-in
   * 4. Player left with just the 1 unknown drawn card
   * 5. Teammate uses Jack to swap that card with Joker
   * 6. Result: Player has [Joker] → score = -1
   *
   * Risk = 0 because actions don't depend on WHAT was drawn, only that something was drawn
   */
  private simulateKingJackJokerChain(playerId: string, cards: Card[]): number {
    // Step 1: Check if player can cascade ALL non-King cards
    // After swapping with King, player has: [drawn_card, ...non-king cards]
    // King cascade can remove cards if there are matching ranks

    const nonKingCards = cards.filter(c => c.rank !== 'K');
    const kingCount = cards.filter(c => c.rank === 'K').length;

    if (kingCount === 0) {
      console.log(`[What-If] ${playerId}: No King, pattern not possible`);
      return Infinity;
    }

    // Count unique ranks in non-King cards
    const rankCounts = new Map<string, number>();
    nonKingCards.forEach(card => {
      const count = rankCounts.get(card.rank) || 0;
      rankCounts.set(card.rank, count + 1);
    });

    // Check if we can cascade all non-King cards
    // Strategy: Use King to declare each unique rank sequentially
    // This removes ALL cards of that rank (including from teammates via toss-in)

    const uniqueRanks = Array.from(rankCounts.keys());
    console.log(
      `[What-If] ${playerId}: ${kingCount} Kings, ${nonKingCards.length} non-King cards, ` +
      `${uniqueRanks.length} unique ranks: [${uniqueRanks.join(',')}]`
    );

    // Optimistic assumption: King can declare multiple ranks in sequence
    // After cascading all unique ranks, player has:
    // - 1 drawn card (unknown)
    // - Possibly remaining Kings (if had multiple)

    let cardsAfterCascade = 1; // The drawn card
    if (kingCount > 1) {
      // Additional Kings remain after swapping 1 King
      cardsAfterCascade += (kingCount - 1);
    }

    console.log(`[What-If] ${playerId}: After cascade, estimated ${cardsAfterCascade} cards remaining`);

    // Step 2: Check if Jack can swap to get Joker
    // If player has exactly 1 card, Jack can swap it for Joker → score = -1
    // If player has more cards (multiple Kings), score will be higher

    if (cardsAfterCascade === 1) {
      // Perfect! Jack swaps the 1 remaining card for Joker
      console.log(`[What-If] ${playerId}: ACHIEVABLE → 1 card after cascade → Jack swaps for Joker → -1`);
      return -1;
    } else {
      // Player has multiple Kings remaining
      // After Jack swap: [Joker, K, K, ...] → score = -1 + 10*(kingCount-1)
      const estimatedScore = -1 + 10 * (kingCount - 1);
      console.log(`[What-If] ${playerId}: Partial → ${cardsAfterCascade} cards (${kingCount-1} Kings) → score ≈ ${estimatedScore}`);
      return estimatedScore;
    }
  }

  /**
   * Evaluate potential score achievable for a coalition member
   */
  private evaluatePotentialScore(
    playerId: string,
    cards: Card[]
  ): { score: number; plan: string } {
    // Calculate current score
    const currentScore = calculateHandScore(cards);

    // Simple heuristic: assume we can remove high-value cards via swaps/toss-ins
    let minScore = currentScore;
    let plan = 'No improvement';

    // Check if we have Joker (-1)
    const hasJoker = cards.some((c) => c.rank === 'Joker');

    // Check for King declarations
    const hasKing = cards.some((c) => c.rank === 'K');

    // Check if any coalition member has Joker that could be swapped to us
    const teammateHasJoker = this.coalitionMembers.some((member) => {
      if (member.id === playerId) return false;
      const memberCards = this.perfectKnowledge.get(member.id);
      if (!memberCards) return false;
      return Array.from(memberCards.values()).some((c) => c.rank === 'Joker');
    });

    // Check if any coalition member has Jack (can facilitate swaps)
    const teammateHasJack = this.coalitionMembers.some((member) => {
      const memberCards = this.perfectKnowledge.get(member.id);
      if (!memberCards) return false;
      return Array.from(memberCards.values()).some((c) => c.rank === 'J');
    });

    // Count matching ranks for cascade potential
    const rankCounts = new Map<string, number>();
    cards.forEach((card) => {
      const count = rankCounts.get(card.rank) || 0;
      rankCounts.set(card.rank, count + 1);
    });

    const maxRankCount = Math.max(...Array.from(rankCounts.values()), 0);
    const hasCascadePotential = maxRankCount >= 3; // 3+ cards of same rank

    console.log(
      `[Coalition] Evaluate ${playerId}: current=${currentScore}, ` +
        `hasJoker=${hasJoker}, teammateJoker=${teammateHasJoker}, ` +
        `teammateJack=${teammateHasJack}, hasKing=${hasKing}, ` +
        `cascade=${hasCascadePotential} (max=${maxRankCount})`
    );

    // Strategy 1: Best case with Joker only
    if (hasJoker) {
      minScore = Math.min(minScore, -1);
      plan = 'Joker-only strategy (has Joker)';
    } else if (hasKing && teammateHasJack && teammateHasJoker) {
      // OPTIMAL PATTERN: Evaluate action chains with known cards
      const chainEval = this.evaluateActionChains(playerId, cards);

      if (chainEval.risk === 0 && chainEval.score === -1) {
        minScore = -1;
        plan = chainEval.plan + ' (OPTIMAL, zero-risk)';
        console.log(`[Coalition] OPTIMAL zero-risk chain for ${playerId}: ${plan}`);
      } else if (chainEval.risk < Infinity) {
        minScore = Math.min(minScore, chainEval.score);
        plan = chainEval.plan + ` (risk=${chainEval.risk})`;
      }
    } else if (teammateHasJoker && teammateHasJack) {
      // Strategy 2: Can receive Joker via Jack swap
      // After getting Joker, try to remove all other cards
      if (hasCascadePotential) {
        // Can remove most cards via cascade after getting Joker
        minScore = Math.min(minScore, -1);
        plan = 'Receive Joker via Jack + cascade away other cards';
      } else {
        // Getting Joker helps but may keep some other cards
        minScore = Math.min(minScore, 5); // Joker + few remaining cards
        plan = 'Receive Joker via Jack';
      }
    } else if (hasCascadePotential && hasKing) {
      // Strategy 3: Cascade with King declaration
      // With K-K-K, can declare and toss in to remove many cards
      const cascadeValue = maxRankCount * 10; // Rough estimate of cards removed
      minScore = Math.min(minScore, Math.max(0, currentScore - cascadeValue));
      plan = `Cascade with ${maxRankCount}x matching ranks`;
    }

    // Check for Ace (value 1)
    const hasAce = cards.some((c) => c.rank === 'A');
    if (hasAce && !hasJoker && minScore > 1) {
      minScore = Math.min(minScore, 5); // Ace + few cards
      plan = 'Keep Ace, remove high cards';
    }

    console.log(
      `[Coalition] ${playerId} potential: ${minScore} (plan: ${plan})`
    );

    return {
      score: minScore,
      plan,
    };
  }

  /**
   * Plan the next coalition member's turn to help champion
   */
  planNextTurn(
    championId: string,
    currentPlayerId: string,
    context: BotDecisionContext
  ): CoalitionMemberPlan {
    console.log(
      `[Coalition] Planning turn for ${currentPlayerId} to help champion ${championId}`
    );

    // Get current champion's hand
    const championCards = this.perfectKnowledge.get(championId);
    const championPlayer = this.coalitionMembers.find(
      (m) => m.id === championId
    );

    if (!championCards || !championPlayer) {
      // Fallback: draw and swap lowest card
      return {
        playerId: currentPlayerId,
        action: 'draw',
        useAction: false,
        swapPosition: 0,
      };
    }

    // Strategy: Help champion by:
    // 1. If currentPlayer == champion: minimize own score
    // 2. If currentPlayer != champion: use Jack to swap good cards to champion

    if (currentPlayerId === championId) {
      return this.planChampionTurn(championId, context);
    } else {
      return this.planSupportTurn(championId, currentPlayerId, context);
    }
  }

  /**
   * Plan all coalition turns (DEPRECATED - for testing only!)
   *
   * IMPORTANT: This method is UNREALISTIC because it peeks at the draw pile.
   * In real gameplay, bots don't have access to the draw pile!
   *
   * Use this ONLY for:
   * - Unit tests that verify optimal strategies
   * - Theoretical analysis of what's possible
   *
   * For real gameplay, use:
   * - shouldTakeDiscard() for the first decision
   * - decideActionWithDrawnCard() for decisions after drawing
   */
  planAllTurns(): Map<string, CoalitionMemberPlan> | null {
    // DISABLED: DP planning without draw pile causes exponential explosion
    // Using heuristic-based incremental planning instead
    console.log(`[Coalition] DP planning DISABLED - using heuristic + incremental planning`);
    return null;

    /* DISABLED CODE:
    // DP solver can work even without draw pile by using non-draw actions
    // (King cascade, Jack swaps)
    console.log(`[Coalition] Planning with drawPile=${this.drawPile.length} cards`);

    // Select champion if not already selected
    if (!this.coalitionChampionId) {
      this.coalitionChampionId = this.selectChampion();
      console.log(
        `[Coalition solver] Selected champion: ${this.coalitionChampionId}`
      );
    }

    console.log('[Coalition] Using solver solver for optimal planning...');

    // Build initial state for solver
    const playerHands = new Map<string, Card[]>();
    const playerOrder: string[] = [];

    this.coalitionMembers.forEach((member) => {
      const memberCards = this.perfectKnowledge.get(member.id);

      // Build hand from perfect knowledge + fallback to actual cards
      const hand: Card[] = [];
      for (let i = 0; i < member.cards.length; i++) {
        // Use perfect knowledge if available, otherwise use actual card
        const card = memberCards?.get(i) || member.cards[i];
        if (card) {
          hand.push(card);
        }
      }

      console.log(
        `[Coalition solver] Built hand for ${member.id}: [${hand
          .map((c) => c.rank)
          .join(',')}] (${hand.length} cards)`
      );

      if (hand.length > 0) {
        playerHands.set(member.id, hand);
        playerOrder.push(member.id);
      }
    });

    console.log(
      `[Coalition solver] Initial state: ${
        playerOrder.length
      } players in order: ${playerOrder.join(', ')}`
    );

    const initialState = {
      playerHands,
      drawPile: [...this.drawPile],
      drawnCard: null, // No card drawn yet for multi-turn planning
      discardTop: null, // TODO: get from context
      currentPlayerIndex: 0,
      playerOrder,
      championId: this.coalitionChampionId,
    };

    // Run solver
    const solver = new CoalitionSolver(this.coalitionChampionId);

    const solution = solver.findOptimalSequence(initialState);

    console.log(
      `[Coalition] Solver found optimal solution: championScore=${solution.championScore}, ` +
        `actions=${solution.actionSequence.length}`
    );

    // Convert solver actions to coalition member plans
    const plans = new Map<string, CoalitionMemberPlan>();

    solution.actionSequence.forEach((solverAction, index) => {
      const playerId = playerOrder[index];
      const plan = this.convertSolverActionToPlan(playerId, solverAction);
      plans.set(playerId, plan);

      console.log(`[Coalition] solver plan for ${playerId}:`, plan);
    });

    return plans;
    */ // End of DISABLED CODE
  }

  /**
   * Convert solver action to coalition member plan
   */
  private convertSolverActionToPlan(
    playerId: string,
    solverAction: any
  ): CoalitionMemberPlan {
    const plan: CoalitionMemberPlan = {
      playerId,
      action: 'draw',
      useAction: false,
    };

    if (solverAction.type === 'draw-swap') {
      plan.swapPosition = solverAction.swapPosition;
      plan.tossInPositions = solverAction.tossInPositions || [];
    } else if (solverAction.type === 'draw-discard') {
      plan.swapPosition = undefined; // Will discard
      plan.tossInPositions = solverAction.tossInPositions || [];
    } else if (solverAction.type === 'declare-cascade') {
      plan.declaredRank = solverAction.declareRank;
      plan.useAction = true;
      plan.tossInPositions = solverAction.tossInPositions || [];
    }

    return plan;
  }

  /**
   * Plan champion's own turn to minimize their score
   */
  private planChampionTurn(
    championId: string,
    _context: BotDecisionContext
  ): CoalitionMemberPlan {
    const championCards = this.perfectKnowledge.get(championId)!;
    const player = this.coalitionMembers.find((m) => m.id === championId)!;

    // Find highest-value card to swap out
    let highestPos = 0;
    let highestValue = -2;

    for (let i = 0; i < player.cards.length; i++) {
      const card = championCards.get(i) || player.cards[i];
      if (card.value > highestValue) {
        highestValue = card.value;
        highestPos = i;
      }
    }

    console.log(
      `[Coalition] Champion ${championId} will swap out highest card at pos ${highestPos} (value: ${highestValue})`
    );

    return {
      playerId: championId,
      action: 'draw',
      useAction: false, // Don't use drawn card's action, just swap
      swapPosition: highestPos,
    };
  }

  /**
   * Plan support member's turn to help champion
   */
  private planSupportTurn(
    championId: string,
    supportId: string,
    context: BotDecisionContext
  ): CoalitionMemberPlan {
    const supportCards = this.perfectKnowledge.get(supportId);

    // Check if support player has Jack (can swap cards)
    const hasJack = supportCards
      ? Array.from(supportCards.values()).some((c) => c.rank === 'J')
      : false;

    if (hasJack) {
      // Use Jack to swap good card to champion
      return this.planJackSwapForChampion(championId, supportId, context);
    }

    // Check if support player has King
    const hasKing = supportCards
      ? Array.from(supportCards.values()).some((c) => c.rank === 'K')
      : false;

    if (hasKing) {
      // Use King to declare Jack and swap
      return this.planKingDeclarationForSwap(championId, supportId, context);
    }

    // Default: draw and swap lowest card
    return {
      playerId: supportId,
      action: 'draw',
      useAction: false, // Don't use drawn card's action, just swap
      swapPosition: 0,
    };
  }

  /**
   * Plan to use Jack to swap good card to champion
   */
  private planJackSwapForChampion(
    championId: string,
    supportId: string,
    _context: BotDecisionContext
  ): CoalitionMemberPlan {
    const supportCards = this.perfectKnowledge.get(supportId)!;
    const championCards = this.perfectKnowledge.get(championId)!;

    // Find Jack position in support player's hand
    let jackPos = -1;
    for (const [pos, card] of supportCards) {
      if (card.rank === 'J') {
        jackPos = pos;
        break;
      }
    }

    // Find best card to give champion (lowest value in support's hand)
    let bestCarsolveros = -1;
    let lowestValue = 999;

    for (const [pos, card] of supportCards) {
      if (pos !== jackPos && card.value < lowestValue) {
        lowestValue = card.value;
        bestCarsolveros = pos;
      }
    }

    // Find worst card in champion's hand to swap out
    let worstChampionPos = 0;
    let highestValue = -2;

    const championPlayer = this.coalitionMembers.find(
      (m) => m.id === championId
    )!;
    for (let i = 0; i < championPlayer.cards.length; i++) {
      const card = championCards.get(i) || championPlayer.cards[i];
      if (card.value > highestValue) {
        highestValue = card.value;
        worstChampionPos = i;
      }
    }

    console.log(
      `[Coalition] ${supportId} using Jack to swap: ` +
        `support[${bestCarsolveros}] (val: ${lowestValue}) <-> champion[${worstChampionPos}] (val: ${highestValue})`
    );

    // Plan: Swap Jack into hand, use its action
    return {
      playerId: supportId,
      action: 'draw',
      swapPosition: jackPos,
      declaredRank: 'J',
      actionDecision: {
        targets: [
          { playerId: supportId, position: bestCarsolveros },
          { playerId: championId, position: worstChampionPos },
        ],
      },
    };
  }

  /**
   * Plan to use King to declare Jack and perform swap
   */
  private planKingDeclarationForSwap(
    championId: string,
    supportId: string,
    _context: BotDecisionContext
  ): CoalitionMemberPlan {
    // Similar to Jack swap, but using King to declare it
    const supportCards = this.perfectKnowledge.get(supportId)!;
    const championCards = this.perfectKnowledge.get(championId)!;

    // Find King position
    let kingPos = -1;
    for (const [pos, card] of supportCards) {
      if (card.rank === 'K') {
        kingPos = pos;
        break;
      }
    }

    // Find best card to give champion
    let bestCarsolveros = -1;
    let lowestValue = 999;

    for (const [pos, card] of supportCards) {
      if (pos !== kingPos && card.value < lowestValue) {
        lowestValue = card.value;
        bestCarsolveros = pos;
      }
    }

    // Find worst card in champion's hand
    let worstChampionPos = 0;
    let highestValue = -2;

    const championPlayer = this.coalitionMembers.find(
      (m) => m.id === championId
    )!;
    for (let i = 0; i < championPlayer.cards.length; i++) {
      const card = championCards.get(i) || championPlayer.cards[i];
      if (card.value > highestValue) {
        highestValue = card.value;
        worstChampionPos = i;
      }
    }

    console.log(
      `[Coalition] ${supportId} using King->Jack to swap: ` +
        `support[${bestCarsolveros}] (val: ${lowestValue}) <-> champion[${worstChampionPos}] (val: ${highestValue})`
    );

    return {
      playerId: supportId,
      action: 'draw',
      swapPosition: kingPos,
      declaredRank: 'K',
      actionDecision: {
        declaredRank: 'J', // King declares Jack
        targets: [
          { playerId: supportId, position: bestCarsolveros },
          { playerId: championId, position: worstChampionPos },
        ],
      },
    };
  }

  /**
   * Decide whether to draw from pile or take from discard
   *
   * This is the FIRST decision in the turn flow (before any card is drawn).
   * Bot only sees the discard top (one card or null).
   *
   * @param context - The current decision context
   * @param discardTop - The top card of discard pile (or null if empty)
   * @returns 'draw' to draw from pile, or 'take-discard' to take discard
   */
  shouldTakeDiscard(
    context: BotDecisionContext,
    discardTop: Card | null
  ): 'draw' | 'take-discard' {
    // Ensure champion is selected
    if (!this.coalitionChampionId) {
      this.coalitionChampionId = this.selectChampion();
    }

    // IMPORTANT: Use the ACTUAL player taking the turn
    const currentPlayer = context.gameState.players[context.gameState.currentPlayerIndex];
    const currentPlayerId = currentPlayer.id;
    const isChampion = currentPlayerId === this.coalitionChampionId;
    const hand = currentPlayer.cards;

    console.log(
      `[Coalition] ${currentPlayerId} (actual player) deciding: draw or take-discard? discardTop=${
        discardTop?.rank || 'null'
      }, isChampion=${isChampion} (decisionMaker=${context.botId})`
    );

    // Clear pending action at start of turn decision
    this.pendingAction = null;

    // If no discard available, must draw
    if (!discardTop) {
      return 'draw';
    }

    // Strategy: Take discard if it's valuable for actions
    // - Peek actions (7, 8, 9, 10) help gather information
    // - King helps with cascade (if have duplicates)
    // Otherwise: Draw from pile (less information revealed)

    if (discardTop.actionText && !discardTop.played) {
      // Take action cards that might be useful
      const usefulActions = ['7', '8', '9', '10', 'K'];
      if (usefulActions.includes(discardTop.rank)) {
        console.log(
          `[Coalition] ${currentPlayerId} taking ${discardTop.rank} from discard (useful action)`
        );
        return 'take-discard';
      }
    }

    // Default: draw from pile
    return 'draw';
  }

  /**
   * Make a decision for the current player's turn based on the drawn card
   *
   * This is the SECOND decision in the turn flow (after drawing ONE card).
   * The engine gives us ONE card (not the whole pile!).
   *
   * IMPORTANT: This is realistic - we only see ONE drawn card at a time!
   *
   * @param context - The current decision context
   * @param drawnCard - The ONE card that was drawn (from pendingCard)
   * @returns The best swap position, or null to discard
   */
  decideActionWithDrawnCard(
    context: BotDecisionContext,
    drawnCard: Card
  ): {
    swapPosition: number | null;
    useAction: boolean;
  } {
    // IMPORTANT: Use the ACTUAL player taking the turn, not the decision maker
    // In coalition mode, context.botId is the leader, but we need the actual player's hand
    const currentPlayer = context.gameState.players[context.gameState.currentPlayerIndex];
    const currentPlayerId = currentPlayer.id;

    // Ensure champion is selected
    if (!this.coalitionChampionId) {
      this.coalitionChampionId = this.selectChampion();
    }

    console.log(
      `[Coalition] ${currentPlayerId} (actual player) deciding action with drawn card: ${drawnCard.rank} (decisionMaker=${context.botId})`
    );

    // Build current state for solver
    const playerHands = new Map<string, Card[]>();
    const playerOrder: string[] = [];

    // Add all coalition members to the state
    this.coalitionMembers.forEach((member) => {
      const memberCards = this.perfectKnowledge.get(member.id);

      // Build hand from perfect knowledge + fallback to actual cards
      const hand: Card[] = [];
      for (let i = 0; i < member.cards.length; i++) {
        // Use perfect knowledge if available, otherwise use actual card
        const card = memberCards?.get(i) || member.cards[i];
        if (card) {
          hand.push(card);
        }
      }

      console.log(
        `[Coalition.decideAction] Built hand for ${member.id}: [${hand.map(c => c.rank).join(',')}] (${hand.length} cards)`
      );

      if (hand.length > 0) {
        playerHands.set(member.id, hand);
        if (member.id === currentPlayerId) {
          playerOrder.push(member.id); // Current player is first
        }
      }
    });

    // Simple heuristic decision: evaluate each swap position
    // Choose the one that results in lowest champion score
    const currentHand = playerHands.get(currentPlayerId) || [];

    let bestSwapPos: number | null = null;
    let bestUseAction = false;
    let bestScore = Infinity;

    // Option 1: Discard without swapping
    const discardScore = calculateHandScore(currentHand);
    if (discardScore < bestScore) {
      bestScore = discardScore;
      bestSwapPos = null;
      bestUseAction = false;
    }

    // Option 2: Swap into each position
    for (let pos = 0; pos < currentHand.length; pos++) {
      const swappedHand = [...currentHand];
      const swappedOut = swappedHand[pos];
      swappedHand[pos] = drawnCard;

      // Calculate champion score after this swap
      const championHand = currentPlayerId === this.coalitionChampionId
        ? swappedHand
        : (playerHands.get(this.coalitionChampionId) || []);

      const score = calculateHandScore(championHand);

      // Check if swapped-out card has an action
      const hasAction = swappedOut.actionText && !swappedOut.played;

      if (score < bestScore) {
        bestScore = score;
        bestSwapPos = pos;
        bestUseAction = hasAction || false;
      }
    }

    console.log(
      `[Coalition] Heuristic decision: swapPosition=${bestSwapPos}, ` +
      `useAction=${bestUseAction}, championScore=${bestScore}`
    );

    return { swapPosition: bestSwapPos, useAction: bestUseAction };
  }

  /**
   * Get King cascade ranks if a King cascade action is pending
   * Called during rank declaration phase (after swapping with King)
   */
  getKingCascadeRanks(): Rank[] | null {
    if (this.pendingAction?.type !== 'use-action') {
      return null;
    }

    // Multi-rank cascade
    if (this.pendingAction.declareRanks && this.pendingAction.declareRanks.length > 0) {
      return this.pendingAction.declareRanks;
    }

    // Single-rank cascade
    if (this.pendingAction.declareRank) {
      return [this.pendingAction.declareRank];
    }

    return null;
  }

  /**
   * Get Jack swap targets if a Jack swap action is pending
   * Called during target selection phase
   */
  getJackSwapTargets(): {
    fromPlayerId: string;
    fromPosition: number;
    toPlayerId: string;
    toPosition: number;
  } | null {
    if (this.pendingAction?.type !== 'jack-swap') {
      return null;
    }

    const { jackSwapFrom, jackSwapTo } = this.pendingAction;
    if (!jackSwapFrom || !jackSwapTo) {
      return null;
    }

    return {
      fromPlayerId: jackSwapFrom.playerId,
      fromPosition: jackSwapFrom.position,
      toPlayerId: jackSwapTo.playerId,
      toPosition: jackSwapTo.position,
    };
  }
}

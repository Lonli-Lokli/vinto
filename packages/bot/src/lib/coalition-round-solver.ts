// Coalition Round Solver
// Coordinates coalition members during final Vinto round to achieve lowest champion score

import { Card, Rank, PlayerState } from '@vinto/shapes';
import { BotActionDecision, BotDecisionContext } from './shapes';
import { calculateHandScore } from './mcts-bot-heuristics';
import { CoalitionDPSolver } from './coalition-dp-solver';

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

  // Store draw pile for DP solver
  private drawPile: Card[] = [];

  // Champion ID (selected coalition member to optimize for)
  private coalitionChampionId?: string;

  constructor(
    leaderId: string,
    vintoCallerId: string,
    allPlayers: PlayerState[],
    perfectKnowledge: Map<string, Map<number, Card>>,
    drawPile?: Card[] // Optional: if known, use DP solver
  ) {
    this.coalitionLeaderId = leaderId;
    this.vintoCallerId = vintoCallerId;
    this.perfectKnowledge = perfectKnowledge;
    this.drawPile = drawPile || [];

    // Identify coalition members (everyone except Vinto caller)
    this.coalitionMembers = allPlayers.filter((p) => p.id !== vintoCallerId);

    // Calculate Vinto caller's score
    const vinto = allPlayers.find((p) => p.id === vintoCallerId);
    this.vintoCallerScore = vinto ? calculateHandScore(vinto.cards) : 50;

    console.log(
      `[Coalition] Initialized: Leader=${leaderId}, Vinto=${vintoCallerId} (score: ${this.vintoCallerScore}), ` +
        `Members=${this.coalitionMembers.map((m) => m.id).join(', ')}, ` +
        `DrawPile=${drawPile ? `${drawPile.length} cards` : 'unknown'}`
    );
  }

  /**
   * Select the champion - coalition member with best chance of lowest score
   */
  selectChampion(): string {
    const candidates = this.evaluateChampionCandidates();

    // Select candidate with lowest potential score
    let champion = candidates[0];
    for (const candidate of candidates) {
      if (candidate.potentialScore < champion.potentialScore) {
        champion = candidate;
      } else if (candidate.potentialScore === champion.potentialScore) {
        // Tie-breaking: prefer better path to achieving the score
        // 1. Prefer player who already has Joker (simplest path)
        if (candidate.hasJoker && !champion.hasJoker) {
          champion = candidate;
        } else if (!candidate.hasJoker && champion.hasJoker) {
          // Keep current champion
        } else {
          // 2. If neither or both have Joker, prefer lower current score
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
   * Plan all coalition turns using Dynamic Programming (OPTIMAL)
   *
   * If draw pile is known, use DP to find guaranteed optimal sequence.
   * Otherwise fall back to heuristics.
   */
  planAllTurnsWithDP(): Map<string, CoalitionMemberPlan> | null {
    if (this.drawPile.length === 0) {
      console.log('[Coalition] Draw pile unknown, cannot use DP solver');
      return null;
    }

    // Select champion if not already selected
    if (!this.coalitionChampionId) {
      this.coalitionChampionId = this.selectChampion();
      console.log(
        `[Coalition DP] Selected champion: ${this.coalitionChampionId}`
      );
    }

    console.log('[Coalition] Using DP solver for optimal planning...');

    // Build initial state for DP
    const playerHands = new Map<string, Card[]>();
    const playerOrder: string[] = [];

    this.coalitionMembers.forEach((member) => {
      const memberCards = this.perfectKnowledge.get(member.id);
      if (!memberCards) {
        console.log(
          `[Coalition DP] WARNING: No perfect knowledge for ${member.id}, skipping!`
        );
        return;
      }

      // Build hand from perfect knowledge
      const hand: Card[] = [];
      for (let i = 0; i < member.cards.length; i++) {
        const card = memberCards.get(i);
        if (card) {
          hand.push(card);
        }
      }

      console.log(
        `[Coalition DP] Built hand for ${member.id}: [${hand
          .map((c) => c.rank)
          .join(',')}] (${hand.length} cards)`
      );

      playerHands.set(member.id, hand);
      playerOrder.push(member.id);
    });

    console.log(
      `[Coalition DP] Initial state: ${
        playerOrder.length
      } players in order: ${playerOrder.join(', ')}`
    );

    const initialState = {
      playerHands,
      drawPile: [...this.drawPile],
      discardTop: null, // TODO: get from context
      currentPlayerIndex: 0,
      playerOrder,
      championId: this.coalitionChampionId,
    };

    // Run DP solver
    const dpSolver = new CoalitionDPSolver(
      this.coalitionChampionId,
      this.vintoCallerId,
      playerOrder,
      this.perfectKnowledge
    );

    const solution = dpSolver.findOptimalSequence(initialState);

    console.log(
      `[Coalition] DP found optimal solution: championScore=${solution.championScore}, ` +
        `actions=${solution.actionSequence.length}`
    );

    // Convert DP actions to coalition member plans
    const plans = new Map<string, CoalitionMemberPlan>();

    solution.actionSequence.forEach((dpAction, index) => {
      const playerId = playerOrder[index];
      const plan = this.convertDPActionToPlan(playerId, dpAction);
      plans.set(playerId, plan);

      console.log(`[Coalition] DP plan for ${playerId}:`, plan);
    });

    return plans;
  }

  /**
   * Convert DP action to coalition member plan
   */
  private convertDPActionToPlan(
    playerId: string,
    dpAction: any
  ): CoalitionMemberPlan {
    const plan: CoalitionMemberPlan = {
      playerId,
      action: 'draw',
      useAction: false,
    };

    if (dpAction.type === 'draw-swap') {
      plan.swapPosition = dpAction.swapPosition;
      plan.tossInPositions = dpAction.tossInPositions || [];
    } else if (dpAction.type === 'draw-discard') {
      plan.swapPosition = undefined; // Will discard
      plan.tossInPositions = dpAction.tossInPositions || [];
    } else if (dpAction.type === 'declare-cascade') {
      plan.declaredRank = dpAction.declareRank;
      plan.useAction = true;
      plan.tossInPositions = dpAction.tossInPositions || [];
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
    let bestCardPos = -1;
    let lowestValue = 999;

    for (const [pos, card] of supportCards) {
      if (pos !== jackPos && card.value < lowestValue) {
        lowestValue = card.value;
        bestCardPos = pos;
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
        `support[${bestCardPos}] (val: ${lowestValue}) <-> champion[${worstChampionPos}] (val: ${highestValue})`
    );

    // Plan: Swap Jack into hand, use its action
    return {
      playerId: supportId,
      action: 'draw',
      swapPosition: jackPos,
      declaredRank: 'J',
      actionDecision: {
        targets: [
          { playerId: supportId, position: bestCardPos },
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
    let bestCardPos = -1;
    let lowestValue = 999;

    for (const [pos, card] of supportCards) {
      if (pos !== kingPos && card.value < lowestValue) {
        lowestValue = card.value;
        bestCardPos = pos;
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
        `support[${bestCardPos}] (val: ${lowestValue}) <-> champion[${worstChampionPos}] (val: ${highestValue})`
    );

    return {
      playerId: supportId,
      action: 'draw',
      swapPosition: kingPos,
      declaredRank: 'K',
      actionDecision: {
        declaredRank: 'J', // King declares Jack
        targets: [
          { playerId: supportId, position: bestCardPos },
          { playerId: championId, position: worstChampionPos },
        ],
      },
    };
  }
}

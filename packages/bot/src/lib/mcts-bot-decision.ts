// services/mcts-bot-decision.ts
import copy from 'fast-copy';
import { BotMemory } from './bot-memory';
import { MCTSMoveGenerator } from './mcts-move-generator';
import { MCTSStateTransition } from './mcts-state-transition';
import { VintoRoundSolver } from './vinto-round-solver';

import {
  Card,
  Difficulty,
  GameState,
  getCardAction,
  getCardShortDescription,
  getCardValue,
  Pile,
  PlayerState,
  Rank,
} from '@vinto/shapes';
import {
  MCTSConfig,
  MCTS_DIFFICULTY_CONFIGS,
  MCTSGameState,
  MCTSMove,
  MCTSNode,
  MCTSPlayerState,
} from './mcts-types';
import {
  SWAP_HAND_SIZE_WEIGHT,
  SWAP_KNOWLEDGE_WEIGHT,
  SWAP_SCORE_WEIGHT,
} from './constants';
import {
  evaluateTossInPotential,
  evaluateRelativePosition,
  evaluateActionCardValue,
  evaluateInformationAdvantage,
  evaluateThreatLevel,
} from './evaluation-helpers';

export interface BotDecisionContext {
  botId: string;
  botPlayer: PlayerState;
  allPlayers: PlayerState[];
  gameState: GameState;
  discardTop?: Card;
  discardPile: Pile; // Full discard pile history for tracking removed cards
  pendingCard?: Card;
  currentAction?: {
    targetType: string;
    card: Card;
    peekTargets?: Array<{
      playerId: string;
      position: number;
      card: Card | undefined;
    }>;
  };
  // Opponent knowledge - what this bot knows about opponents' cards
  opponentKnowledge: Map<string, Map<number, Card>>; // opponentId -> position -> card
  // Coalition context (for final round)
  coalitionLeaderId?: string | null; // ID of the coalition leader (if in final round)
  isCoalitionMember?: boolean; // True if bot is part of coalition against Vinto caller
}

export interface BotActionTarget {
  playerId: string;
  position: number; // -1 for player-level targeting
}

export interface BotActionDecision {
  targets: BotActionTarget[];
  shouldSwap?: boolean; // For peek-then-swap decisions
  declaredRank?: Rank; // For King declarations
}

export interface BotTurnDecision {
  action: 'draw' | 'take-discard';
  cardChoice?: 'use-action' | 'swap' | 'discard'; // If drawing
  swapPosition?: number; // If swapping
  actionDecision?: BotActionDecision; // Pre-computed action plan when taking from discard
}

/**
 * Predicted outcome of a complete turn after swapping
 * Used by the Turn Consequence Simulator
 */
interface TurnOutcome {
  finalHandSize: number;
  finalKnownCards: number;
  finalScore: number;
}

export interface BotDecisionService {
  // Main turn decisions
  decideTurnAction(context: BotDecisionContext): BotTurnDecision;

  // Card action decisions
  shouldUseAction(drawnCard: Card, context: BotDecisionContext): boolean;

  // Action target selections
  selectActionTargets(context: BotDecisionContext): BotActionDecision;

  // Specific action decisions
  shouldSwapAfterPeek(
    peekedCards: Card[],
    context: BotDecisionContext
  ): boolean;
  selectKingDeclaration(context: BotDecisionContext): Rank;

  // Utility decisions
  shouldParticipateInTossIn(
    discardedRanks: [Rank, ...Rank[]],
    context: BotDecisionContext
  ): boolean;
  selectBestSwapPosition(
    drawnCard: Card,
    context: BotDecisionContext
  ): number | null;

  // Game-ending decision
  shouldCallVinto(context: BotDecisionContext): boolean;
}

// Factory for creating bot decision services
export class BotDecisionServiceFactory {
  static create(difficulty: Difficulty): BotDecisionService {
    // Use MCTS bot for all difficulties
    // Difficulty controls memory accuracy and MCTS iterations, not decision quality
    return new MCTSBotDecisionService(difficulty);
  }
}

/**
 * MCTS-based Bot Decision Service
 * Uses Monte Carlo Tree Search with imperfect information (determinization)
 */
export class MCTSBotDecisionService implements BotDecisionService {
  private botMemory: BotMemory;
  private config: MCTSConfig;
  private botId: string;
  // Map of player ID -> cached action plan (for coalition scenarios where leader plans for multiple members)
  private cachedActionPlans = new Map<string, BotActionDecision>();

  constructor(private difficulty: Difficulty) {
    this.botId = ''; // Will be set in first call
    this.config = MCTS_DIFFICULTY_CONFIGS[difficulty];
    this.botMemory = new BotMemory('', difficulty); // TODO, will be recreated
  }

  // ========== BotDecisionService Interface Implementation ==========

  decideTurnAction(context: BotDecisionContext): BotTurnDecision {
    this.initializeIfNeeded(context);

    // Set context for coalition evaluation
    this.setBotDecisionContext(context);

    console.log(
      `[MCTS] ${this.botId} deciding turn action (${this.difficulty})`
    );

    // HEURISTIC: Always take 7 or 8 from discard if bot has unknown cards
    // These peek actions are STRICTLY better than drawing:
    // - Guaranteed knowledge gain (vs. random draw)
    // - No hand size increase
    // - No score increase
    // - No information revealed to opponents
    if (context.discardTop) {
      const { rank: discardRank, played } = context.discardTop;
      const hasUnknownCards = this.countUnknownCards(context.botPlayer) > 0;

      if (
        (discardRank === '7' || discardRank === '8') &&
        hasUnknownCards &&
        !played
      ) {
        console.log(
          `[MCTS] Heuristic: Always taking ${discardRank} from discard (has unknown cards)`
        );
        return { action: 'take-discard' };
      }
    }

    const gameState = this.constructGameState(context);
    const result = this.runMCTSWithPlan(gameState);

    if (result.move.type === 'take-discard') {
      // If taking from discard and we have a follow-up action plan, cache it
      if (result.actionPlan) {
        return {
          action: 'take-discard',
          actionDecision: result.actionPlan,
        };
      }
      return { action: 'take-discard' };
    }

    return { action: 'draw' };
  }

  shouldUseAction(drawnCard: Card, context: BotDecisionContext): boolean {
    this.initializeIfNeeded(context);

    // Set context for coalition evaluation
    this.setBotDecisionContext(context);

    if (!drawnCard.actionText || drawnCard.played) return false;

    // HEURISTIC: Always use 7 or 8 peek actions if bot has unknown cards
    // These are STRICTLY better than swapping because:
    // - Guaranteed knowledge gain
    // - No hand size increase (unlike swapping which keeps the card)
    // - No score increase
    const hasUnknownCards = this.countUnknownCards(context.botPlayer) > 0;
    if ((drawnCard.rank === '7' || drawnCard.rank === '8') && hasUnknownCards) {
      console.log(
        `[MCTS] Heuristic: Always using ${drawnCard.rank} peek action (has unknown cards)`
      );
      // Note: 7/8 don't need action plan caching since they have simple single-target selection
      return true;
    }

    // HEURISTIC: For Ace, compare action value vs swap value
    // Ace is worth only 1 point, so swapping it for a high card is often better
    // than using the force-draw action (which is only good defensively)
    if (drawnCard.rank === 'A') {
      // Check if we have high-value known cards to swap
      let maxKnownValue = 0;
      for (let pos = 0; pos < context.botPlayer.cards.length; pos++) {
        if (context.botPlayer.knownCardPositions.includes(pos)) {
          const card = context.botPlayer.cards[pos];
          if (card.value > maxKnownValue) {
            maxKnownValue = card.value;
          }
        }
      }

      // If we can swap Ace for a card worth 8+, do that instead
      if (maxKnownValue >= 8) {
        console.log(
          `[MCTS] Heuristic: Swapping Ace instead of using action (can replace ${maxKnownValue})`
        );
        this.cachedActionPlans.delete(context.botId); // Clear any cached plan for this player
        return false;
      }

      // Check if any opponent is close to calling Vinto (defensive Ace use)
      const botScore = context.botPlayer.cards.reduce(
        (sum, c) => sum + c.value,
        0
      );
      for (const player of context.allPlayers) {
        if (player.id === context.botId) continue;
        const opponentScore = player.cards.reduce((sum, c) => sum + c.value, 0);

        // If opponent has low score and few cards, Ace force-draw is valuable
        if (opponentScore < botScore - 3 && player.cards.length <= 3) {
          console.log(
            `[MCTS] Heuristic: Using Ace action (defensive - opponent near Vinto)`
          );
          // Ace has simple single-target selection, no need to cache plan
          this.cachedActionPlans.delete(context.botId);
          return true;
        }
      }

      // Default: swap Ace (low value card, action is situational)
      console.log(
        `[MCTS] Heuristic: Swapping Ace (low value, no defensive need)`
      );
      this.cachedActionPlans.delete(context.botId);
      return false;
    }

    // For other action cards (J, Q, 9, 10), run MCTS and cache the action plan
    // This ensures we execute the planned action targets instead of re-evaluating
    const gameState = this.constructGameState(context);
    const result = this.runMCTSWithPlan(gameState);

    if (result.move.type === 'use-action') {
      // Cache the action plan for Jack/Queen/9/10 actions
      // These require complex target selection that should be planned ahead
      if (result.actionPlan) {
        this.cachedActionPlans.set(context.botId, result.actionPlan);
        console.log(
          `[Use Action] Caching action plan for ${context.botId} with ${drawnCard.rank}:`,
          result.actionPlan
        );
      } else {
        this.cachedActionPlans.delete(context.botId);
      }
      return true;
    }

    this.cachedActionPlans.delete(context.botId);
    return false;
  }

  selectActionTargets(context: BotDecisionContext): BotActionDecision {
    this.initializeIfNeeded(context);

    // Check if we have a cached action plan from King declaration or take-discard
    const cachedPlan = this.cachedActionPlans.get(context.botId);
    if (cachedPlan) {
      console.log(
        `[Action Targets] Using cached action plan for ${context.botId}`,
        cachedPlan
      );
      this.cachedActionPlans.delete(context.botId); // Clear cache after use
      return cachedPlan;
    }

    // Set context for coalition evaluation
    this.setBotDecisionContext(context);

    const gameState = this.constructGameState(context);
    const bestMove = this.runMCTS(gameState);

    if (bestMove.targets) {
      return {
        targets: bestMove.targets.map((t) => ({
          playerId: t.playerId,
          position: t.position,
        })),
        // Use shouldSwap from the move (for Queen) or check if type is 'swap' (for Jack)
        shouldSwap: bestMove.shouldSwap ?? bestMove.type === 'swap',
        declaredRank: bestMove.declaredRank,
      };
    }

    return { targets: [] };
  }

  shouldSwapAfterPeek(
    peekedCards: Card[],
    context: BotDecisionContext
  ): boolean {
    this.initializeIfNeeded(context);

    // Update memory with peeked cards
    if (context.currentAction?.peekTargets) {
      context.currentAction.peekTargets.forEach((target, index) => {
        if (peekedCards[index]) {
          this.botMemory.observeCard(
            peekedCards[index],
            target.playerId,
            target.position
          );
        }
      });
    }

    const gameState = this.constructGameState(context);
    const bestMove = this.runMCTS(gameState);

    return bestMove.type === 'swap' && bestMove.shouldSwap === true;
  }

  selectKingDeclaration(context: BotDecisionContext): Rank {
    this.initializeIfNeeded(context);

    const gameState = this.constructGameState(context);
    const result = this.runMCTSWithPlan(gameState);

    if (result.move.declaredRank) {
      // Cache the action plan if declaring an action card
      // This ensures we execute the planned action when selectActionTargets is called
      if (result.actionPlan) {
        this.cachedActionPlans.set(context.botId, result.actionPlan);
        console.log(
          `[King Declaration] Caching action plan for ${context.botId} with declared ${result.move.declaredRank}:`,
          result.actionPlan
        );
      } else {
        this.cachedActionPlans.delete(context.botId);
      }

      return result.move.declaredRank;
    }

    // Fallback to Q (most powerful)
    this.cachedActionPlans.delete(context.botId);
    return 'Q';
  }

  /**
   * IMPORTANT: Tossing in is ALWAYS beneficial because:
   * 1. Reduces hand size (closer to winning)
   * 2. Reduces score (lower points)
   *
   * Therefore, we use simple heuristic: participate if we have ANY matching cards.
   * No need for MCTS evaluation - this is a no-brainer decision.
   */
  shouldParticipateInTossIn(
    discardedRanks: [Rank, ...Rank[]],
    context: BotDecisionContext
  ): boolean {
    const ranksToCheck: Rank[] = discardedRanks.filter(
      (rank) => getCardValue(rank) >= 0
    );
    this.initializeIfNeeded(context);

    // Simple check: do we have any cards that match the toss-in ranks?
    const hasMatchingCard = context.botPlayer.cards.some((card, index) => {
      // Only consider known cards
      if (!context.botPlayer.knownCardPositions.includes(index)) {
        return false;
      }
      // Check if card matches any of the toss-in ranks
      return ranksToCheck.includes(card.rank);
    });

    return hasMatchingCard;
  }

  selectBestSwapPosition(
    drawnCard: Card,
    context: BotDecisionContext
  ): number | null {
    this.initializeIfNeeded(context);

    // Set context for coalition evaluation
    this.setBotDecisionContext(context);

    // Temporarily add drawn card to memory for accurate simulation
    const tempMemory = new BotMemory(this.botId, this.difficulty);

    // Copy current memory state
    context.botPlayer.cards.forEach((card, position) => {
      if (context.botPlayer.knownCardPositions.includes(position)) {
        tempMemory.observeCard(card, this.botId, position);
      }
    });

    // Add drawn card as temporary knowledge
    tempMemory.observeCard(drawnCard, this.botId, -1); // -1 indicates pending card

    let bestPosition = 0;
    let bestScore = -Infinity;

    // Evaluate each possible swap position
    for (
      let position = 0;
      position < context.botPlayer.cards.length;
      position++
    ) {
      const outcome = this.simulateTurnOutcome(
        drawnCard,
        position,
        context,
        tempMemory
      );

      const outcomeScore = this.calculateOutcomeScore(outcome);

      console.log(
        `[SwapSelector] Position ${position}: handSize=${outcome.finalHandSize}, ` +
          `known=${outcome.finalKnownCards}/${context.botPlayer.cards.length}, ` +
          `score=${outcome.finalScore.toFixed(
            1
          )}, outcomeScore=${outcomeScore.toFixed(1)}`
      );

      if (outcomeScore > bestScore) {
        bestScore = outcomeScore;
        bestPosition = position;
      }
    }

    console.log(
      `[SwapSelector] Selected position ${bestPosition} with score ${bestScore.toFixed(
        1
      )}`
    );

    return bestPosition;
  }

  // ========== Turn Consequence Simulator ==========

  /**
   * Simulate the full consequence of swapping the drawn card with a specific position
   * This is the "what-if" engine that predicts the final turn state
   */
  private simulateTurnOutcome(
    drawnCard: Card,
    swapPosition: number,
    context: BotDecisionContext,
    tempMemory: BotMemory
  ): TurnOutcome {
    const botPlayer = context.botPlayer;

    // Stage 1: Swap Simulation
    // Determine which card would be discarded
    const discardedCard = botPlayer.cards[swapPosition];

    // Calculate initial state after swap (hand size unchanged, but composition changes)
    const currentHandSize = botPlayer.cards.length;
    let currentKnownCards = botPlayer.knownCardPositions.length;
    let currentScore = this.calculateHandScore(botPlayer.cards);

    // After swap: we know the drawn card is now in our hand
    // We lose knowledge of the discarded position, gain knowledge of drawn card
    const isDiscardedPositionKnown =
      botPlayer.knownCardPositions.includes(swapPosition);

    if (isDiscardedPositionKnown) {
      // We're replacing a known card with the drawn card (still known)
      // No net change in known cards
    } else {
      // We're replacing an unknown card with a known card
      currentKnownCards += 1;
    }

    // Update score: remove discarded card value, add drawn card value
    currentScore = currentScore - discardedCard.value + drawnCard.value;

    // Stage 2: Toss-In Cascade Simulation
    const { handSize: postTossInHandSize, score: postTossInScore } =
      this.simulateTossInCascade(
        discardedCard.rank,
        currentHandSize,
        currentScore,
        context,
        tempMemory
      );

    // Stage 3: Action & Knowledge Gain Simulation
    const knowledgeGain = this.simulateActionKnowledgeGain(
      discardedCard,
      context,
      tempMemory
    );

    return {
      finalHandSize: postTossInHandSize,
      finalKnownCards: currentKnownCards + knowledgeGain,
      finalScore: postTossInScore,
    };
  }

  /**
   * Stage 2: Simulate the toss-in cascade effect
   * Predicts how many additional cards could be tossed in based on known matches
   */
  private simulateTossInCascade(
    discardedRank: Rank,
    currentHandSize: number,
    currentScore: number,
    context: BotDecisionContext,
    _tempMemory: BotMemory
  ): { handSize: number; score: number } {
    let tossInCount = 0;
    let scoreReduction = 0;

    // Check each known card in bot's hand for matching rank
    for (let pos = 0; pos < context.botPlayer.cards.length; pos++) {
      if (context.botPlayer.knownCardPositions.includes(pos)) {
        const card = context.botPlayer.cards[pos];

        if (card.rank === discardedRank) {
          tossInCount++;
          scoreReduction += card.value;
        }
      }
    }

    // Apply toss-in effect
    return {
      handSize: currentHandSize - tossInCount,
      score: currentScore - scoreReduction,
    };
  }

  /**
   * Stage 3: Simulate knowledge gain from card actions
   * This is the intelligence layer that predicts information gain
   */
  private simulateActionKnowledgeGain(
    discardedCard: Card,
    context: BotDecisionContext,
    _tempMemory: BotMemory
  ): number {
    const action = discardedCard.actionText;

    if (!action) {
      return 0; // No action = no knowledge gain
    }

    const rank = discardedCard.rank;

    // Peek Actions: Reliable knowledge gain
    // 7 and 8 actions are extremely valuable because they:
    // 1. Give guaranteed knowledge (no risk)
    // 2. Don't reduce hand size (preserve card advantage)
    // 3. Don't reveal information to opponents (unlike swapping which shows toss-ins)
    // 4. Enable better future decisions with asymmetric information advantage
    // Weight them MUCH higher than swapping with toss-in potential
    if (rank === '7' || rank === '8') {
      // 7 and 8 peek at 1 card - always gain knowledge if we have unknown cards
      const unknownCount = this.countUnknownCards(context.botPlayer);
      // Return 3 instead of 1 to account for:
      // - +1 for knowledge gain
      // - +1 for not reducing hand size
      // - +1 for not revealing information to opponents
      return unknownCount > 0 ? 3 : 0;
    }

    if (rank === 'Q') {
      // Q peeks at 2 cards - extremely valuable for the same reasons as 7/8
      // Plus it can optionally swap after peeking (making it even more powerful)
      const unknownCount = this.countUnknownCards(context.botPlayer);
      // Base knowledge gain (2 cards) + bonus for information advantage
      const baseKnowledge = Math.min(2, unknownCount);
      // Add bonus points for the strategic advantages
      return baseKnowledge > 0 ? baseKnowledge + 2 : 0;
    }

    // Swap Actions: Knowledge-gaining swap heuristic
    if (rank === 'J' || rank === '9' || rank === '10') {
      return this.simulateKnowledgeGainingSwap(context, _tempMemory);
    }

    // King (K): EXTREMELY valuable to discard via swap
    // - Removes card from hand when declared (like using an action)
    // - Triggers toss-in for BOTH King AND declared rank
    // - Can execute declared card's action
    // - Declaring another King creates massive cascade potential
    if (rank === 'K') {
      // Base value: equivalent to a strong peek action
      let kingValue = 4;

      // Bonus if bot has OTHER known Kings (enables King toss-in cascade)
      let otherKings = 0;
      for (let pos = 0; pos < context.botPlayer.cards.length; pos++) {
        if (!context.botPlayer.knownCardPositions.includes(pos)) continue;
        const card = context.botPlayer.cards[pos];
        if (card.rank === 'K') {
          otherKings++;
        }
      }
      kingValue += otherKings * 3; // Each King enables toss-in cascade

      // Bonus if bot has known action cards to declare
      let knownActionCards = 0;
      for (let pos = 0; pos < context.botPlayer.cards.length; pos++) {
        if (!context.botPlayer.knownCardPositions.includes(pos)) continue;
        const card = context.botPlayer.cards[pos];
        if (card.actionText && card.rank !== 'K') {
          knownActionCards++;
        }
      }
      kingValue += knownActionCards * 2; // Can declare and execute these actions

      return kingValue;
    }

    // Ace (A): Low value card (1 point), but force-draw action is situational
    // Swapping Ace into hand is often good (low points)
    // Using Ace action is only good when opponent is about to call Vinto
    // Give small bonus since it's a flexible card
    if (rank === 'A') {
      return 1;
    }

    return 0;
  }

  /**
   * Heuristic: Check if a knowledge-gaining swap is possible
   * Returns 1 if we can swap one of our unknown cards for an opponent's known low-value card
   */
  private simulateKnowledgeGainingSwap(
    context: BotDecisionContext,
    _tempMemory: BotMemory
  ): number {
    // Check if we have unknown cards in our hand
    const hasUnknownCards = this.countUnknownCards(context.botPlayer) > 0;

    if (!hasUnknownCards) {
      return 0; // Can't gain knowledge if we already know everything
    }

    // Check if we know any opponent cards (from previous peeks or actions)
    for (const [_opponentId, knownCards] of context.opponentKnowledge) {
      if (knownCards.size > 0) {
        // We know at least one opponent card - a strategic swap is possible
        return 1;
      }
    }

    return 0; // No strategic swap opportunity
  }

  /**
   * Count how many unknown cards the bot has
   */
  private countUnknownCards(botPlayer: PlayerState): number {
    return botPlayer.cards.length - botPlayer.knownCardPositions.length;
  }

  /**
   * Calculate the total value (score) of a hand
   */
  private calculateHandScore(cards: Card[]): number {
    return cards.reduce((sum, card) => sum + card.value, 0);
  }

  // ========== Outcome Scoring Heuristic ==========

  /**
   * Strategic priority weights (expert-defined hierarchy)
   */

  /**
   * Calculate a comparable score for a turn outcome based on strategic priorities
   * Higher score = better outcome
   */
  private calculateOutcomeScore(outcome: TurnOutcome): number {
    // Knowledge: more is better (positive contribution)
    const knowledgeScore = outcome.finalKnownCards * SWAP_KNOWLEDGE_WEIGHT;

    // Hand size: fewer is better (negative contribution becomes positive)
    const handSizeScore = -outcome.finalHandSize * SWAP_HAND_SIZE_WEIGHT;

    // Score: lower is better (negative contribution becomes positive)
    const scoreComponent = -outcome.finalScore * SWAP_SCORE_WEIGHT;

    return knowledgeScore + handSizeScore + scoreComponent;
  }

  shouldCallVinto(context: BotDecisionContext): boolean {
    this.initializeIfNeeded(context);

    // Never call Vinto too early
    if (context.gameState.turnNumber < context.allPlayers.length * 2) {
      return false;
    }

    // Step 1: Run MCTS to see if it suggests calling Vinto
    const gameState = this.constructGameState(context);
    const bestMove = this.runMCTS(gameState);

    if (bestMove.type !== 'call-vinto') {
      return false;
    }

    // Step 2: MCTS suggests calling Vinto - now validate with worst-case analysis
    const solver = new VintoRoundSolver(this.botId, this.botMemory);

    // Build opponent list
    const opponents = context.allPlayers
      .filter((p) => p.id !== this.botId)
      .map((p) => ({
        id: p.id,
        cardCount: p.cards.length,
      }));

    // Run worst-case validation
    const validation = solver.validateVintoCall(
      context.botPlayer.cards,
      opponents,
      context.discardPile
    );

    // Only call Vinto if:
    // 1. MCTS suggests it (already passed)
    // 2. VintoRoundSolver confirms it's safe
    // 3. Confidence is reasonable (> 40%)
    return validation.shouldCallVinto && validation.confidence > 0.4;
  }

  // ========== MCTS Core Algorithm ==========

  /**
   * Run MCTS and extract action plan if taking from discard
   * This ensures consistency: if bot decides to take discard based on using the action,
   * it commits to that plan instead of re-evaluating later
   */
  private runMCTSWithPlan(rootState: MCTSGameState): {
    move: MCTSMove;
    actionPlan?: BotActionDecision;
  } {
    const root = new MCTSNode(rootState, null, null);
    root.untriedMoves = this.generatePossibleMoves(rootState);

    // Early return if there are no possible moves (e.g., empty draw pile, no actionable discard)
    if (root.untriedMoves.length === 0) {
      console.log(`[MCTS] No possible moves available, returning pass`);
      return {
        move: {
          type: 'pass',
          playerId: this.botId,
        },
      };
    }

    const startTime = Date.now();
    let iterations = 0;

    // Run MCTS iterations until time limit or iteration limit
    while (
      iterations < this.config.iterations &&
      Date.now() - startTime < this.config.timeLimit
    ) {
      let node = this.select(root);

      if (!node.isTerminal && node.hasUntriedMoves()) {
        node = this.expand(node);
      }

      const reward = this.simulate(node.state);
      node.backpropagate(reward);

      iterations++;
    }

    console.log(
      `[MCTS] Completed ${iterations} iterations in ${Date.now() - startTime}ms`
    );

    // Select best move based on visit count (most robust)
    const bestChild = root.selectMostVisitedChild();

    if (!bestChild || !bestChild.move) {
      return {
        move: {
          type: 'pass',
          playerId: this.botId,
        },
      };
    }

    console.log(
      `[MCTS] Best move: ${bestChild.move.type} (visits: ${
        bestChild.visits
      }, reward: ${bestChild.getAverageReward().toFixed(3)})`
    );

    // Extract action plan for moves that will require action target selection:
    // 1. take-discard with action card
    // 2. King declaration of action card (use-action with declaredRank)
    // 3. Direct use-action with targets (J/Q/A from drawn cards)
    let actionPlan: BotActionDecision | undefined;

    if (
      bestChild.move.type === 'take-discard' &&
      bestChild.move.actionCard?.actionText
    ) {
      // Look ahead in the tree to find what action the bot plans to take
      actionPlan = this.extractActionPlan(bestChild);
    } else if (
      bestChild.move.type === 'use-action' &&
      bestChild.move.declaredRank
    ) {
      // King declaring an action card - check if it's an action rank
      const declaredAction = getCardAction(bestChild.move.declaredRank);
      if (declaredAction) {
        // This is declaring an action card (7-K) - extract the planned action
        actionPlan = this.extractActionPlan(bestChild);
        console.log(
          `[MCTS] King declaring action card ${bestChild.move.declaredRank}, extracted plan:`,
          actionPlan
        );
      }
    } else if (
      bestChild.move.type === 'use-action' &&
      bestChild.move.targets &&
      bestChild.move.targets.length > 0
    ) {
      // Direct use-action with targets (J/Q/9/10/A from drawn cards)
      // The move already has targets, use them directly
      actionPlan = {
        targets: bestChild.move.targets.map((t) => ({
          playerId: t.playerId,
          position: t.position,
        })),
        shouldSwap: bestChild.move.shouldSwap,
        declaredRank: bestChild.move.declaredRank,
      };
      console.log(
        `[MCTS] Direct use-action move with targets, caching plan:`,
        actionPlan
      );
    }

    return {
      move: bestChild.move,
      actionPlan,
    };
  }

  /**
   * Extract action plan from MCTS tree after take-discard decision
   * This looks at the best child nodes to see what the bot plans to do with the action
   */
  private extractActionPlan(
    takeDiscardNode: MCTSNode
  ): BotActionDecision | undefined {
    // The take-discard node's best child should represent using the action
    // (or discarding, but if we're here, MCTS likely wants to use it)
    const actionChild = takeDiscardNode.selectMostVisitedChild();

    if (!actionChild || !actionChild.move) {
      return undefined;
    }

    // If the move has targets already, use those
    if (actionChild.move.targets && actionChild.move.targets.length > 0) {
      return {
        targets: actionChild.move.targets.map((t) => ({
          playerId: t.playerId,
          position: t.position,
        })),
        shouldSwap: actionChild.move.shouldSwap,
        declaredRank: actionChild.move.declaredRank,
      };
    }

    return undefined;
  }

  /**
   * Main MCTS algorithm
   */
  private runMCTS(rootState: MCTSGameState): MCTSMove {
    const root = new MCTSNode(rootState, null, null);
    root.untriedMoves = this.generatePossibleMoves(rootState);

    // Early return if there are no possible moves (e.g., empty draw pile, no actionable discard)
    if (root.untriedMoves.length === 0) {
      console.log(`[MCTS] No possible moves available, returning pass`);
      return {
        type: 'pass',
        playerId: this.botId,
      };
    }

    const startTime = Date.now();
    let iterations = 0;

    // Run MCTS iterations until time limit or iteration limit
    while (
      iterations < this.config.iterations &&
      Date.now() - startTime < this.config.timeLimit
    ) {
      // 1. Selection: traverse tree using UCB1
      let node = this.select(root);

      // 2. Expansion: add new child if not terminal and not fully expanded
      if (!node.isTerminal && node.hasUntriedMoves()) {
        node = this.expand(node);
      }

      // 3. Simulation: random playout from current state
      const reward = this.simulate(node.state);

      // 4. Backpropagation: update all ancestors
      node.backpropagate(reward);

      iterations++;
    }

    console.log(
      `[MCTS] Completed ${iterations} iterations in ${Date.now() - startTime}ms`
    );

    // Select best move based on visit count (most robust)
    const bestChild = root.selectMostVisitedChild();

    if (bestChild && bestChild.move) {
      console.log(
        `[MCTS] Best move: ${bestChild.move.type} (visits: ${
          bestChild.visits
        }, reward: ${bestChild.getAverageReward().toFixed(3)})`
      );
      return bestChild.move;
    }

    // Fallback: return pass move
    return {
      type: 'pass',
      playerId: this.botId,
    };
  }

  /**
   * Selection phase - traverse tree using UCB1
   */
  private select(node: MCTSNode): MCTSNode {
    while (!node.isTerminal) {
      if (node.hasUntriedMoves()) {
        return node;
      }

      if (!node.isFullyExpanded) {
        return node;
      }

      // Select child with highest UCB1 score
      const child = node.selectBestChildUCB1(this.config.explorationConstant);
      if (!child) break;

      node = child;
    }

    return node;
  }

  /**
   * Expansion phase - add a new child node
   */
  private expand(node: MCTSNode): MCTSNode {
    const move = node.getRandomUntriedMove();
    if (!move) return node;

    const newState = this.applyMove(node.state, move);
    const child = new MCTSNode(newState, move, node);
    child.untriedMoves = this.generatePossibleMoves(newState);

    node.addChild(child);

    return child;
  }

  /**
   * Simulation phase - prioritized rollout with determinization
   *
   * Rollout Policy: Prioritized for Strategic Information & Control
   * 1. Game-Ending Moves (Highest Priority): call-vinto, winning toss-in
   * 2. Information-Gathering (High Priority): known-match toss-in, peek actions (7, 8, Q)
   * 3. Score Reduction (Medium Priority): swap high-value for low-value
   * 4. Defensive Moves (Medium-Low Priority): Ace against opponents close to winning
   * 5. Fallback (Lowest Priority): random move
   */
  private simulate(state: MCTSGameState): number {
    // Determinize: sample hidden information
    const deterministicState = this.determinize(state);

    // Deep copy the deterministic state to ensure independence
    let currentState = this.deepCopyGameState(deterministicState);
    let depth = 0;

    // Fast rollout with prioritized move selection
    while (!this.isTerminal(currentState) && depth < this.config.rolloutDepth) {
      const moves = this.generatePossibleMoves(currentState);
      if (moves.length === 0) break;

      // Select move using prioritized rollout policy
      const move = this.selectRolloutMove(currentState, moves);
      currentState = this.applyMove(currentState, move);

      depth++;
    }

    // Evaluate final state
    return this.evaluateState(currentState, this.botId);
  }

  /**
   * Select move during rollout using prioritized policy
   * This implements the strategic heuristics for faster convergence
   */
  private selectRolloutMove(state: MCTSGameState, moves: MCTSMove[]): MCTSMove {
    const currentPlayer = state.players[state.currentPlayerIndex];
    if (!currentPlayer || moves.length === 0) {
      return moves[0];
    }

    // Priority 1: Game-Ending Moves
    // Check for call-vinto that is likely to win
    const vintoMoves = moves.filter((m) => m.type === 'call-vinto');
    if (vintoMoves.length > 0) {
      const botScore = currentPlayer.score;
      const opponentScores = state.players
        .filter((p) => p.id !== currentPlayer.id)
        .map((p) => p.score);
      const avgOpponentScore =
        opponentScores.reduce((a, b) => a + b, 0) /
        (opponentScores.length || 1);

      // Call vinto if bot score is significantly lower (likely to win)
      if (botScore < avgOpponentScore - 5) {
        return vintoMoves[0];
      }
    }

    // Check for toss-in that results in zero cards (instant win)
    const tossInMoves = moves.filter((m) => m.type === 'toss-in');
    if (tossInMoves.length > 0 && currentPlayer.cardCount === 1) {
      // If we have 1 card and can toss it in, we win
      return tossInMoves[0];
    }

    // Priority 2: Information-Gathering (High Priority)
    // Known matching card toss-in
    if (tossInMoves.length > 0) {
      const discardRank = state.discardPileTop?.rank;
      if (discardRank) {
        for (const move of tossInMoves) {
          if (move.tossInPositions && move.tossInPositions.length > 0) {
            // Check if any of the positions contain high-confidence matching cards
            for (const position of move.tossInPositions) {
              const card = state.hiddenCards.get(
                `${currentPlayer.id}-${position}`
              );
              const memory = currentPlayer.knownCards.get(position);
              // If we know this card matches, prioritize the toss-in move
              if (
                memory &&
                memory.confidence > 0.5 &&
                card &&
                card.rank === discardRank
              ) {
                return move;
              }
            }
          }
        }
      }
    }

    // Peek actions (7, 8, Q) - 75% probability to select
    const peekMoves = moves.filter((m) => {
      if (m.type !== 'use-action') return false;
      const card = state.pendingCard;
      if (!card) return false;
      return card.rank === '7' || card.rank === '8' || card.rank === 'Q';
    });

    if (peekMoves.length > 0 && Math.random() < 0.75) {
      return peekMoves[Math.floor(Math.random() * peekMoves.length)];
    }

    // Priority 3: Score Reduction (Medium Priority)
    // Swap move that replaces known high-value card (>9) with known low-value card (<3)
    const swapMoves = moves.filter((m) => m.type === 'swap');
    if (swapMoves.length > 0) {
      for (const move of swapMoves) {
        if (move.swapPosition !== undefined) {
          const oldCard = state.hiddenCards.get(
            `${currentPlayer.id}-${move.swapPosition}`
          );
          const newCard = state.pendingCard;
          const memory = currentPlayer.knownCards.get(move.swapPosition);

          if (
            oldCard &&
            newCard &&
            memory &&
            memory.confidence > 0.5 &&
            oldCard.value > 9 &&
            newCard.value < 3
          ) {
            return move;
          }
        }
      }
    }

    // Priority 4: Defensive Moves (Medium-Low Priority)
    // Check if an opponent is close to winning (1 or 2 cards left)
    const opponentsCloseToWinning = state.players.filter(
      (p) => p.id !== currentPlayer.id && p.cardCount <= 2
    );

    if (opponentsCloseToWinning.length > 0) {
      // Look for Ace (force draw) action
      const aceMoves = moves.filter((m) => {
        if (m.type !== 'use-action') return false;
        const card = state.pendingCard;
        if (!card) return false;
        return card.rank === 'A';
      });

      if (aceMoves.length > 0) {
        // Target the opponent closest to winning
        const targetOpponent = opponentsCloseToWinning.reduce((closest, opp) =>
          opp.cardCount < closest.cardCount ? opp : closest
        );

        const targetedAceMove = aceMoves.find(
          (m) =>
            m.targets &&
            m.targets.length > 0 &&
            m.targets[0].playerId === targetOpponent.id
        );

        if (targetedAceMove) {
          return targetedAceMove;
        }
      }
    }

    // Priority 5: Fallback - Random move
    return moves[Math.floor(Math.random() * moves.length)];
  }

  /**
   * Deep copy a game state to prevent shared references
   */
  private deepCopyGameState(state: MCTSGameState): MCTSGameState {
    return copy(state);
  }

  // ========== Helper Methods ==========

  /**
   * Initialize bot memory if needed
   */
  private initializeIfNeeded(context: BotDecisionContext): void {
    if (this.botId !== context.botId) {
      this.botId = context.botId;
      this.botMemory = new BotMemory(context.botId, this.difficulty);
    }

    // Update memory with current context
    this.updateMemoryFromContext(context);
  }

  /**
   * Update bot memory from context (cards bot has seen)
   */
  private updateMemoryFromContext(context: BotDecisionContext): void {
    // Update from bot's own known cards
    context.botPlayer.cards.forEach((card, position) => {
      if (context.botPlayer.knownCardPositions.includes(position)) {
        const existing = this.botMemory.getCardMemory(this.botId, position);
        if (!existing || existing.card?.id !== card.id) {
          this.botMemory.observeCard(card, this.botId, position);
        }
      }
    });

    // Update from opponent knowledge
    context.opponentKnowledge.forEach((knownCards, opponentId) => {
      knownCards.forEach((card, position) => {
        const existing = this.botMemory.getCardMemory(opponentId, position);
        if (!existing || existing.card?.id !== card.id) {
          this.botMemory.observeCard(card, opponentId, position);
        }
      });
    });
  }

  /**
   * Construct MCTS game state from bot context
   */
  private constructGameState(context: BotDecisionContext): MCTSGameState {
    const players: MCTSPlayerState[] = context.allPlayers.map((p) => ({
      id: p.id,
      cardCount: p.cards.length,
      knownCards: this.botMemory.getPlayerMemory(p.id),
      score: this.estimatePlayerScore(p.id, context),
    }));

    // Determine if we're in toss-in phase based on game state
    const isTossInPhase =
      context.gameState.subPhase === 'toss_queue_active' &&
      !!context.gameState.activeTossIn;

    // STEP 2 FIX: Make discardPileTop aware of active toss-in
    // If we're in toss-in phase, use the toss-in rank as the discard top
    let simulationDiscardTop = context.discardTop || null;

    if (isTossInPhase && context.gameState.activeTossIn) {
      const tossInRank = context.gameState.activeTossIn.ranks[0];
      simulationDiscardTop = {
        id: `tossin-state-${tossInRank}`,
        rank: tossInRank,
        value: getCardValue(tossInRank),
        actionText: getCardShortDescription(tossInRank),
        played: true,
      };
    }

    return {
      players,
      currentPlayerIndex: context.allPlayers.findIndex(
        (p) => p.id === context.botId
      ),
      botPlayerId: context.botId,
      discardPileTop: simulationDiscardTop,
      discardPile: context.discardPile,
      deckSize: 54, // Standard deck with 2 Jokers
      botMemory: this.botMemory,
      hiddenCards: new Map(),
      pendingCard: context.pendingCard || null,
      isTossInPhase,
      tossInRanks:
        isTossInPhase && context.gameState.activeTossIn
          ? context.gameState.activeTossIn.ranks
          : undefined,
      turnCount: context.gameState.turnNumber,
      finalTurnTriggered: context.gameState.finalTurnTriggered,
      vintoCallerId: context.gameState.vintoCallerId || null,
      coalitionLeaderId: context.coalitionLeaderId || null,
      isTerminal: false,
      winner: null,
    };
  }

  /**
   * Estimate player score from known and unknown cards
   * Uses probabilistic information from botMemory to make more accurate estimates
   */
  private estimatePlayerScore(
    playerId: string,
    context: BotDecisionContext
  ): number {
    const player = context.allPlayers.find((p) => p.id === playerId);
    if (!player) return 50;

    const knownCards = this.botMemory.getPlayerMemory(playerId);
    let knownScore = 0;
    let unknownCount = 0;

    for (let i = 0; i < player.cards.length; i++) {
      const memory = knownCards.get(i);
      if (memory && memory.confidence > 0.5) {
        knownScore += memory.card!.value;
      } else {
        unknownCount++;
      }
    }

    // Calculate expected value of remaining cards based on distribution
    const averageRemainingValue = this.calculateAverageRemainingCardValue();

    // Estimate unknown cards using the calculated average from remaining cards
    const unknownScore = unknownCount * averageRemainingValue;

    return knownScore + unknownScore;
  }

  /**
   * Calculate the average value of remaining (unseen) cards
   * Uses the card distribution from botMemory to compute expected value
   */
  private calculateAverageRemainingCardValue(): number {
    const distribution = this.botMemory.getCardDistribution();

    let totalValue = 0;
    let totalCount = 0;

    for (const [rank, count] of distribution) {
      if (count > 0) {
        const value = getCardValue(rank);
        totalValue += value * count;
        totalCount += count;
      }
    }

    // If no cards remain in distribution, fall back to neutral average (6)
    if (totalCount === 0) {
      return 6;
    }

    return totalValue / totalCount;
  }

  /**
   * Determinize hidden information by sampling
   * Creates a consistent possible world for simulation
   */
  private determinize(state: MCTSGameState): MCTSGameState {
    // Use fast-copy for proper deep cloning to prevent state bleeding
    const newState = copy(state);
    newState.hiddenCards = new Map();

    // Build deck of available ranks (standard 52-card deck + 2 Jokers)
    const standardRanks: Rank[] = [
      'A',
      'A',
      'A',
      'A',
      '2',
      '2',
      '2',
      '2',
      '3',
      '3',
      '3',
      '3',
      '4',
      '4',
      '4',
      '4',
      '5',
      '5',
      '5',
      '5',
      '6',
      '6',
      '6',
      '6',
      '7',
      '7',
      '7',
      '7',
      '8',
      '8',
      '8',
      '8',
      '9',
      '9',
      '9',
      '9',
      '10',
      '10',
      '10',
      '10',
      'J',
      'J',
      'J',
      'J',
      'Q',
      'Q',
      'Q',
      'Q',
      'K',
      'K',
      'K',
      'K',
      'Joker',
      'Joker', // 2 Jokers
    ];

    // Build availableRanks pool from standardRanks
    const availableRanks: Rank[] = [...standardRanks];

    // Remove discarded cards from available pool (cards permanently out of play)
    for (const discardedCard of state.discardPile) {
      const idx = availableRanks.indexOf(discardedCard.rank);
      if (idx >= 0) {
        availableRanks.splice(idx, 1);
      }
    }

    // Remove cards we know about from available pool
    for (const player of state.players) {
      for (let pos = 0; pos < player.cardCount; pos++) {
        const memory = player.knownCards.get(pos);
        if (memory && memory.confidence > 0.5) {
          const knownRank = memory.card!.rank;
          const idx = availableRanks.indexOf(knownRank);
          if (idx >= 0) {
            availableRanks.splice(idx, 1);
          }
        }
      }
    }

    // Sample cards for each player position
    for (const player of state.players) {
      for (let pos = 0; pos < player.cardCount; pos++) {
        const memory = player.knownCards.get(pos);

        if (!memory || memory.confidence < 0.5) {
          // Unknown card - sample from distribution
          let sampledRank: Rank;

          if (availableRanks.length > 0) {
            // Sample from available ranks and remove to prevent duplicates
            const idx = Math.floor(Math.random() * availableRanks.length);
            sampledRank = availableRanks[idx];
            availableRanks.splice(idx, 1); // Remove sampled rank to maintain consistency
          } else {
            // Fallback: use bot memory distribution
            sampledRank = state.botMemory.sampleCardFromDistribution() || '6';
          }

          // Create sampled card
          const card: Card = {
            id: `${player.id}-${pos}-sampled`,
            rank: sampledRank,
            value: getCardValue(sampledRank),
            actionText: getCardShortDescription(sampledRank),
            played: false,
          };
          newState.hiddenCards.set(`${player.id}-${pos}`, card);
        } else {
          // Known card - use from memory
          newState.hiddenCards.set(`${player.id}-${pos}`, memory.card!);
        }
      }
    }

    return newState;
  }

  /**
   * Generate possible moves from current state
   */
  private generatePossibleMoves(state: MCTSGameState): MCTSMove[] {
    const moves = MCTSMoveGenerator.generateMoves(state);
    return MCTSMoveGenerator.pruneMoves(state, moves);
  }

  /**
   * Apply a move to the state and return new state
   */
  private applyMove(state: MCTSGameState, move: MCTSMove): MCTSGameState {
    return MCTSStateTransition.applyMove(state, move);
  }

  /**
   * CRITICAL IMPROVEMENT: Evaluate states by understanding POTENTIAL, not just current state
   *
   * Before: Only looked at current score and hand size
   * After: Evaluates:
   * 1. Toss-in potential (pairs, triples of matching ranks)
   * 2. Action card synergies (having King + action cards to declare)
   * 3. Information asymmetry (what we know vs what opponents know)
   * 4. Relative position (not absolute)
   */
  private evaluateState(state: MCTSGameState, botPlayerId: string): number {
    const botPlayer = state.players.find((p) => p.id === botPlayerId);
    if (!botPlayer) return 0;

    // Terminal state check
    if (state.isTerminal) {
      return state.winner === botPlayerId ? 1.0 : 0.0;
    }

    // Component 1: Toss-in Potential (30% weight) - NEW!
    const tossInScore = evaluateTossInPotential(state, botPlayer);

    // Component 2: Relative Position (25% weight)
    const positionScore = evaluateRelativePosition(state, botPlayer);

    // Component 3: Action Card Value (20% weight)
    const actionScore = evaluateActionCardValue(state, botPlayer);

    // Component 4: Information Advantage (15% weight)
    const infoScore = evaluateInformationAdvantage(state, botPlayer);

    // Component 5: Threat Level (10% weight)
    const threatScore = evaluateThreatLevel(state, botPlayer);

    const finalScore =
      tossInScore * 0.3 +
      positionScore * 0.25 +
      actionScore * 0.2 +
      infoScore * 0.15 +
      threatScore * 0.1;

    return Math.max(0, Math.min(1, finalScore));
  }
  /**
   * Get current bot decision context (used for coalition evaluation)
   */
  private currentContext?: BotDecisionContext;

  private getBotDecisionContext(): BotDecisionContext | undefined {
    return this.currentContext;
  }

  private setBotDecisionContext(context: BotDecisionContext): void {
    this.currentContext = context;
  }

  /**
   * Check if state is terminal
   */
  private isTerminal(state: MCTSGameState): boolean {
    return MCTSStateTransition.isTerminal(state);
  }
}

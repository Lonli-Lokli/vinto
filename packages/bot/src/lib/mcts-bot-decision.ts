// services/mcts-bot-decision.ts
import { BotMemory } from './bot-memory';
import { MCTSMoveGenerator } from './mcts-move-generator';
import { VintoRoundSolver } from './vinto-round-solver';
import {
  createCoalitionPlan,
  executeCoalitionStep,
  CoalitionAction,
} from './coalition-round-solver';
import { StateConstructor } from './state-constructor';
import { SwapPositionSelector } from './swap-position-selector';
import { MCTSEngine } from './mcts-engine';

import {
  Card,
  Difficulty,
  getCardAction,
  getCardValue,
  Rank,
} from '@vinto/shapes';
import {
  MCTSConfig,
  MCTS_DIFFICULTY_CONFIGS,
  MCTSGameState,
  MCTSMove,
} from './mcts-types';
import {
  BotDecisionService,
  BotActionDecision,
  BotDecisionContext,
  BotTurnDecision,
} from './shapes';

/**
 * MCTS-based Bot Decision Service
 * Uses Monte Carlo Tree Search with imperfect information (determinization)
 */
export class MCTSBotDecisionService implements BotDecisionService {
  private botMemory: BotMemory;
  private config: MCTSConfig;
  private botId: string;
  private mctsEngine: MCTSEngine;
  // Map of player ID -> cached action plan (for coalition scenarios where leader plans for multiple members)
  private cachedActionPlans = new Map<string, BotActionDecision>();
  // Cached champion ID for coalition mode - set once at start of final round
  private coalitionChampionId: string | null = null;

  constructor(private difficulty: Difficulty) {
    this.botId = ''; // Will be set in first call
    this.config = MCTS_DIFFICULTY_CONFIGS[difficulty];
    this.botMemory = new BotMemory('', difficulty); // TODO, will be recreated
    this.mctsEngine = new MCTSEngine(this.config);
  }

  // ========== BotDecisionService Interface Implementation ==========

  decideTurnAction(context: BotDecisionContext): BotTurnDecision {
    this.initializeIfNeeded(context);

    // ROUTING: Check if we're in coalition mode (final phase)
    const inCoalitionMode =
      context.phase === 'final' &&
      context.coalitionLeaderId !== null &&
      context.botPlayer.id !== context.vintoCallerId;

    // Clear champion cache if no longer in coalition mode
    if (!inCoalitionMode && this.coalitionChampionId) {
      console.log(`[Coalition] Clearing champion cache - coalition ended`);
      this.coalitionChampionId = null;
    }

    if (inCoalitionMode) {
      console.log(`[Coalition] ${this.botId} using coalition solver`);
      return this.decideCoalitionTurnAction(context);
    }

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
      const hasUnknownCards =
        context.botPlayer.cards.length -
          context.botPlayer.knownCardPositions.length >
        0;

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

    const gameState = StateConstructor.constructGameState(
      context,
      this.botMemory,
      this.botId
    );
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

  /**
   * Coalition decision making - uses plan-based strategy instead of MCTS
   */
  private decideCoalitionTurnAction(
    context: BotDecisionContext
  ): BotTurnDecision {
    // Build knowledge map from bot memory
    const knownCards = new Map<string, Card>();

    // Add bot's known cards
    context.botPlayer.cards.forEach((card, pos) => {
      if (context.botPlayer.knownCardPositions.includes(pos)) {
        knownCards.set(`${context.botId}[${pos}]`, card);
      }
    });

    // Add opponent known cards from memory
    context.opponentKnowledge.forEach((posMap, playerId) => {
      posMap.forEach((card, pos) => {
        knownCards.set(`${playerId}[${pos}]`, card);
      });
    });

    // Create comprehensive plan using ALL possible move sequences
    // IMPORTANT: Pass cached champion ID to ensure consistency across all turns
    const plan = createCoalitionPlan(
      context.gameState,
      knownCards,
      undefined,
      this.coalitionChampionId
    );

    // Cache the champion ID on first call so it stays consistent throughout final round
    if (!this.coalitionChampionId) {
      this.coalitionChampionId = plan.championId;
      console.log(
        `[Coalition] Champion locked in: ${this.coalitionChampionId}`
      );
    }

    console.log(
      `[Coalition] Champion: ${plan.championId}, target: ${
        plan.targetScore
      }, confidence: ${(plan.confidence * 100).toFixed(0)}%`
    );
    console.log(`[Coalition] Generated ${plan.steps.length} action plan steps`);

    // Cache ALL actions from the coalition plan for all players
    // This ensures each player uses their planned action instead of running MCTS
    for (const step of plan.steps) {
      const actionDecision = this.convertCoalitionActionToDecision(step);
      if (actionDecision) {
        this.cachedActionPlans.set(step.playerId, actionDecision);
        console.log(
          `[Coalition] Cached action for ${step.playerId}: ${step.description}`
        );
      }
    }

    // Execute plan for current player
    // IMPORTANT: Use the actual current player from game state, not the bot making the decision
    // (since leader makes decisions for all coalition members)
    const currentPlayerId =
      context.gameState.players[context.gameState.currentPlayerIndex].id;
    
    const result = executeCoalitionStep(
      context.gameState,
      plan,
      currentPlayerId,
      undefined
    );

    if (result) {
      console.log(
        `[Coalition] ${context.botId} executing: ${result.action.description}`
      );

      // Handle specific coalition actions that require special execution
      if (result.action.actionType === 'discard') {
        // Discard drawn card action
        return { action: 'draw' };
      } else if (result.action.actionType === 'declare-king') {
        // King declaration action - King is in hand, so draw a card first
        // Then the King action will be executed in choosing/selecting phase
        console.log(
          `[Coalition] Executing King declaration: ${result.action.declaredRank}`
        );
        return {
          action: 'draw',
          actionDecision: {
            targets: [{ playerId: currentPlayerId, position: result.action.cardIndex }],
            declaredRank: result.action.declaredRank,
          },
        };
      } else if (
        result.action.actionType === 'swap-jack' ||
        result.action.actionType === 'peek-swap-queen' ||
        result.action.actionType === 'peek-own' ||
        result.action.actionType === 'peek-opponent'
      ) {
        // Action card usage from hand - draw a card, then use the action in choosing phase
        // The action plan will be cached and used when the bot decides what to do with drawn card
        console.log(
          `[Coalition] Will use action card from hand: ${result.action.actionType}`
        );
        const actionDecision = this.convertCoalitionActionToDecision(
          result.action
        );
        return {
          action: 'draw',
          actionDecision: actionDecision || undefined,
        };
      }
    } else {
      console.log(
        `[Coalition] ${context.botId} not in plan, using standard MCTS`
      );
    }

    // Execute via MCTS
    const gameState = StateConstructor.constructGameState(
      context,
      this.botMemory,
      this.botId
    );
    const mctsResult = this.runMCTSWithPlan(gameState);

    if (mctsResult.move.type === 'take-discard') {
      if (mctsResult.actionPlan) {
        return {
          action: 'take-discard',
          actionDecision: mctsResult.actionPlan,
        };
      }
      return { action: 'take-discard' };
    }

    return { action: 'draw' };
  }

  /**
   * Convert coalition action to bot action decision format
   */
  private convertCoalitionActionToDecision(
    action: CoalitionAction
  ): BotActionDecision | null {
    // For actions from hand (with drawnCardSwapWith), we need to include
    // the swap position as the first target
    if (action.drawnCardSwapWith !== undefined) {
      const swapTarget = {
        playerId: action.playerId,
        position: action.drawnCardSwapWith,
      };

      // If there are additional action targets (for Jack/Queen/etc), include them
      const actionTargets = action.targets
        ? action.targets.map((t) => ({
            playerId: t.playerId,
            position: t.cardIndex,
          }))
        : [];

      return {
        targets: [swapTarget, ...actionTargets],
        shouldSwap: action.shouldSwap,
        declaredRank: action.declaredRank,
      };
    }

    // For actions without swap (direct from drawn card or discard)
    if (!action.targets || action.targets.length === 0) {
      return null;
    }

    return {
      targets: action.targets.map((t) => ({
        playerId: t.playerId,
        position: t.cardIndex,
      })),
      shouldSwap: action.shouldSwap,
      declaredRank: action.declaredRank,
    };
  }

  shouldUseAction(drawnCard: Card, context: BotDecisionContext): boolean {
    this.initializeIfNeeded(context);

    if (!drawnCard.actionText || drawnCard.played) return false;

    // HEURISTIC: Always use 7 or 8 peek actions if bot has unknown cards
    // These are STRICTLY better than swapping because:
    // - Guaranteed knowledge gain
    // - No hand size increase (unlike swapping which keeps the card)
    // - No score increase
    const hasUnknownCards =
      context.botPlayer.cards.length -
        context.botPlayer.knownCardPositions.length >
      0;
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

    // For other action cards (J, Q, 9, 10, K), verify action can be executed before committing
    // CRITICAL FIX: Check if action has valid targets before deciding to use it
    const gameState = StateConstructor.constructGameState(
      context,
      this.botMemory,
      this.botId
    );

    // First check if there are any valid action moves
    const actionType = getCardAction(drawnCard.rank);
    if (!actionType) {
      console.log(
        `[MCTS] No action type found for ${drawnCard.rank}, swapping instead`
      );
      this.cachedActionPlans.delete(context.botId);
      return false;
    }

    const validActionMoves = MCTSMoveGenerator.generateActionMoves(
      gameState,
      actionType
    );

    if (validActionMoves.length === 0) {
      console.log(
        `[MCTS] No valid action moves for ${drawnCard.rank}, swapping instead`
      );
      this.cachedActionPlans.delete(context.botId);
      return false;
    }

    // Valid action moves exist, now run MCTS to decide whether to use action or swap
    const result = this.runMCTSWithPlan(gameState);

    if (result.move.type === 'use-action') {
      // Cache the action plan for Jack/Queen/9/10/K actions
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

    const gameState = StateConstructor.constructGameState(
      context,
      this.botMemory,
      this.botId
    );
    const bestMove = this.mctsEngine.runMCTS(gameState);

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

    const gameState = StateConstructor.constructGameState(
      context,
      this.botMemory,
      this.botId
    );
    const bestMove = this.mctsEngine.runMCTS(gameState);

    return bestMove.type === 'swap' && bestMove.shouldSwap === true;
  }

  selectKingDeclaration(context: BotDecisionContext): Rank {
    this.initializeIfNeeded(context);

    const gameState = StateConstructor.constructGameState(
      context,
      this.botMemory,
      this.botId
    );
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

    return SwapPositionSelector.selectBestSwapPosition(
      drawnCard,
      context,
      this.botId,
      this.difficulty
    );
  }

  shouldCallVinto(context: BotDecisionContext): boolean {
    this.initializeIfNeeded(context);

    // Never call Vinto too early
    if (context.gameState.turnNumber < context.allPlayers.length * 2) {
      return false;
    }

    // Step 1: Run MCTS to see if it suggests calling Vinto
    const gameState = StateConstructor.constructGameState(
      context,
      this.botMemory,
      this.botId
    );
    const bestMove = this.mctsEngine.runMCTS(gameState);

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

  // ========== MCTS Coordination ==========

  /**
   * Run MCTS and extract action plan if taking from discard
   */
  private runMCTSWithPlan(rootState: MCTSGameState): {
    move: MCTSMove;
    actionPlan?: BotActionDecision;
  } {
    // Run MCTS search
    const bestMove = this.mctsEngine.runMCTS(rootState);

    // Extract action plan if the move requires target selection
    const actionPlan = this.extractActionPlanFromMove(bestMove);

    return {
      move: bestMove,
      actionPlan,
    };
  }

  /**
   * Extract action plan from move if it contains target information
   */
  private extractActionPlanFromMove(
    move: MCTSMove
  ): BotActionDecision | undefined {
    // Check if move has targets that need to be cached
    if (move.targets && move.targets.length > 0) {
      return {
        targets: move.targets.map(
          (t: { playerId: string; position: number }) => ({
            playerId: t.playerId,
            position: t.position,
          })
        ),
        shouldSwap: move.shouldSwap,
        declaredRank: move.declaredRank,
      };
    }

    return undefined;
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
    console.log(
      `[Memory Update] ${this.botId} has ${context.botPlayer.knownCardPositions.length} known card positions:`,
      context.botPlayer.knownCardPositions
    );

    // Update from bot's own known cards
    context.botPlayer.cards.forEach((card, position) => {
      if (context.botPlayer.knownCardPositions.includes(position)) {
        const existing = this.botMemory.getCardMemory(this.botId, position);
        if (!existing || existing.card?.id !== card.id) {
          console.log(
            `[Memory Update] ${this.botId} observing own card at position ${position}: ${card.rank}`
          );
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
}

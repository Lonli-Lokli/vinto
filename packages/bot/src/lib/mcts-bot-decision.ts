// services/mcts-bot-decision.ts
import copy from 'fast-copy';
import { BotMemory } from './bot-memory';
import { MCTSMoveGenerator } from './mcts-move-generator';
import { MCTSStateTransition } from './mcts-state-transition';
import { VintoRoundSolver } from './vinto-round-solver';

import {
  Card,
  Difficulty,
  getCardAction,
  getCardShortDescription,
  getCardValue,
  Rank,
} from '@vinto/shapes';
import {
  MCTSConfig,
  MCTS_DIFFICULTY_CONFIGS,
  MCTSGameState,
  MCTSMove,
  MCTSNode,
} from './mcts-types';
import {
  BotDecisionService,
  BotActionDecision,
  BotDecisionContext,
  BotTurnDecision,
} from './shapes';

// Import extracted pure functions
import {
  shouldAlwaysTakeDiscardPeekCard,
  shouldAlwaysUsePeekAction,
  shouldUseAceAction,
  shouldParticipateInTossIn,
} from './mcts-bot-heuristics';
import {
  simulateDiscardOutcome,
  simulateTurnOutcome,
  calculateOutcomeScore,
  calculateStrategicOutcomeScore,
} from './mcts-outcome-simulator';
import { determinize } from './mcts-determinization';
import { extractActionPlan } from './mcts-action-planning';
import { estimatePlayerScore } from './mcts-score-estimator';
import { evaluateState } from './mcts-state-evaluator';
import { selectRolloutMove } from './mcts-rollout-policy';

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

    console.log(
      `[MCTS] ${this.botId} deciding turn action (${this.difficulty})`
    );

    // HEURISTIC: Always take peek cards from discard if bot has unknown cards
    if (
      shouldAlwaysTakeDiscardPeekCard(
        context.discardTop ?? null,
        context.botPlayer
      )
    ) {
      console.log(
        `[MCTS] Heuristic: Always taking ${
          context.discardTop!.rank
        } from discard (has unknown cards)`
      );
      return { action: 'take-discard' };
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

    if (!drawnCard.actionText || drawnCard.played) return false;

    // HEURISTIC: Always use peek actions if bot has unknown cards
    if (shouldAlwaysUsePeekAction(drawnCard, context.botPlayer)) {
      console.log(
        `[MCTS] Heuristic: Always using ${drawnCard.rank} peek action (has unknown cards)`
      );
      return true;
    }

    // HEURISTIC: For Ace, compare action value vs swap value
    if (drawnCard.rank === 'A') {
      const useAction = shouldUseAceAction(
        context.botPlayer,
        context.allPlayers,
        context.botId
      );

      if (!useAction) {
        console.log(`[MCTS] Heuristic: Swapping Ace instead of using action`);
        this.cachedActionPlans.delete(context.botId);
      } else {
        console.log(
          `[MCTS] Heuristic: Using Ace action (defensive - opponent near Vinto)`
        );
        this.cachedActionPlans.delete(context.botId);
      }

      return useAction;
    }

    // For other action cards, verify action can be executed before committing
    const gameState = this.constructGameState(context);

    // Check if there are any valid action moves
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

    // Valid action moves exist, run MCTS to decide
    const result = this.runMCTSWithPlan(gameState);

    if (result.move.type === 'use-action') {
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
   */
  shouldParticipateInTossIn(
    discardedRanks: [Rank, ...Rank[]],
    context: BotDecisionContext
  ): boolean {
    this.initializeIfNeeded(context);
    return shouldParticipateInTossIn(discardedRanks, context.botPlayer);
  }

  selectBestSwapPosition(
    drawnCard: Card,
    context: BotDecisionContext
  ): number | null {
    this.initializeIfNeeded(context);

    // Temporarily add drawn card to memory for accurate simulation
    const tempMemory = new BotMemory(this.botId, this.difficulty);

    // Copy current memory state
    context.botPlayer.cards.forEach((card, position) => {
      if (context.botPlayer.knownCardPositions.includes(position)) {
        tempMemory.observeCard(card, this.botId, position);
      }
    });

    // Add drawn card as temporary knowledge
    tempMemory.observeCard(drawnCard, this.botId, -1);

    let bestPosition = 0;
    let bestScore = -Infinity;
    let shouldDiscard = false;

    // Evaluate discarding the drawn card
    const discardOutcome = simulateDiscardOutcome(
      drawnCard,
      context.botPlayer,
      context,
      tempMemory
    );
    const discardScore = calculateOutcomeScore(discardOutcome);

    console.log(
      `[SwapSelector] Discard option: handSize=${discardOutcome.finalHandSize}, ` +
        `known=${discardOutcome.finalKnownCards}/${context.botPlayer.cards.length}, ` +
        `score=${discardOutcome.finalScore.toFixed(
          1
        )}, outcomeScore=${discardScore.toFixed(1)}`
    );

    bestScore = discardScore;
    shouldDiscard = true;

    // Evaluate each possible swap position
    for (
      let position = 0;
      position < context.botPlayer.cards.length;
      position++
    ) {
      const outcome = simulateTurnOutcome(
        drawnCard,
        position,
        context.botPlayer,
        context,
        tempMemory
      );

      const swappedOutCard = context.botPlayer.cards[position];
      const outcomeScore = calculateStrategicOutcomeScore(
        outcome,
        drawnCard,
        swappedOutCard
      );

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
        shouldDiscard = false;
      }
    }

    if (shouldDiscard) {
      console.log(
        `[SwapSelector] Selected DISCARD with score ${bestScore.toFixed(1)}`
      );
      return null;
    }

    console.log(
      `[SwapSelector] Selected position ${bestPosition} with score ${bestScore.toFixed(
        1
      )}`
    );

    return bestPosition;
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
      actionPlan = extractActionPlan(bestChild);
    } else if (
      bestChild.move.type === 'use-action' &&
      bestChild.move.declaredRank
    ) {
      // King declaring an action card - check if it's an action rank
      const declaredAction = getCardAction(bestChild.move.declaredRank);
      if (declaredAction) {
        // This is declaring an action card (7-K) - extract the planned action
        actionPlan = extractActionPlan(bestChild);
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
   */
  private simulate(state: MCTSGameState): number {
    // Determinize: sample hidden information
    const deterministicState = determinize(state);

    // Deep copy the deterministic state to ensure independence
    let currentState = this.deepCopyGameState(deterministicState);
    let depth = 0;

    // Fast rollout with prioritized move selection
    while (!this.isTerminal(currentState) && depth < this.config.rolloutDepth) {
      const moves = this.generatePossibleMoves(currentState);
      if (moves.length === 0) break;

      // Select move using prioritized rollout policy
      const move = selectRolloutMove(currentState, moves);
      currentState = this.applyMove(currentState, move);

      depth++;
    }

    // Evaluate final state
    return evaluateState(currentState, this.botId);
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

  /**
   * Construct MCTS game state from bot context
   */
  private constructGameState(context: BotDecisionContext): MCTSGameState {
    const players = context.allPlayers.map((p) => {
      const playerMemory = this.botMemory.getPlayerMemory(p.id);
      console.log(
        `[GameState] Player ${p.id} memory: ${playerMemory.size} cards known`
      );
      return {
        id: p.id,
        cardCount: p.cards.length,
        knownCards: playerMemory,
        score: estimatePlayerScore(p, this.botMemory, p.id),
      };
    });

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
      // --- THIS IS THE CORRESPONDING FIX IN THE BOT ---
      // It prioritizes the active card from the context, falling back to the
      // pending card. This makes the simulation state correct in all scenarios.
      pendingCard: context.activeActionCard || context.pendingCard || null,
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
   * Check if state is terminal
   */
  private isTerminal(state: MCTSGameState): boolean {
    return MCTSStateTransition.isTerminal(state);
  }
}

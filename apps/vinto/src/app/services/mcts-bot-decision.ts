// services/mcts-bot-decision.ts
import { Card, Rank, Difficulty } from '../shapes';
import {
  BotDecisionService,
  BotDecisionContext,
  BotTurnDecision,
  BotActionDecision,
} from './bot-decision';
import { BotMemory } from './bot-memory';
import {
  MCTSNode,
  MCTSGameState,
  MCTSMove,
  MCTSPlayerState,
  MCTSConfig,
  MCTS_DIFFICULTY_CONFIGS,
} from './mcts-types';

/**
 * MCTS-based Bot Decision Service
 * Uses Monte Carlo Tree Search with imperfect information (determinization)
 */
export class MCTSBotDecisionService implements BotDecisionService {
  private botMemory: BotMemory;
  private config: MCTSConfig;
  private botId: string;

  constructor(private difficulty: Difficulty) {
    this.botId = ''; // Will be set in first call
    this.config = MCTS_DIFFICULTY_CONFIGS[difficulty];
    this.botMemory = new BotMemory('', difficulty); // Temp, will be recreated
  }

  // ========== BotDecisionService Interface Implementation ==========

  decideTurnAction(context: BotDecisionContext): BotTurnDecision {
    this.initializeIfNeeded(context);

    console.log(
      `[MCTS] ${this.botId} deciding turn action (${this.difficulty})`
    );

    const gameState = this.constructGameState(context);
    const bestMove = this.runMCTS(gameState);

    if (bestMove.type === 'take-discard') {
      return { action: 'take-discard' };
    }

    return { action: 'draw' };
  }

  shouldUseAction(drawnCard: Card, context: BotDecisionContext): boolean {
    this.initializeIfNeeded(context);

    if (!drawnCard.action) return false;

    console.log(
      `[MCTS] ${this.botId} deciding whether to use ${drawnCard.rank} action`
    );

    const gameState = this.constructGameState(context);
    const bestMove = this.runMCTS(gameState);

    return bestMove.type === 'use-action';
  }

  selectActionTargets(context: BotDecisionContext): BotActionDecision {
    this.initializeIfNeeded(context);

    console.log(`[MCTS] ${this.botId} selecting action targets`);

    const gameState = this.constructGameState(context);
    const bestMove = this.runMCTS(gameState);

    if (bestMove.targets) {
      return {
        targets: bestMove.targets.map((t) => ({
          playerId: t.playerId,
          position: t.position,
        })),
        shouldSwap: bestMove.type === 'swap',
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

    console.log(`[MCTS] ${this.botId} deciding whether to swap after peek`);

    const gameState = this.constructGameState(context);
    const bestMove = this.runMCTS(gameState);

    return bestMove.type === 'swap' && bestMove.shouldSwap === true;
  }

  selectKingDeclaration(context: BotDecisionContext): Rank {
    this.initializeIfNeeded(context);

    console.log(`[MCTS] ${this.botId} selecting King declaration`);

    const gameState = this.constructGameState(context);
    const bestMove = this.runMCTS(gameState);

    if (bestMove.declaredRank) {
      return bestMove.declaredRank;
    }

    // Fallback to Q (most powerful)
    return 'Q';
  }

  shouldParticipateInTossIn(
    discardedRank: Rank,
    context: BotDecisionContext
  ): boolean {
    this.initializeIfNeeded(context);

    console.log(`[MCTS] ${this.botId} deciding toss-in for ${discardedRank}`);

    const gameState = this.constructGameState(context);
    const bestMove = this.runMCTS(gameState);

    return bestMove.type === 'toss-in';
  }

  selectBestSwapPosition(
    drawnCard: Card,
    context: BotDecisionContext
  ): number | null {
    this.initializeIfNeeded(context);

    console.log(
      `[MCTS] ${this.botId} selecting swap position for ${drawnCard.rank}`
    );

    const gameState = this.constructGameState(context);
    const bestMove = this.runMCTS(gameState);

    if (bestMove.type === 'swap' && bestMove.swapPosition !== undefined) {
      return bestMove.swapPosition;
    }

    return null;
  }

  shouldCallVinto(context: BotDecisionContext): boolean {
    this.initializeIfNeeded(context);

    // Never call Vinto too early
    if (context.gameState.turnCount < context.allPlayers.length * 2) {
      return false;
    }

    console.log(`[MCTS] ${this.botId} deciding whether to call Vinto`);

    const gameState = this.constructGameState(context);
    const bestMove = this.runMCTS(gameState);

    return bestMove.type === 'call-vinto';
  }

  // ========== MCTS Core Algorithm ==========

  /**
   * Main MCTS algorithm
   */
  private runMCTS(rootState: MCTSGameState): MCTSMove {
    const root = new MCTSNode(rootState, null, null);
    root.untriedMoves = this.generatePossibleMoves(rootState);

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
   * Simulation phase - random playout with determinization
   */
  private simulate(state: MCTSGameState): number {
    // Determinize: sample hidden information
    const deterministicState = this.determinize(state);

    let currentState = { ...deterministicState };
    let depth = 0;

    // Fast rollout
    while (!this.isTerminal(currentState) && depth < this.config.rolloutDepth) {
      const moves = this.generatePossibleMoves(currentState);
      if (moves.length === 0) break;

      // Select random move
      const move = moves[Math.floor(Math.random() * moves.length)];
      currentState = this.applyMove(currentState, move);

      depth++;
    }

    // Evaluate final state
    return this.evaluateState(currentState);
  }

  // ========== Helper Methods ==========

  /**
   * Initialize bot memory if needed
   */
  private initializeIfNeeded(context: BotDecisionContext): void {
    if (this.botId !== context.botId) {
      this.botId = context.botId;
      this.botMemory = new BotMemory(context.botId, this.difficulty);
      console.log(
        `[MCTS] Initialized memory for ${this.botId} (${this.difficulty})`
      );
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
      if (context.botPlayer.knownCardPositions.has(position)) {
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

    return {
      players,
      currentPlayerIndex: context.allPlayers.findIndex(
        (p) => p.id === context.botId
      ),
      botPlayerId: context.botId,
      discardPileTop: context.discardTop || null,
      deckSize: 52, // Approximate
      botMemory: this.botMemory,
      hiddenCards: new Map(),
      pendingCard: context.pendingCard || null,
      isTossInPhase: false,
      turnCount: context.gameState.turnCount,
      finalTurnTriggered: context.gameState.finalTurnTriggered,
      isTerminal: false,
      winner: null,
    };
  }

  /**
   * Estimate player score from known and unknown cards
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

    // Estimate unknown cards as average value (6)
    const unknownScore = unknownCount * 6;

    return knownScore + unknownScore;
  }

  /**
   * Determinize hidden information by sampling
   * Creates a consistent possible world for simulation
   */
  private determinize(state: MCTSGameState): MCTSGameState {
    const newState = { ...state };
    newState.hiddenCards = new Map();

    // Track which ranks have been sampled to maintain consistency
    const availableRanks: Rank[] = [];

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

    // If we removed too many, start fresh
    if (availableRanks.length < 10) {
      availableRanks.push(...standardRanks);
    }

    // Sample cards for each player position
    for (const player of state.players) {
      for (let pos = 0; pos < player.cardCount; pos++) {
        const memory = player.knownCards.get(pos);

        if (!memory || memory.confidence < 0.5) {
          // Unknown card - sample from distribution
          let sampledRank: Rank;

          if (availableRanks.length > 0) {
            // Sample from available ranks
            const idx = Math.floor(Math.random() * availableRanks.length);
            sampledRank = availableRanks[idx];
            // Don't remove to allow duplicates in simulation
          } else {
            // Fallback: use bot memory distribution
            sampledRank = state.botMemory.sampleCardFromDistribution() || '6';
          }

          // Create sampled card
          const card: Card = {
            id: `${player.id}-${pos}-sampled`,
            rank: sampledRank,
            value: this.getRankValue(sampledRank),
            action: this.getRankAction(sampledRank),
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
    const { MCTSMoveGenerator } = require('./mcts-move-generator');
    const moves = MCTSMoveGenerator.generateMoves(state);
    return MCTSMoveGenerator.pruneMoves(state, moves);
  }

  /**
   * Apply a move to the state and return new state
   */
  private applyMove(state: MCTSGameState, move: MCTSMove): MCTSGameState {
    const { MCTSStateTransition } = require('./mcts-state-transition');
    return MCTSStateTransition.applyMove(state, move);
  }

  /**
   * Evaluate state from bot's perspective (0-1, higher is better)
   */
  private evaluateState(state: MCTSGameState): number {
    const botPlayer = state.players.find((p) => p.id === state.botPlayerId);
    if (!botPlayer) return 0;

    // 1. Score component - lower bot score is better
    const botScore = botPlayer.score;
    const opponentScores = state.players
      .filter((p) => p.id !== state.botPlayerId)
      .map((p) => p.score);

    const avgOpponentScore =
      opponentScores.reduce((a, b) => a + b, 0) / (opponentScores.length || 1);
    const minOpponentScore = Math.min(...opponentScores);

    // Score advantage vs average (positive is good)
    const scoreAdvantage = avgOpponentScore - botScore;

    // Score advantage vs best opponent (for competitive positioning)
    const competitiveAdvantage = minOpponentScore - botScore;

    // 2. Card count component - fewer cards is better
    const botCardCount = botPlayer.cardCount;
    const avgOpponentCardCount =
      opponentScores.length > 0
        ? state.players
            .filter((p) => p.id !== state.botPlayerId)
            .reduce((sum, p) => sum + p.cardCount, 0) / opponentScores.length
        : 4;

    const cardCountAdvantage = avgOpponentCardCount - botCardCount;

    // 3. Knowledge component - more known cards is better
    const botKnownCards = botPlayer.knownCards.size;
    const botKnowledgeRatio =
      botCardCount > 0 ? botKnownCards / botCardCount : 0;

    // Calculate opponent knowledge (what bot knows about opponents)
    let totalOpponentCardsKnown = 0;
    let totalOpponentCards = 0;
    for (const opponent of state.players) {
      if (opponent.id === state.botPlayerId) continue;
      totalOpponentCardsKnown += opponent.knownCards.size;
      totalOpponentCards += opponent.cardCount;
    }

    const opponentKnowledgeRatio =
      totalOpponentCards > 0 ? totalOpponentCardsKnown / totalOpponentCards : 0;

    // Information advantage = knowing own cards + knowing opponent cards
    const knowledgeAdvantage = (botKnowledgeRatio + opponentKnowledgeRatio) * 3;

    // 4. Action card value - having action cards is good
    let actionCardCount = 0;
    for (let pos = 0; pos < botCardCount; pos++) {
      const card = state.hiddenCards.get(`${state.botPlayerId}-${pos}`);
      if (card && card.action) {
        actionCardCount++;
      }
    }
    const actionCardBonus = actionCardCount * 2;

    // 5. High value card detection - knowing you have high cards is bad
    let knownHighCardPenalty = 0;
    for (let pos = 0; pos < botCardCount; pos++) {
      const memory = botPlayer.knownCards.get(pos);
      if (memory && memory.card && memory.card.value > 8) {
        knownHighCardPenalty += (memory.card.value - 6) * 0.5;
      }
    }

    // 6. Game phase component
    const isEarlyGame = state.turnCount < state.players.length * 2;
    const isLateGame = state.finalTurnTriggered || state.turnCount > 40;

    let phaseMultiplier = 1.0;
    if (isEarlyGame) {
      phaseMultiplier = 0.7; // Early game: prioritize information gathering
    } else if (isLateGame) {
      phaseMultiplier = 1.5; // Late game: score matters most
    }

    // 7. Terminal state bonus
    let terminalBonus = 0;
    if (state.isTerminal) {
      if (state.winner === state.botPlayerId) {
        terminalBonus = 100; // Huge bonus for winning
      } else {
        terminalBonus = -100; // Huge penalty for losing
      }
    }

    // 8. Position relative to calling Vinto
    let vintoReadinessBonus = 0;
    if (isLateGame && botScore < avgOpponentScore - 3) {
      vintoReadinessBonus = 5; // Bonus for being in good position to call Vinto
    }

    // Weighted combination
    const reward =
      scoreAdvantage * 2.5 * phaseMultiplier + // Score difference vs average
      competitiveAdvantage * 0.5 + // Score vs best opponent
      cardCountAdvantage * 1.0 + // Fewer cards is better
      knowledgeAdvantage * (isEarlyGame ? 2.0 : 0.5) + // Info valuable early
      actionCardBonus + // Action cards are useful
      -knownHighCardPenalty + // Penalty for known high cards
      vintoReadinessBonus + // Ready to win
      terminalBonus; // Terminal state bonus

    // Normalize to [0, 1] using sigmoid
    return 1 / (1 + Math.exp(-reward / 20));
  }

  /**
   * Check if state is terminal
   */
  private isTerminal(state: MCTSGameState): boolean {
    const { MCTSStateTransition } = require('./mcts-state-transition');
    return MCTSStateTransition.isTerminal(state);
  }

  /**
   * Helper: get value for rank
   */
  private getRankValue(rank: Rank): number {
    const values: Record<Rank, number> = {
      A: 1,
      '2': 2,
      '3': 3,
      '4': 4,
      '5': 5,
      '6': 6,
      '7': 7,
      '8': 8,
      '9': 9,
      '10': 10,
      J: 11,
      Q: 12,
      K: 0,
      Joker: 0, // Joker not used but required by Rank type
    };
    return values[rank];
  }

  /**
   * Helper: get action for rank
   */
  private getRankAction(rank: Rank): string | undefined {
    const actions: Partial<Record<Rank, string>> = {
      '7': 'peek-own',
      '8': 'peek-opponent',
      '9': 'swap',
      '10': 'peek-and-swap',
      J: 'blind-swap',
      Q: 'peek-and-swap',
      K: 'declare',
      A: 'force-draw',
    };
    return actions[rank];
  }
}

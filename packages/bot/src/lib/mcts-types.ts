// services/mcts-types.ts
import { Card, Pile, Rank } from '@vinto/shapes';
import { BotMemory } from './bot-memory';

/**
 * Type of move in the game
 */
export type MCTSMoveType =
  | 'draw'
  | 'take-discard'
  | 'use-action'
  | 'swap'
  | 'discard'
  | 'toss-in'
  | 'call-vinto'
  | 'pass';

/**
 * Target for an action
 */
export interface MCTSActionTarget {
  playerId: string;
  position: number;
}

/**
 * A move in the game
 */
export interface MCTSMove {
  type: MCTSMoveType;
  playerId: string;

  // Action-specific data
  actionCard?: Card;
  targets?: MCTSActionTarget[];
  swapPosition?: number;
  declaredRank?: Rank;
  tossInPositions?: number[]; // Array of positions for multi-card toss-in
  shouldSwap?: boolean; // For peek-and-swap actions
}

/**
 * Simplified player state for MCTS
 */
export interface MCTSPlayerState {
  id: string;
  cardCount: number;
  knownCards: Map<number, { card: Card | null; confidence: number }>; // Position -> Memory (from bot's perspective)
  score: number; // Estimated score based on known cards
}

/**
 * Game state for MCTS simulation
 */
export interface MCTSGameState {
  // Known information
  players: MCTSPlayerState[];
  currentPlayerIndex: number;
  botPlayerId: string;

  discardPileTop: Card | null;
  discardPile: Pile; // Full discard pile history
  deckSize: number;

  // Bot's belief state (imperfect information)
  botMemory: BotMemory;

  // Hidden information (sampled during determinization)
  // Map of playerId-position -> Card
  hiddenCards: Map<string, Card>;

  // Current turn state
  pendingCard: Card | null; // Card drawn but not yet swapped/discarded
  isTossInPhase: boolean;
  tossInRanks?: [Rank, ...Rank[]]; // Available ranks for toss-in (multiple ranks can be valid)

  // Game phase
  turnCount: number;
  finalTurnTriggered: boolean;

  // Coalition context (for final round)
  vintoCallerId: string | null; // Player who called Vinto
  coalitionLeaderId: string | null; // Coalition leader (if selected)

  // For evaluation
  isTerminal: boolean;
  winner: string | null;
}

/**
 * MCTS Tree Node
 */
export class MCTSNode {
  state: MCTSGameState;
  move: MCTSMove | null;
  parent: MCTSNode | null;
  children: MCTSNode[];

  visits: number;
  totalReward: number;

  untriedMoves: MCTSMove[];
  isTerminal: boolean;
  isFullyExpanded: boolean;

  constructor(
    state: MCTSGameState,
    move: MCTSMove | null,
    parent: MCTSNode | null
  ) {
    this.state = state;
    this.move = move;
    this.parent = parent;
    this.children = [];

    this.visits = 0;
    this.totalReward = 0;

    this.untriedMoves = [];
    this.isTerminal = state.isTerminal;
    this.isFullyExpanded = false;
  }

  /**
   * Get average reward (exploitation term)
   */
  getAverageReward(): number {
    return this.visits > 0 ? this.totalReward / this.visits : 0;
  }

  /**
   * Check if this node has untried moves
   */
  hasUntriedMoves(): boolean {
    return this.untriedMoves.length > 0;
  }

  /**
   * Get a random untried move
   */
  getRandomUntriedMove(): MCTSMove | null {
    if (this.untriedMoves.length === 0) return null;

    const index = Math.floor(Math.random() * this.untriedMoves.length);
    return this.untriedMoves.splice(index, 1)[0];
  }

  /**
   * Add a child node
   */
  addChild(child: MCTSNode): void {
    this.children.push(child);

    if (this.untriedMoves.length === 0) {
      this.isFullyExpanded = true;
    }
  }

  /**
   * Select best child using UCB1 formula
   */
  selectBestChildUCB1(explorationConstant: number): MCTSNode | null {
    if (this.children.length === 0) return null;

    let bestScore = -Infinity;
    let bestChild: MCTSNode | null = null;

    for (const child of this.children) {
      if (child.visits === 0) {
        // Unvisited child has infinite value
        return child;
      }

      const exploitation = child.totalReward / child.visits;
      const exploration =
        explorationConstant * Math.sqrt(Math.log(this.visits) / child.visits);

      const ucb1Score = exploitation + exploration;

      if (ucb1Score > bestScore) {
        bestScore = ucb1Score;
        bestChild = child;
      }
    }

    return bestChild;
  }

  /**
   * Select child with highest visit count (most robust choice)
   */
  selectMostVisitedChild(): MCTSNode | null {
    if (this.children.length === 0) return null;

    let maxVisits = -1;
    let bestChild: MCTSNode | null = null;

    for (const child of this.children) {
      if (child.visits > maxVisits) {
        maxVisits = child.visits;
        bestChild = child;
      }
    }

    return bestChild;
  }

  /**
   * Backpropagate reward up the tree
   */
  backpropagate(reward: number): void {
    this.visits++;
    this.totalReward += reward;

    if (this.parent) {
      this.parent.backpropagate(reward);
    }
  }

  /**
   * Get depth of this node in the tree
   */
  getDepth(): number {
    let depth = 0;
    let current: MCTSNode | null = this.parent;

    while (current !== null) {
      depth++;
      current = current.parent;
    }

    return depth;
  }

  /**
   * Debug string representation
   */
  toString(): string {
    const moveStr = this.move
      ? `${this.move.type} by ${this.move.playerId}`
      : 'ROOT';
    return `Node[${moveStr}] visits=${
      this.visits
    } reward=${this.totalReward.toFixed(2)} children=${this.children.length}`;
  }
}

/**
 * MCTS Configuration
 */
export interface MCTSConfig {
  iterations: number;
  explorationConstant: number;
  rolloutDepth: number;
  timeLimit: number; // milliseconds
}

/**
 * Default MCTS configurations by difficulty
 */
export const MCTS_DIFFICULTY_CONFIGS: Record<
  'easy' | 'moderate' | 'hard',
  MCTSConfig
> = {
  easy: {
    iterations: 500,
    explorationConstant: Math.sqrt(2),
    rolloutDepth: 5,
    timeLimit: 500,
  },
  moderate: {
    iterations: 1_500,
    explorationConstant: Math.sqrt(2),
    rolloutDepth: 10,
    timeLimit: 1_000,
  },
  hard: {
    iterations: 5_000,
    explorationConstant: Math.sqrt(2),
    rolloutDepth: 15,
    timeLimit: 1_500,
  },
};

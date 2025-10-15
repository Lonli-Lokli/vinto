// engine/types/GameState.ts
// Authoritative game state - the single source of truth

import { Card, Difficulty, Pile, Rank } from './domain-types';

/**
 * Complete, serializable game state
 * This is the ONLY state model the engine uses
 */
export interface GameState {
  // Game metadata
  gameId: string;
  roundNumber: number;
  turnCount: number;

  // Phase management
  phase: GamePhase;
  subPhase: GameSubPhase;
  finalTurnTriggered: boolean;

  // Players
  players: PlayerState[];
  currentPlayerIndex: number;

  // Vinto state
  vintoCallerId: string | null;
  coalitionLeaderId: string | null;

  // Deck state
  drawPile: Pile;
  discardPile: Pile;

  // Active action state (replaces ActionStore)
  pendingAction: PendingAction | null;

  // Toss-in state (replaces TossInStore)
  activeTossIn: ActiveTossIn | null;

  // Action history (for UI display)
  recentActions: GameActionHistory[];

  // Configuration
  difficulty: Difficulty;
}

/**
 * Player state within the game
 * Serializable version of Player from shapes.ts
 */
export interface PlayerState {
  id: string;
  name: string;
  isHuman: boolean;
  isBot: boolean;
  cards: Card[];

  // Serializable sets (stored as arrays)
  knownCardPositions: number[];

  // Vinto flags
  isVintoCaller: boolean;
  coalitionWith: string[];

  // Bot-specific state
  botMemory?: SerializedBotMemory;
  opponentKnowledge?: Record<string, SerializedOpponentKnowledge>;
}

export type PlayerPosition = 'bottom' | 'left' | 'top' | 'right';
/**
 * Pending action state (multi-step actions)
 */
export interface PendingAction {
  card: Card;
  playerId: string;
  actionPhase: ActionPhase;
  targetType?: TargetType;
  targets: ActionTarget[];
  declaredRank?: Rank;
  swapPosition?: number;
}

export type TargetType =
  | 'own-card'
  | 'opponent-card'
  | 'peek-then-swap'
  | 'swap-cards'
  | 'force-draw'
  | 'declare-action';

export type ActionPhase =
  | 'choosing-action'
  | 'selecting-target'
  | 'peeking'
  | 'swapping'
  | 'declaring-rank';

export interface ActionTarget {
  playerId: string;
  position: number;
  card?: Card;
}

/**
 * Active toss-in state
 */
export interface ActiveTossIn {
  rank: Rank;
  initiatorId: string;
  originalPlayerIndex: number; // Track whose turn it was when toss-in started
  participants: string[];
  queuedActions: TossInAction[];
  waitingForInput: boolean;
  timeRemaining?: number;
  playersReadyForNextTurn: string[]; // Player IDs who confirmed they're done with toss-in
}

export interface TossInAction {
  playerId: string;
  card: Card;
  position: number;
}

/**
 * Action history entry (for UI display)
 */
export interface GameActionHistory {
  playerId: string;
  playerName: string;
  description: string;
  timestamp: number;
  turnNumber: number;
}

/**
 * Game phases
 */
export type GamePhase = 'setup' | 'playing' | 'final' | 'scoring';

export type GameSubPhase =
  | 'idle'
  | 'drawing'
  | 'choosing'
  | 'selecting'
  | 'awaiting_action'
  | 'declaring_rank'
  | 'ai_thinking'
  | 'toss_queue_active'
  | 'toss_queue_processing';

/**
 * Serialized bot memory (for state persistence)
 */
export interface SerializedBotMemory {
  ownCards: Record<number, SerializedCardMemory>;
  opponentCards: Record<string, Record<number, SerializedCardMemory>>;
  cardDistribution: Record<Rank, number>;
  seenCards: Record<string, number>;
}

export interface SerializedCardMemory {
  card: Card;
  confidence: number;
  lastSeen: number;
}

export interface SerializedOpponentKnowledge {
  knownCards: Record<number, Card>;
}

/**
 * Create initial game state
 */
export function createInitialGameState(
  gameId: string,
  players: PlayerState[],
  difficulty: Difficulty,
  deck: Card[]
): GameState {
  return {
    gameId,
    roundNumber: 1,
    turnCount: 0,
    phase: 'setup',
    subPhase: 'idle',
    finalTurnTriggered: false,
    players,
    currentPlayerIndex: 0,
    vintoCallerId: null,
    coalitionLeaderId: null,
    drawPile: Pile.fromCards(deck),
    discardPile: new Pile(),
    pendingAction: null,
    activeTossIn: null,
    recentActions: [],
    difficulty,
  };
}

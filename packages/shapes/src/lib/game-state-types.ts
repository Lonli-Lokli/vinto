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
  turnNumber: number;

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
  nickname: string;
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
  from: 'drawing' | 'hand';
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

/**
 * Action phase tracking for card actions
 *
 * All card actions follow the same flow:
 * 1. 'choosing-action' - Player decides to use card action (after USE_CARD_ACTION)
 * 2. 'selecting-target' - Player selects target(s) via SELECT_ACTION_TARGET
 *
 * Progress through multi-step actions (J, Q, K) is tracked via targets.length:
 * - Jack: targets.length 0→1→2 (select 2 cards to swap)
 * - Queen: targets.length 0→1→2 (peek 2 cards, then decide swap)
 * - King: targets.length 0→1 (select card, then declare rank via declaredRank field)
 * - Simple (7,8,9,10,A): targets.length 0→1 (select single target)
 */
export type ActionPhase = 'choosing-action' | 'selecting-target';

export interface ActionTarget {
  playerId: string;
  position: number;
  card?: Card;
}

/**
 * Active toss-in state
 */
export interface ActiveTossIn {
  ranks: [Rank, ...Rank[]]; // Support multiple ranks, but always at least one element
  initiatorId: string;
  originalPlayerIndex: number; // Track whose turn it was when toss-in started
  participants: string[];
  queuedActions: TossInAction[];
  waitingForInput: boolean;
  timeRemaining?: number;
  playersReadyForNextTurn: string[]; // Player IDs who confirmed they're done with toss-in
  failedAttempts?: FailedTossInAttempt[]; // Track invalid toss-in attempts for animations
  tossInCompleted?: boolean; // Flag indicating if toss-in period has concluded
}

export interface TossInAction {
  playerId: string;
  card: Card;
  position: number;
}

export interface FailedTossInAttempt {
  playerId: string;
  cardRank: Rank;
  position: number;
  expectedRanks: [Rank, ...Rank[]];
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

/**
 * Game sub-phases - detailed turn progression states
 *
 * TURN FLOW:
 * idle → drawing/choosing → selecting → awaiting_action → toss_queue_active → idle
 *
 * Sub-phase descriptions:
 */
export type GameSubPhase =
  /**
   * 'idle' - Waiting for player to start their turn
   * - Occurs at the beginning of a player's turn
   * - Player can either draw from deck or take from discard pile
   * - For bots, transitions to 'ai_thinking' immediately
   * - For humans, waits for player input (draw or take discard)
   */
  | 'idle'

  /**
   * 'drawing' - Card is being drawn from deck (transitional state)
   * - Brief state during card draw animation
   * - Immediately transitions to 'choosing'
   * - Not commonly used in practice, usually skips directly to 'choosing'
   */
  | 'drawing'

  /**
   * 'choosing' - Player has drawn a card and must choose what to do
   * - Occurs after drawing a card from the deck
   * - Player sees the drawn card and must decide:
   *   1. Use card action (if action card) → 'awaiting_action'
   *   2. Swap card into hand → 'selecting'
   *   3. Discard without using (rare)
   * - The drawn card is in pendingAction.card
   */
  | 'choosing'

  /**
   * 'selecting' - Player swapped a card and must choose final action
   * - Occurs after swapping drawn card into hand
   * - The removed card is now in pendingAction.card
   * - Player must decide:
   *   1. Use removed card's action (if action card and declared correctly)
   *   2. Discard the removed card
   * - After discarding, triggers toss-in period → 'toss_queue_active'
   */
  | 'selecting'

  /**
   * 'awaiting_action' - Waiting for player to complete a card action
   * - Occurs when player chooses to use a card's action
   * - Player must select action targets or make action-specific choices
   * - Different actions require different inputs:
   *   - Ace (A): Select opponent to draw penalty card
   *   - King (K): Declare rank (stays in 'awaiting_action', uses actionPhase: 'declaring-rank')
   *   - Queen (Q): Select 2 cards to peek, then optionally swap
   *   - Jack (J): Select 2 cards to swap
   *   - 10/9: Select 1 opponent card to peek
   *   - 8/7: Select 1 own card to peek
   * - The specific step within the action is tracked via PendingAction.actionPhase
   * - After action completes, transitions to 'toss_queue_active'
   */
  | 'awaiting_action'

  /**
   * 'ai_thinking' - Bot is calculating its next move
   * - Replaces 'idle' for bot players
   * - Bot AI is running to decide action
   * - Transitions to appropriate phase after decision:
   *   - If drawing: 'choosing'
   *   - If taking discard: 'awaiting_action'
   */
  | 'ai_thinking'

  /**
   * 'toss_queue_active' - Toss-in period is active, waiting for players
   * - Occurs after a card is discarded or action completes
   * - All players can toss in matching rank cards
   * - Players can mark themselves as "ready" to continue
   * - When all human players are ready:
   *   - If queued actions exist → 'toss_queue_processing'
   *   - If no queued actions → 'idle' (next turn)
   * - Toss-in details stored in activeTossIn
   */
  | 'toss_queue_active'

  /**
   * 'toss_queue_processing' - Processing queued toss-in actions
   * - Occurs when players finish tossing in cards
   * - Queued action cards are executed one by one
   * - Each action may require player input (transitions to 'awaiting_action')
   * - After each action completes, returns to 'toss_queue_active'
   * - When all queued actions are processed → 'idle' (next turn)
   */
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
    turnNumber: 0,
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

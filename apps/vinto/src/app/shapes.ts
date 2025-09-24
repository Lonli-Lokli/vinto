// types/game.ts
export interface Card {
  id: string;
  rank: string;
  value: number;
  action?: string;
  suit?: '♠' | '♥' | '♦' | '♣';
}

export interface Player {
  id: string;
  name: string;
  cards: Card[];
  knownCardPositions: Set<number>;
  isHuman: boolean;
  position: 'bottom' | 'left' | 'top' | 'right';
  avatar: string;
  coalitionWith: Set<string>;
}

export interface GameState {
  players: Player[];
  currentPlayerIndex: number;
  drawPile: Card[];
  discardPile: Card[];
  phase: 'setup' | 'playing' | 'final' | 'scoring';
  gameId: string;
  roundNumber: number;
  turnCount: number;
  maxTurns: number;
  finalTurnTriggered: boolean;
}

export interface AIMove {
  type: 'draw' | 'discard' | 'swap';
  confidence: number;
  expectedValue: number;
  reasoning: string;
  thinkingTime: number;
  networkTime?: number;
  error?: boolean;
}

export type Difficulty = 'easy' | 'medium' | 'hard';

export interface GameStore extends GameState {
  oracle: any; // Will be typed properly in the client
  aiThinking: boolean;
  currentMove: AIMove | null;
  sessionActive: boolean;
  pendingCard: Card | null;
  isSelectingSwapPosition: boolean;
  setupPeeksRemaining: number;
  waitingForTossIn: boolean;
  tossInTimer: number;

  initGame: () => Promise<void>;
  updateDifficulty: (diff: Difficulty) => void;
  peekCard: (playerId: string, pos: number) => void;
  finishSetup: () => void;
  drawCard: () => void;
  takeFromDiscard: () => void;
  swapCard: (pos: number) => void;
  cancelSwap: () => void;
  tossInCard: (playerId: string, position: number) => void;
  makeAIMove: (diff: string) => Promise<void>;
  formCoalition: (playerId1: string, playerId2: string) => void;
  breakCoalition: (playerId1: string, playerId2: string) => void;
  callVinto: () => void;
  calculateFinalScores: () => { [playerId: string]: number };
}
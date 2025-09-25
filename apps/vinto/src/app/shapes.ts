// types/game.ts
export interface Card {
  id: string;
  rank: Rank;
  value: number;
  action?: string;
  played: boolean;
}

export type Rank =
  | '2'
  | '3'
  | '4'
  | '5'
  | '6'
  | '7'
  | '8'
  | '9'
  | '10'
  | 'J'
  | 'Q'
  | 'K'
  | 'A'
  | 'Joker';

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

export type Difficulty = 'basic' | 'moderate' | 'hard' | 'ultimate';

export type TossInTime = 3 | 5 | 7 | 10;

export interface GameStore extends GameState {
  oracle: any; // Will be typed properly in the client
  aiThinking: boolean;
  currentMove: AIMove | null;
  sessionActive: boolean;
  pendingCard: Card | null;
  isSelectingSwapPosition: boolean;
  isChoosingCardAction: boolean;
  selectedSwapPosition: number | null;
  setupPeeksRemaining: number;
  waitingForTossIn: boolean;
  tossInTimer: number;
  tossInTimeConfig: TossInTime;
  difficulty: Difficulty;

  initGame: () => Promise<void>;
  updateDifficulty: (diff: Difficulty) => void;
  updateTossInTime: (time: TossInTime) => void;
  peekCard: (playerId: string, pos: number) => void;
  finishSetup: () => void;
  drawCard: () => void;
  takeFromDiscard: () => void;
  chooseSwap: () => void;
  choosePlayCard: () => void;
  swapCard: (pos: number) => void;
  cancelSwap: () => void;
  tossInCard: (playerId: string, position: number) => void;
  makeAIMove: (diff: string) => Promise<void>;
  formCoalition: (playerId1: string, playerId2: string) => void;
  breakCoalition: (playerId1: string, playerId2: string) => void;
  callVinto: () => void;
  calculateFinalScores: () => { [playerId: string]: number };
}

export class NeverError extends Error {
  constructor(value: never) {
    super(`NeverError: Unexpected value ${value}`);
  }
}
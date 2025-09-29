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
  knownCardPositions: Set<number>; // Permanently known cards (setup phase)
  temporarilyVisibleCards: Set<number>; // Cards visible during current action only
  isHuman: boolean;
  position: 'bottom' | 'left' | 'top' | 'right';
  avatar: string;
  coalitionWith: Set<string>;
}

export interface TempState {
  gameId: string;
  roundNumber: number;
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

export type Difficulty = 'easy' | 'moderate' | 'hard' ;

export type TossInTime = 5 | 7 | 10;

import type { OracleVintoClient } from './lib/oracle-client';

export interface GameStore extends GameState {
  oracle: OracleVintoClient; // Client used by the store
  aiThinking: boolean;
  currentMove: AIMove | null;
  sessionActive: boolean;
  pendingCard: Card | null;
  isSelectingSwapPosition: boolean;
  isChoosingCardAction: boolean;
  isAwaitingActionTarget: boolean;
  actionContext: {
    action: string;
    playerId: string;
    targetType?:
      | 'own-card'
      | 'opponent-card'
      | 'swap-cards'
      | 'peek-then-swap'
      | 'declare-action'
      | 'force-draw';
    declaredCard?: Rank;
  } | null;
  selectedSwapPosition: number | null;
  swapTargets: { playerId: string; position: number }[];
  peekTargets: { playerId: string; position: number; card?: Card }[];
  isDeclaringRank: boolean;
  swapPosition: number | null;
  setupPeeksRemaining: number;
  waitingForTossIn: boolean;
  tossInTimer: number;
  tossInTimeConfig: TossInTime;
  difficulty: Difficulty;
  canCallVintoAfterHumanTurn: boolean;

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
  executeCardAction: (card: Card, playerId: string) => void;
  selectActionTarget: (playerId: string, position: number) => void;
  executeQueenSwap: () => void;
  skipQueenSwap: () => void;
  declareKingAction: (rank: Rank) => void;
  declareRank: (rank: Rank) => void;
  skipDeclaration: () => void;
  cancelAction: () => void;
  discardCard: () => void;
  tossInCard: (playerId: string, position: number) => void;
  makeAIMove: (diff: string) => Promise<void>;
  formCoalition: (playerId1: string, playerId2: string) => void;
  breakCoalition: (playerId1: string, playerId2: string) => void;
  callVinto: () => void;
  calculateFinalScores: () => { [playerId: string]: number };
  updateVintoCallAvailability: () => void;
  startTossInPeriod: () => void;
  clearTemporaryCardVisibility: () => void;
}

export class NeverError extends Error {
  constructor(value: never) {
    super(`NeverError: Unexpected value ${value}`);
  }
}

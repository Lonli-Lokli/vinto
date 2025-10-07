// types/game.ts
export interface Card {
  id: string;
  rank: Rank;
  value: number;
  actionText?: string;
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
export type CardAction =   'peek-own' | 'peek-opponent' | 'peek-and-swap' | 'swap-cards' | 'force-draw' | 'declare-action';
      
export interface OpponentKnowledge {
  opponentId: string;
  knownCards: Map<number, Card>; // position -> Card that bot knows opponent has
}

export interface Player {
  id: string;
  name: string;
  cards: Card[];
  knownCardPositions: Set<number>; // Permanently known cards (own cards known during setup/gameplay)
  temporarilyVisibleCards: Set<number>; // Cards visible during current action only
  highlightedCards: Set<number>; // Cards highlighted during bot peek actions (not revealed)
  opponentKnowledge: Map<string, OpponentKnowledge>; // Track known opponent cards (bots only)
  isHuman: boolean;
  isBot: boolean;
  position: 'bottom' | 'left' | 'top' | 'right';
  coalitionWith: Set<string>;
  isVintoCaller?: boolean; // True if this player called Vinto
  isCoalitionLeader?: boolean; // True if this player is leading the coalition
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

export type Difficulty = 'easy' | 'moderate' | 'hard';

export interface GameStore extends GameState {
  aiThinking: boolean;
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
  difficulty: Difficulty;
  canCallVintoAfterHumanTurn: boolean;

  initGame: () => Promise<void>;
  updateDifficulty: (diff: Difficulty) => void;
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

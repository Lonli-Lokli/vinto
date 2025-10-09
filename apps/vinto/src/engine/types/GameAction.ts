// engine/types/GameAction.ts
// All possible game actions - the "language" for communicating with the engine

import { Card, Difficulty, Rank } from '../../app/shapes';

/**
 * Discriminated union of all game actions
 * Every user interaction becomes one of these actions
 */
export type GameAction =
  // Turn actions
  | DrawCardAction
  | TakeDiscardAction
  | SwapCardAction
  | DiscardCardAction

  // Card actions
  | UseCardActionAction
  | SelectActionTargetAction
  | ConfirmPeekAction
  | ExecuteQueenSwapAction
  | SkipQueenSwapAction
  | DeclareKingActionAction

  // Toss-in actions
  | ParticipateInTossInAction
  | FinishTossInPeriodAction

  // Game flow
  | CallVintoAction
  | SetCoalitionLeaderAction
  | AdvanceTurnAction
  | ProcessAITurnAction

  // Setup
  | PeekSetupCardAction
  | FinishSetupAction

  // Configuration
  | UpdateDifficultyAction

  // Debug/Testing
  | SetNextDrawCardAction;

/**
 * Turn Actions
 */
export interface DrawCardAction {
  type: 'DRAW_CARD';
  payload: {
    playerId: string;
  };
}

export interface TakeDiscardAction {
  type: 'TAKE_DISCARD';
  payload: {
    playerId: string;
  };
}

export interface SwapCardAction {
  type: 'SWAP_CARD';
  payload: {
    playerId: string;
    position: number;
    declaredRank?: Rank;
  };
}

export interface DiscardCardAction {
  type: 'DISCARD_CARD';
  payload: {
    playerId: string;
  };
}

/**
 * Card Actions
 */
export interface UseCardActionAction {
  type: 'USE_CARD_ACTION';
  payload: {
    playerId: string;
    card: Card;
  };
}

export interface SelectActionTargetAction {
  type: 'SELECT_ACTION_TARGET';
  payload: {
    playerId: string;
    targetPlayerId: string;
    position: number;
  };
}

export interface ConfirmPeekAction {
  type: 'CONFIRM_PEEK';
  payload: {
    playerId: string;
  };
}

export interface ExecuteQueenSwapAction {
  type: 'EXECUTE_QUEEN_SWAP';
  payload: {
    playerId: string;
  };
}

export interface SkipQueenSwapAction {
  type: 'SKIP_QUEEN_SWAP';
  payload: {
    playerId: string;
  };
}

export interface DeclareKingActionAction {
  type: 'DECLARE_KING_ACTION';
  payload: {
    playerId: string;
    declaredRank: Rank;
  };
}

/**
 * Toss-in Actions
 */
export interface ParticipateInTossInAction {
  type: 'PARTICIPATE_IN_TOSS_IN';
  payload: {
    playerId: string;
    position: number;
  };
}

export interface FinishTossInPeriodAction {
  type: 'FINISH_TOSS_IN_PERIOD';
  payload: {
    initiatorId: string;
  };
}

/**
 * Game Flow Actions
 */
export interface CallVintoAction {
  type: 'CALL_VINTO';
  payload: {
    playerId: string;
  };
}

export interface SetCoalitionLeaderAction {
  type: 'SET_COALITION_LEADER';
  payload: {
    leaderId: string;
  };
}

export interface AdvanceTurnAction {
  type: 'ADVANCE_TURN';
  payload: Record<string, never>; // Empty payload
}

export interface ProcessAITurnAction {
  type: 'PROCESS_AI_TURN';
  payload: {
    playerId: string;
  };
}

/**
 * Setup Actions
 */
export interface PeekSetupCardAction {
  type: 'PEEK_SETUP_CARD';
  payload: {
    playerId: string;
    position: number;
  };
}

export interface FinishSetupAction {
  type: 'FINISH_SETUP';
  payload: {
    playerId: string;
  };
}

/**
 * Configuration Actions
 */
export interface UpdateDifficultyAction {
  type: 'UPDATE_DIFFICULTY';
  payload: {
    difficulty: Difficulty;
  };
}

/**
 * Debug/Testing Actions
 */
export interface SetNextDrawCardAction {
  type: 'SET_NEXT_DRAW_CARD';
  payload: {
    rank: Rank;
  };
}

/**
 * Action creator helper functions
 */
export const GameActions = {
  drawCard: (playerId: string): DrawCardAction => ({
    type: 'DRAW_CARD',
    payload: { playerId },
  }),

  takeDiscard: (playerId: string): TakeDiscardAction => ({
    type: 'TAKE_DISCARD',
    payload: { playerId },
  }),

  swapCard: (playerId: string, position: number, declaredRank?: Rank): SwapCardAction => ({
    type: 'SWAP_CARD',
    payload: { playerId, position, declaredRank },
  }),

  discardCard: (playerId: string): DiscardCardAction => ({
    type: 'DISCARD_CARD',
    payload: { playerId },
  }),

  playCardAction: (playerId: string, card: Card): UseCardActionAction => ({
    type: 'USE_CARD_ACTION',
    payload: { playerId, card },
  }),

  selectActionTarget: (
    playerId: string,
    targetPlayerId: string,
    position: number
  ): SelectActionTargetAction => ({
    type: 'SELECT_ACTION_TARGET',
    payload: { playerId, targetPlayerId, position },
  }),

  confirmPeek: (playerId: string): ConfirmPeekAction => ({
    type: 'CONFIRM_PEEK',
    payload: { playerId },
  }),

  executeQueenSwap: (playerId: string): ExecuteQueenSwapAction => ({
    type: 'EXECUTE_QUEEN_SWAP',
    payload: { playerId },
  }),

  skipQueenSwap: (playerId: string): SkipQueenSwapAction => ({
    type: 'SKIP_QUEEN_SWAP',
    payload: { playerId },
  }),

  declareKingAction: (playerId: string, declaredRank: Rank): DeclareKingActionAction => ({
    type: 'DECLARE_KING_ACTION',
    payload: { playerId, declaredRank },
  }),

  participateInTossIn: (playerId: string, position: number): ParticipateInTossInAction => ({
    type: 'PARTICIPATE_IN_TOSS_IN',
    payload: { playerId, position },
  }),

  finishTossInPeriod: (initiatorId: string): FinishTossInPeriodAction => ({
    type: 'FINISH_TOSS_IN_PERIOD',
    payload: { initiatorId },
  }),

  callVinto: (playerId: string): CallVintoAction => ({
    type: 'CALL_VINTO',
    payload: { playerId },
  }),

  setCoalitionLeader: (leaderId: string): SetCoalitionLeaderAction => ({
    type: 'SET_COALITION_LEADER',
    payload: { leaderId },
  }),

  advanceTurn: (): AdvanceTurnAction => ({
    type: 'ADVANCE_TURN',
    payload: {},
  }),

  processAITurn: (playerId: string): ProcessAITurnAction => ({
    type: 'PROCESS_AI_TURN',
    payload: { playerId },
  }),

  peekSetupCard: (playerId: string, position: number): PeekSetupCardAction => ({
    type: 'PEEK_SETUP_CARD',
    payload: { playerId, position },
  }),

  finishSetup: (playerId: string): FinishSetupAction => ({
    type: 'FINISH_SETUP',
    payload: { playerId },
  }),

  updateDifficulty: (difficulty: Difficulty): UpdateDifficultyAction => ({
    type: 'UPDATE_DIFFICULTY',
    payload: { difficulty },
  }),

  setNextDrawCard: (rank: Rank): SetNextDrawCardAction => ({
    type: 'SET_NEXT_DRAW_CARD',
    payload: { rank },
  }),
};

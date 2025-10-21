// engine/types/GameAction.ts
// All possible game actions - the "language" for communicating with the engine

import { Card, Difficulty, Rank } from './domain-types';

/**
 * Discriminated union of all game actions
 * Every user interaction becomes one of these actions
 */
export type GameAction =
  // Turn actions
  | DrawCardAction
  | PlayDiscardAction
  | SwapCardAction
  | DiscardCardAction

  // Card actions
  | UseCardActionAction
  | SelectActionTargetAction
  | ConfirmPeekAction
  | ExecuteJackSwapAction
  | SkipJackSwapAction
  | ExecuteQueenSwapAction
  | SkipQueenSwapAction
  | DeclareKingActionAction

  // Toss-in actions
  | ParticipateInTossInAction
  | PlayerTossInFinishedAction
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

export interface PlayDiscardAction {
  type: 'PLAY_DISCARD';
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

export type AceTargetActionPayload = {
  rank: 'A';
  playerId: string;
  targetPlayerId: string;
};
export type OtherTargetActionPayload = {
  rank: 'Any';
  playerId: string;
  targetPlayerId: string;
  position: number;
};
export type TargetActionPayload =
  | AceTargetActionPayload
  | OtherTargetActionPayload;

export interface SelectActionTargetAction {
  type: 'SELECT_ACTION_TARGET';
  payload: TargetActionPayload;
}

export interface ConfirmPeekAction {
  type: 'CONFIRM_PEEK';
  payload: {
    playerId: string;
  };
}

export interface ExecuteJackSwapAction {
  type: 'EXECUTE_JACK_SWAP';
  payload: {
    playerId: string;
  };
}

export interface SkipJackSwapAction {
  type: 'SKIP_JACK_SWAP';
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

export interface PlayerTossInFinishedAction {
  type: 'PLAYER_TOSS_IN_FINISHED';
  payload: {
    playerId: string;
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

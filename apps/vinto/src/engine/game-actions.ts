import {
  AdvanceTurnAction,
  CallVintoAction,
  Card,
  ConfirmPeekAction,
  DeclareKingActionAction,
  Difficulty,
  DiscardCardAction,
  DrawCardAction,
  ExecuteQueenSwapAction,
  FinishSetupAction,
  FinishTossInPeriodAction,
  ParticipateInTossInAction,
  PlayerTossInFinishedAction,
  PeekSetupCardAction,
  ProcessAITurnAction,
  Rank,
  SelectActionTargetAction,
  SetCoalitionLeaderAction,
  SetNextDrawCardAction,
  SkipQueenSwapAction,
  SwapCardAction,
  TakeDiscardAction,
  UpdateDifficultyAction,
  UseCardActionAction,
} from '@/shared';

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

  swapCard: (
    playerId: string,
    position: number,
    declaredRank?: Rank
  ): SwapCardAction => ({
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

  declareKingAction: (
    playerId: string,
    declaredRank: Rank
  ): DeclareKingActionAction => ({
    type: 'DECLARE_KING_ACTION',
    payload: { playerId, declaredRank },
  }),

  participateInTossIn: (
    playerId: string,
    position: number
  ): ParticipateInTossInAction => ({
    type: 'PARTICIPATE_IN_TOSS_IN',
    payload: { playerId, position },
  }),

  playerTossInFinished: (playerId: string): PlayerTossInFinishedAction => ({
    type: 'PLAYER_TOSS_IN_FINISHED',
    payload: { playerId },
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

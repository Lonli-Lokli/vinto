// engine/GameEngine.ts
// Core game engine - pure reducer that transforms state via actions

import { actionValidator } from './action-validator';
import { handleSwapCard } from './cases/swap-card';
import { handleAdvanceTurn } from './cases/advance-turn';
import { handleCallVinto } from './cases/call-vinto';
import { handleConfirmPeek } from './cases/confirm-peek';
import { handleDeclareKingAction } from './cases/declare-king-action';
import { handleDiscardCard } from './cases/discard-card';
import { handleDrawCard } from './cases/draw-card';
import { handleExecuteQueenSwap } from './cases/execute-queen-swap';
import { handleFinishSetup } from './cases/finish-setup';
import { handleFinishTossInPeriod } from './cases/finish-toss-in';
import { handleParticipateInTossIn } from './cases/participate-in-toss';
import { handlePlayerTossInFinished } from './cases/player-toss-in-finished';
import { handlePeekSetupCard } from './cases/peek-setup-card';
import { handleProcessAITurn } from './cases/process-ai-turn';
import { handleSelectActionTarget } from './cases/select-action-target';
import { handleSetCoalitionLeader } from './cases/set-coalition-leader';
import { handleSkipQueenSwap } from './cases/skip-queen-swap';
import { handlePlayDiscard } from './cases/play-discard';
import { handleUseCardAction } from './cases/use-card';
import { handleUpdateDifficulty } from './cases/update-difficulty';
import { handleSetNextDrawCard } from './cases/set-next-draw-card';
import { GameAction, GameState, NeverError } from '@/shared';

/**
 * GameEngine - The authoritative game logic
 *
 * This is a pure, stateless service that implements the core game rules.
 * It has ONE primary method: reduce()
 *
 * Key principles:
 * - Pure functions only (no side effects)
 * - No mutations (uses deep copy)
 * - No dependencies on UI, stores, or client code
 * - Fully testable
 * - Server-runnable
 */
export class GameEngine {
  /**
   * Core reducer: State + Action → New State
   *
   * This is the ONLY method that modifies game state
   * All game logic flows through here
   *
   * @param state Current game state
   * @param action Action to apply
   * @returns New game state (never mutates input)
   */
  static reduce(state: GameState, action: GameAction): GameState {
    // Validate action is legal in current state
    const validation = actionValidator(state, action);
    if (!validation.valid) {
      console.warn(`Invalid action ${action.type}: ${validation.reason}`);
      return state; // Return unchanged state for invalid actions
    }

    // Route to specific handler based on action type
    switch (action.type) {
      case 'DRAW_CARD':
        return handleDrawCard(state, action);

      case 'SWAP_CARD':
        return handleSwapCard(state, action);

      case 'DISCARD_CARD':
        return handleDiscardCard(state, action);

      case 'ADVANCE_TURN':
        return handleAdvanceTurn(state, action);

      case 'PLAY_DISCARD':
        return handlePlayDiscard(state, action);

      case 'USE_CARD_ACTION':
        return handleUseCardAction(state, action);

      case 'SELECT_ACTION_TARGET':
        return handleSelectActionTarget(state, action);

      case 'CONFIRM_PEEK':
        return handleConfirmPeek(state, action);

      case 'CALL_VINTO':
        return handleCallVinto(state, action);

      case 'EXECUTE_QUEEN_SWAP':
        return handleExecuteQueenSwap(state, action);

      case 'SKIP_QUEEN_SWAP':
        return handleSkipQueenSwap(state, action);

      case 'DECLARE_KING_ACTION':
        return handleDeclareKingAction(state, action);

      case 'PARTICIPATE_IN_TOSS_IN':
        return handleParticipateInTossIn(state, action);

      case 'PLAYER_TOSS_IN_FINISHED':
        return handlePlayerTossInFinished(state, action);

      case 'FINISH_TOSS_IN_PERIOD':
        return handleFinishTossInPeriod(state, action);

      case 'SET_COALITION_LEADER':
        return handleSetCoalitionLeader(state, action);

      case 'PROCESS_AI_TURN':
        return handleProcessAITurn(state, action);

      case 'PEEK_SETUP_CARD':
        return handlePeekSetupCard(state, action);

      case 'FINISH_SETUP':
        return handleFinishSetup(state, action);

      case 'UPDATE_DIFFICULTY':
        return handleUpdateDifficulty(state, action);

      case 'SET_NEXT_DRAW_CARD':
        return handleSetNextDrawCard(state, action);

      default:
        throw new NeverError(action);
    }
  }
}

// engine/GameEngine.ts
// Core game engine - pure reducer that transforms state via actions

import { logger } from '@vinto/shapes';
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
import { GameAction, GameState, NeverError } from '@vinto/shapes';
import { handleExecuteJackSwap } from './cases/execute-jack-swap';
import { handleSkipJackSwap } from './cases/skip-jack-swap';
import { advanceTurnAfterTossIn } from './utils/toss-in-utils';

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
      logger.warn(`Invalid action ${action.type}: ${validation.reason}`, {
        actionType: action.type,
        reason: validation.reason,
        currentPhase: state.phase,
        currentSubPhase: state.subPhase,
      });
      return state; // Return unchanged state for invalid actions
    }

    console.log(`[GameEngine] Processing action: ${action.type}`);
    // Route to specific handler based on action type
    let newState: GameState;
    switch (action.type) {
      case 'DRAW_CARD':
        newState = handleDrawCard(state, action);
        break;

      case 'SWAP_CARD':
        newState = handleSwapCard(state, action);
        break;

      case 'DISCARD_CARD':
        newState = handleDiscardCard(state, action);
        break;

      case 'ADVANCE_TURN':
        newState = handleAdvanceTurn(state, action);
        break;

      case 'PLAY_DISCARD':
        newState = handlePlayDiscard(state, action);
        break;

      case 'USE_CARD_ACTION':
        newState = handleUseCardAction(state, action);
        break;

      case 'SELECT_ACTION_TARGET':
        newState = handleSelectActionTarget(state, action);
        break;

      case 'CONFIRM_PEEK':
        newState = handleConfirmPeek(state, action);
        break;

      case 'CALL_VINTO':
        newState = handleCallVinto(state, action);
        break;

      case 'EXECUTE_JACK_SWAP':
        newState = handleExecuteJackSwap(state, action);
        break;

      case 'SKIP_JACK_SWAP':
        newState = handleSkipJackSwap(state, action);
        break;

      case 'EXECUTE_QUEEN_SWAP':
        newState = handleExecuteQueenSwap(state, action);
        break;

      case 'SKIP_QUEEN_SWAP':
        newState = handleSkipQueenSwap(state, action);
        break;

      case 'DECLARE_KING_ACTION':
        newState = handleDeclareKingAction(state, action);
        break;

      case 'PARTICIPATE_IN_TOSS_IN':
        newState = handleParticipateInTossIn(state, action);
        break;

      case 'PLAYER_TOSS_IN_FINISHED':
        newState = handlePlayerTossInFinished(state, action);
        break;

      case 'FINISH_TOSS_IN_PERIOD':
        newState = handleFinishTossInPeriod(state, action);
        break;

      case 'SET_COALITION_LEADER':
        newState = handleSetCoalitionLeader(state, action);
        break;

      case 'PROCESS_AI_TURN':
        newState = handleProcessAITurn(state, action);
        break;

      case 'PEEK_SETUP_CARD':
        newState = handlePeekSetupCard(state, action);
        break;

      case 'FINISH_SETUP':
        newState = handleFinishSetup(state, action);
        break;

      case 'UPDATE_DIFFICULTY':
        newState = handleUpdateDifficulty(state, action);
        break;

      case 'SET_NEXT_DRAW_CARD':
        newState = handleSetNextDrawCard(state, action);
        break;

      default:
        throw new NeverError(action);
    }

    // POST-ACTION PROCESSING
    // Auto-advance turn if toss-in queue is empty and all players are ready
    if (
      newState.activeTossIn &&
      newState.activeTossIn.queuedActions.length === 0 &&
      newState.pendingAction === null &&
      newState.activeTossIn.playersReadyForNextTurn.length === newState.players.length
    ) {
      // All queued actions have been processed
      // Advance turn automatically
      advanceTurnAfterTossIn(newState, `GameEngine.reduce(${action.type})`);
    }

    return newState;
  }
}

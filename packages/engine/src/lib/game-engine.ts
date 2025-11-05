// engine/GameEngine.ts
// Core game engine - pure reducer that transforms state via actions

import { actionValidator } from './action-validator';
import { handleSwapCard } from './cases/swap-card';
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
import { handleUpdateBotVersion } from './cases/update-bot-version';
import { handleSetNextDrawCard } from './cases/set-next-draw-card';
import { handleSwapHandWithDeck } from './cases/swap-hand-with-deck';
import { GameAction, GameState, NeverError } from '@vinto/shapes';
import { handleExecuteJackSwap } from './cases/execute-jack-swap';
import { handleSkipJackSwap } from './cases/skip-jack-swap';
import { advanceTurnAfterTossIn } from './utils/toss-in-utils';
import { handleSkipPeek } from './cases/skip-peek';
import { handleEmpty } from './cases/empty';

type GameEngineReduceResult =
  | {
      success: true;
      state: GameState;
    }
  | {
      success: false;
      state: GameState;
      reason: string;
    };
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
  static reduce(state: GameState, action: GameAction): GameEngineReduceResult {
    // Validate action is legal in current state
    const validation = actionValidator(state, action);
    if (!validation.valid) {
      return { success: false, state, reason: validation.reason }; // Return unchanged state for invalid actions
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

      case 'SKIP_PEEK':
        newState = handleSkipPeek(state, action);
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

      case 'UPDATE_BOT_VERSION':
        newState = handleUpdateBotVersion(state, action);
        break;

      case 'SET_NEXT_DRAW_CARD':
        newState = handleSetNextDrawCard(state, action);
        break;

      case 'SWAP_HAND_WITH_DECK':
        newState = handleSwapHandWithDeck(state, action);
        break;

      case 'EMPTY':
        newState = handleEmpty(state, action);
        break;

      default:
        throw new NeverError(action);
    }

    if (newState === state) {
      return {
        success: false,
        state,
        reason: `Action handler for ${action.type} did not modify state`,
      };
    }

    // POST-ACTION PROCESSING
    // Auto-advance turn if toss-in queue is empty and all players are ready
    if (
      newState.activeTossIn &&
      newState.activeTossIn.queuedActions.length === 0 &&
      newState.pendingAction === null &&
      newState.activeTossIn.playersReadyForNextTurn.length ===
        newState.players.length &&
      action.type !== 'EMPTY' // skip for empty action
    ) {
      // All queued actions have been processed
      // Advance turn automatically
      advanceTurnAfterTossIn(newState, `GameEngine.reduce(${action.type})`);
    }

    return { success: true, state: newState };
  }
}

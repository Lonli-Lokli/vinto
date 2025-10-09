/* eslint-disable @typescript-eslint/no-unused-vars */
// engine/GameEngine.ts
// Core game engine - pure reducer that transforms state via actions

import copy from 'fast-copy';
import {
  GameState,
  GameAction,
  DrawCardAction,
  SwapCardAction,
  DiscardCardAction,
  AdvanceTurnAction,
  TakeDiscardAction,
  UseCardActionAction,
  SelectActionTargetAction,
  ConfirmPeekAction,
  CallVintoAction,
  ExecuteQueenSwapAction,
  SkipQueenSwapAction,
  DeclareKingActionAction,
  ParticipateInTossInAction,
  FinishTossInPeriodAction,
  SetCoalitionLeaderAction,
  ProcessAITurnAction,
  PeekSetupCardAction,
  FinishSetupAction,
} from './types';
import { NeverError } from '@/app/shapes';

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
    const validation = this.validateAction(state, action);
    if (!validation.valid) {
      console.warn(`Invalid action ${action.type}: ${validation.reason}`);
      return state; // Return unchanged state for invalid actions
    }

    // Route to specific handler based on action type
    switch (action.type) {
      case 'DRAW_CARD':
        return this.handleDrawCard(state, action);

      case 'SWAP_CARD':
        return this.handleSwapCard(state, action);

      case 'DISCARD_CARD':
        return this.handleDiscardCard(state, action);

      case 'ADVANCE_TURN':
        return this.handleAdvanceTurn(state, action);

      case 'TAKE_DISCARD':
        return this.handleTakeDiscard(state, action);

      case 'USE_CARD_ACTION':
        return this.handleUseCardAction(state, action);

      case 'SELECT_ACTION_TARGET':
        return this.handleSelectActionTarget(state, action);

      case 'CONFIRM_PEEK':
        return this.handleConfirmPeek(state, action);

      case 'CALL_VINTO':
        return this.handleCallVinto(state, action);

      case 'EXECUTE_QUEEN_SWAP':
        return this.handleExecuteQueenSwap(state, action);

      case 'SKIP_QUEEN_SWAP':
        return this.handleSkipQueenSwap(state, action);

      case 'DECLARE_KING_ACTION':
        return this.handleDeclareKingAction(state, action);

      case 'PARTICIPATE_IN_TOSS_IN':
        return this.handleParticipateInTossIn(state, action);

      case 'FINISH_TOSS_IN_PERIOD':
        return this.handleFinishTossInPeriod(state, action);

      case 'SET_COALITION_LEADER':
        return this.handleSetCoalitionLeader(state, action);

      case 'PROCESS_AI_TURN':
        return this.handleProcessAITurn(state, action);

      case 'PEEK_SETUP_CARD':
        return this.handlePeekSetupCard(state, action);

      case 'FINISH_SETUP':
        return this.handleFinishSetup(state, action);

      default:
        throw new NeverError(action);
    }
  }

  /**
   * Validates if an action is legal in the current state
   */
  private static validateAction(
    state: GameState,
    action: GameAction
  ): { valid: boolean; reason?: string } {
    // Common validations
    switch (action.type) {
      case 'DRAW_CARD': {
        const { playerId } = action.payload;

        // Must be player's turn
        const currentPlayer = state.players[state.currentPlayerIndex];
        if (currentPlayer.id !== playerId) {
          return { valid: false, reason: 'Not player turn' };
        }

        // Must be in idle phase
        if (state.subPhase !== 'idle' && state.subPhase !== 'ai_thinking') {
          return {
            valid: false,
            reason: `Cannot draw in phase ${state.subPhase}`,
          };
        }

        // Must have cards in draw pile
        if (state.drawPile.length === 0) {
          return { valid: false, reason: 'Draw pile is empty' };
        }

        return { valid: true };
      }

      case 'SWAP_CARD': {
        const { playerId, position } = action.payload;

        // Must be player's turn
        const currentPlayer = state.players[state.currentPlayerIndex];
        if (currentPlayer.id !== playerId) {
          return { valid: false, reason: 'Not player turn' };
        }

        // Must be in choosing phase (after drawing a card)
        if (state.subPhase !== 'choosing') {
          return {
            valid: false,
            reason: `Cannot swap in phase ${state.subPhase}`,
          };
        }

        // Must have a pending action
        if (!state.pendingAction) {
          return { valid: false, reason: 'No pending action to swap' };
        }

        // Position must be valid (0-3 for 4 cards in hand)
        if (position < 0 || position >= currentPlayer.cards.length) {
          return { valid: false, reason: `Invalid position ${position}` };
        }

        return { valid: true };
      }

      case 'DISCARD_CARD': {
        const { playerId } = action.payload;

        // Must be player's turn
        const currentPlayer = state.players[state.currentPlayerIndex];
        if (currentPlayer.id !== playerId) {
          return { valid: false, reason: 'Not player turn' };
        }

        // Must be in selecting phase (after swapping)
        if (state.subPhase !== 'selecting') {
          return {
            valid: false,
            reason: `Cannot discard in phase ${state.subPhase}`,
          };
        }

        return { valid: true };
      }

      case 'ADVANCE_TURN': {
        // Must be in idle phase (turn completed)
        if (state.subPhase !== 'idle') {
          return {
            valid: false,
            reason: `Cannot advance turn in phase ${state.subPhase}`,
          };
        }

        return { valid: true };
      }

      case 'TAKE_DISCARD': {
        const { playerId } = action.payload;

        // Must be player's turn
        const currentPlayer = state.players[state.currentPlayerIndex];
        if (currentPlayer.id !== playerId) {
          return { valid: false, reason: 'Not player turn' };
        }

        // Must be in idle or ai_thinking phase
        if (state.subPhase !== 'idle' && state.subPhase !== 'ai_thinking') {
          return {
            valid: false,
            reason: `Cannot take discard in phase ${state.subPhase}`,
          };
        }

        // Must have cards in discard pile
        if (state.discardPile.length === 0) {
          return { valid: false, reason: 'Discard pile is empty' };
        }

        return { valid: true };
      }

      case 'USE_CARD_ACTION': {
        const { playerId } = action.payload;

        // Must be player's turn
        const currentPlayer = state.players[state.currentPlayerIndex];
        if (currentPlayer.id !== playerId) {
          return { valid: false, reason: 'Not player turn' };
        }

        // Must be in selecting phase (after swapping, deciding to use action vs discard)
        if (state.subPhase !== 'selecting') {
          return {
            valid: false,
            reason: `Cannot use card action in phase ${state.subPhase}`,
          };
        }

        // Must have a pending action with a card
        if (!state.pendingAction?.card) {
          return { valid: false, reason: 'No card to use action from' };
        }

        return { valid: true };
      }

      case 'SELECT_ACTION_TARGET': {
        const { playerId, targetPlayerId, position } = action.payload;

        // Must be player's turn
        const currentPlayer = state.players[state.currentPlayerIndex];
        if (currentPlayer.id !== playerId) {
          return { valid: false, reason: 'Not player turn' };
        }

        // Must be in awaiting_action phase (after USE_CARD_ACTION)
        if (state.subPhase !== 'awaiting_action') {
          return {
            valid: false,
            reason: `Cannot select target in phase ${state.subPhase}`,
          };
        }

        // Must have a pending action
        if (!state.pendingAction) {
          return { valid: false, reason: 'No pending action to add target to' };
        }

        // Find target player
        const targetPlayer = state.players.find((p) => p.id === targetPlayerId);
        if (!targetPlayer) {
          return { valid: false, reason: 'Target player not found' };
        }

        // Position must be valid for target player
        if (position < 0 || position >= targetPlayer.cards.length) {
          return {
            valid: false,
            reason: `Invalid position ${position} for target player`,
          };
        }

        return { valid: true };
      }

      case 'CONFIRM_PEEK': {
        const { playerId } = action.payload;

        // Must be player's turn
        const currentPlayer = state.players[state.currentPlayerIndex];
        if (currentPlayer.id !== playerId) {
          return { valid: false, reason: 'Not player turn' };
        }

        // Must be in awaiting_action phase (after peeking at card)
        // Note: In a full implementation, we'd check for 'peeking' subphase
        // For now, we accept awaiting_action as that's where peek actions happen
        if (state.subPhase !== 'awaiting_action') {
          return {
            valid: false,
            reason: `Cannot confirm peek in phase ${state.subPhase}`,
          };
        }

        return { valid: true };
      }

      case 'CALL_VINTO': {
        const { playerId } = action.payload;

        // Must be player's turn
        const currentPlayer = state.players[state.currentPlayerIndex];
        if (currentPlayer.id !== playerId) {
          return { valid: false, reason: 'Not player turn' };
        }

        // Player must have low score (sum of cards <= threshold, e.g., 5)
        // For MVP: just check they haven't called vinto already
        if (state.vintoCallerId !== null) {
          return { valid: false, reason: 'Vinto already called' };
        }

        return { valid: true };
      }

      case 'EXECUTE_QUEEN_SWAP':
      case 'SKIP_QUEEN_SWAP': {
        const { playerId } = action.payload;

        // Must be player's turn
        const currentPlayer = state.players[state.currentPlayerIndex];
        if (currentPlayer.id !== playerId) {
          return { valid: false, reason: 'Not player turn' };
        }

        // Must be in awaiting_action phase (after selecting peek targets)
        if (state.subPhase !== 'awaiting_action') {
          return {
            valid: false,
            reason: `Cannot execute Queen action in phase ${state.subPhase}`,
          };
        }

        // Must have a pending action
        if (!state.pendingAction) {
          return { valid: false, reason: 'No pending action' };
        }

        // Must have exactly 2 targets (Queen peeks at 2 cards)
        if (state.pendingAction.targets.length !== 2) {
          return {
            valid: false,
            reason: `Queen action requires 2 targets, got ${state.pendingAction.targets.length}`,
          };
        }

        return { valid: true };
      }

      case 'DECLARE_KING_ACTION': {
        const { playerId } = action.payload;

        // Must be player's turn
        const currentPlayer = state.players[state.currentPlayerIndex];
        if (currentPlayer.id !== playerId) {
          return { valid: false, reason: 'Not player turn' };
        }

        // Must be in awaiting_action phase (after using King card)
        if (state.subPhase !== 'awaiting_action') {
          return {
            valid: false,
            reason: `Cannot declare King action in phase ${state.subPhase}`,
          };
        }

        // Must have a pending action with King card
        if (!state.pendingAction?.card) {
          return { valid: false, reason: 'No pending King card' };
        }

        if (state.pendingAction.card.rank !== 'K') {
          return { valid: false, reason: 'Pending card is not a King' };
        }

        return { valid: true };
      }

      // TODO: Add validation for other actions
      default:
        return { valid: true }; // Permissive for now
    }
  }

  /**
   * TAKE_DISCARD Handler
   *
   * Flow:
   * 1. Transition to 'drawing' phase (for animation, same as draw)
   * 2. Remove top card from discard pile
   * 3. Create pending action with taken card
   * 4. Transition to 'choosing' phase
   *
   * Note: Very similar to DRAW_CARD, but takes from discard pile
   */
  private static handleTakeDiscard(
    state: GameState,
    action: TakeDiscardAction
  ): GameState {
    const { playerId } = action.payload;

    // Create new state (deep copy for safety)
    const newState = copy(state);

    // Transition to drawing phase (skip if already in ai_thinking for bots)
    if (newState.subPhase !== 'ai_thinking') {
      newState.subPhase = 'drawing';
    }

    // Take the top card from discard pile
    const takenCard = newState.discardPile.pop();
    if (!takenCard) {
      // Should never happen due to validation, but be defensive
      return state;
    }

    // Create pending action
    newState.pendingAction = {
      card: takenCard,
      playerId,
      actionPhase: 'choosing-action',
      targets: [],
    };

    // Transition to choosing phase
    newState.subPhase = 'choosing';

    return newState;
  }

  /**
   * DRAW_CARD Handler
   *
   * Flow:
   * 1. Transition to 'drawing' phase (for animation)
   * 2. Remove top card from draw pile
   * 3. Create pending action with drawn card
   * 4. Transition to 'choosing' phase
   */
  private static handleDrawCard(
    state: GameState,
    action: DrawCardAction
  ): GameState {
    const { playerId } = action.payload;

    // Create new state (deep copy for safety)
    const newState = copy(state);

    // Transition to drawing phase (skip if already in ai_thinking for bots)
    if (newState.subPhase !== 'ai_thinking') {
      newState.subPhase = 'drawing';
    }

    // Draw the top card
    const drawnCard = newState.drawPile.shift();
    if (!drawnCard) {
      // Should never happen due to validation, but be defensive
      return state;
    }

    // Create pending action
    newState.pendingAction = {
      card: drawnCard,
      playerId,
      actionPhase: 'choosing-action',
      targets: [],
    };

    // Transition to choosing phase
    // (In real implementation, this would happen after animation completes)
    newState.subPhase = 'choosing';

    return newState;
  }

  /**
   * ADVANCE_TURN Handler
   *
   * Flow:
   * 1. Move to next player (circular)
   * 2. Check if next player is a bot
   * 3. Transition to appropriate phase (idle for humans, ai_thinking for bots)
   */
  private static handleAdvanceTurn(
    state: GameState,
    action: AdvanceTurnAction
  ): GameState {
    // Create new state (deep copy for safety)
    const newState = copy(state);

    // Advance to next player (circular)
    newState.currentPlayerIndex =
      (newState.currentPlayerIndex + 1) % newState.players.length;

    // Get the new current player
    const nextPlayer = newState.players[newState.currentPlayerIndex];

    // Transition to appropriate phase based on player type
    if (nextPlayer.isBot) {
      newState.subPhase = 'ai_thinking';
    } else {
      newState.subPhase = 'idle';
    }

    return newState;
  }

  /**
   * Helper: Get current player
   * (Currently unused but may be useful for future refactoring)
   */
  // private static getCurrentPlayer(state: GameState) {
  //   return state.players[state.currentPlayerIndex];
  // }

  /**
   * Helper: Find player by ID
   * (Currently unused but may be useful for future refactoring)
   */
  // private static findPlayer(state: GameState, playerId: string) {
  //   return state.players.find((p) => p.id === playerId);
  // }

  /**
   * CALL_VINTO Handler
   *
   * Flow:
   * 1. Player declares "Vinto!" when they have low score
   * 2. Set vintoCallerId to track who called it
   * 3. Set finalTurnTriggered to true (last round begins)
   * 4. Game continues normally but will end after all players get one more turn
   *
   * Note: Vinto is called to declare you're close to winning
   */
  private static handleCallVinto(
    state: GameState,
    action: CallVintoAction
  ): GameState {
    const { playerId } = action.payload;

    // Create new state (deep copy for safety)
    const newState = copy(state);

    // Set vinto caller
    newState.vintoCallerId = playerId;

    // Trigger final turn (everyone gets one more turn)
    newState.finalTurnTriggered = true;

    // Find the player and mark them as vinto caller
    const player = newState.players.find((p) => p.id === playerId);
    if (player) {
      player.isVintoCaller = true;
    }

    // Game continues in same phase (vinto is declared, turn continues)
    return newState;
  }

  /**
   * EXECUTE_QUEEN_SWAP Handler
   *
   * Flow:
   * 1. Player has used a Queen card action
   * 2. Player has selected 2 cards to peek at (stored in pendingAction.targets)
   * 3. Player chooses to swap those two cards (Queen's special ability)
   * 4. Swap the two target cards between players/positions
   * 5. Move Queen card to discard pile
   * 6. Complete turn (increment turn count, transition to idle)
   *
   * Note: Queen allows peeking at 2 cards then optionally swapping them
   * This handler executes the swap; SKIP_QUEEN_SWAP skips it
   */
  private static handleExecuteQueenSwap(
    state: GameState,
    _action: ExecuteQueenSwapAction
  ): GameState {

    // Create new state (deep copy for safety)
    const newState = copy(state);

    // Get the two targets from pending action
    const targets = newState.pendingAction!.targets;
    const [target1, target2] = targets;

    // Find the two target players
    const player1 = newState.players.find((p) => p.id === target1.playerId);
    const player2 = newState.players.find((p) => p.id === target2.playerId);

    if (!player1 || !player2) {
      // Should never happen due to validation
      return state;
    }

    // Swap the two cards
    const card1 = player1.cards[target1.position];
    const card2 = player2.cards[target2.position];

    player1.cards[target1.position] = card2;
    player2.cards[target2.position] = card1;

    // Move Queen card to discard pile
    if (newState.pendingAction?.card) {
      newState.discardPile.push(newState.pendingAction.card);
    }

    // Clear pending action
    newState.pendingAction = null;

    // Increment turn count
    newState.turnCount += 1;

    // Transition to idle (turn complete)
    newState.subPhase = 'idle';

    return newState;
  }

  /**
   * SKIP_QUEEN_SWAP Handler
   *
   * Flow:
   * 1. Player has used a Queen card action
   * 2. Player has selected 2 cards to peek at (stored in pendingAction.targets)
   * 3. Player chooses NOT to swap those two cards (declined Queen's ability)
   * 4. Move Queen card to discard pile (without swapping)
   * 5. Complete turn (increment turn count, transition to idle)
   *
   * Note: Same as EXECUTE_QUEEN_SWAP but skips the actual swap
   */
  private static handleSkipQueenSwap(
    state: GameState,
    _action: SkipQueenSwapAction
  ): GameState {

    // Create new state (deep copy for safety)
    const newState = copy(state);

    // Move Queen card to discard pile (skip the swap)
    if (newState.pendingAction?.card) {
      newState.discardPile.push(newState.pendingAction.card);
    }

    // Clear pending action
    newState.pendingAction = null;

    // Increment turn count
    newState.turnCount += 1;

    // Transition to idle (turn complete)
    newState.subPhase = 'idle';

    return newState;
  }

  /**
   * DECLARE_KING_ACTION Handler
   *
   * Flow:
   * 1. Player has used a King card (via USE_CARD_ACTION)
   * 2. Player declares a rank (A, 7, 8, 9, 10, J, Q - not K)
   * 3. A toss-in is triggered for that rank
   * 4. All other players must discard any cards of the declared rank
   * 5. Move King card to discard pile
   * 6. Complete turn (increment turn count, transition to idle)
   *
   * Note: King's ability triggers a toss-in, forcing others to discard matching ranks
   * In this MVP, we'll just complete the turn. Full toss-in logic will be in PARTICIPATE_IN_TOSS_IN
   */
  private static handleDeclareKingAction(
    state: GameState,
    action: DeclareKingActionAction
  ): GameState {
    const { playerId, declaredRank } = action.payload;

    // Create new state (deep copy for safety)
    const newState = copy(state);

    // Move King card to discard pile
    if (newState.pendingAction?.card) {
      newState.discardPile.push(newState.pendingAction.card);
    }

    // Trigger toss-in for the declared rank
    // In a full implementation, this would set up activeTossIn
    // For MVP: we'll create the toss-in structure but not fully process it
    newState.activeTossIn = {
      rank: declaredRank, // Note: property is 'rank' not 'declaredRank'
      initiatorId: playerId,
      participants: [], // Other players will participate via PARTICIPATE_IN_TOSS_IN
      queuedActions: [],
      waitingForInput: true,
    };

    // Clear pending action
    newState.pendingAction = null;

    // Increment turn count
    newState.turnCount += 1;

    // Transition to idle (turn complete)
    // In full game, might transition to 'toss_in' phase
    newState.subPhase = 'idle';

    return newState;
  }

  /**
   * CONFIRM_PEEK Handler
   *
   * Flow:
   * 1. Player has peeked at a card (7, 8, 9, or 10 action)
   * 2. Confirming they've seen it and are ready to continue
   * 3. Move card to discard pile
   * 4. Clear pending action
   * 5. Increment turn and transition to idle
   *
   * Note: The actual peek happens in SELECT_ACTION_TARGET
   * This just confirms and completes the turn
   */
  private static handleConfirmPeek(
    state: GameState,
    _action: ConfirmPeekAction
  ): GameState {

    // Create new state (deep copy for safety)
    const newState = copy(state);

    // Move action card to discard pile
    if (newState.pendingAction?.card) {
      newState.discardPile.push(newState.pendingAction.card);
    }

    // Clear pending action
    newState.pendingAction = null;

    // Increment turn count
    newState.turnCount += 1;

    // Transition to idle (turn complete)
    newState.subPhase = 'idle';

    return newState;
  }

  /**
   * SELECT_ACTION_TARGET Handler
   *
   * Flow:
   * 1. Player has chosen to use a card action (via USE_CARD_ACTION)
   * 2. Now selecting which card/player to target
   * 3. Add target to pending action's targets array
   * 4. Execute card-specific logic based on card rank:
   *    - Jack (J): Blind swap 2 cards (needs 2 targets)
   *    - 7/8/9/10: Peek at card (1 target, confirm needed)
   *    - Ace (A): Force opponent to draw (1 target)
   *
   * Note: Queen (Q) and King (K) have their own dedicated handlers
   */
  private static handleSelectActionTarget(
    state: GameState,
    action: SelectActionTargetAction
  ): GameState {
    const { targetPlayerId, position } = action.payload;

    // Create new state (deep copy for safety)
    const newState = copy(state);

    // Add target to pending action
    if (newState.pendingAction) {
      newState.pendingAction.targets.push({
        playerId: targetPlayerId,
        position,
      });
    }

    const cardRank = newState.pendingAction?.card?.rank;
    const targets = newState.pendingAction?.targets || [];

    // Handle card-specific logic
    if (cardRank === 'J') {
      // Jack: Blind swap 2 cards
      if (targets.length === 2) {
        // We have both targets, execute the swap
        const [target1, target2] = targets;

        // Find the two target players
        const player1 = newState.players.find((p) => p.id === target1.playerId);
        const player2 = newState.players.find((p) => p.id === target2.playerId);

        if (player1 && player2) {
          // Swap the two cards (blind swap - no peeking)
          const card1 = player1.cards[target1.position];
          const card2 = player2.cards[target2.position];

          player1.cards[target1.position] = card2;
          player2.cards[target2.position] = card1;
        }

        // Move Jack card to discard pile
        if (newState.pendingAction?.card) {
          newState.discardPile.push(newState.pendingAction.card);
        }

        // Clear pending action
        newState.pendingAction = null;

        // Increment turn count
        newState.turnCount += 1;

        // Transition to idle (turn complete)
        newState.subPhase = 'idle';
      }
      // If we only have 1 target, stay in awaiting_action phase for second target
      // No state changes needed, just return current state with updated targets
    } else if (cardRank === '7' || cardRank === '8' || cardRank === '9' || cardRank === '10') {
      // Peek cards (7/8 peek own, 9/10 peek opponent)
      // The peek action is handled by UI (showing the card)
      // This handler just tracks the target
      // Player will then call CONFIRM_PEEK to complete the action
      // Stay in awaiting_action phase - no further action needed here
    } else if (cardRank === 'A') {
      // Ace: Force opponent to draw a penalty card
      if (targets.length === 1) {
        const target = targets[0];
        const targetPlayer = newState.players.find((p) => p.id === target.playerId);

        if (targetPlayer && newState.drawPile.length > 0) {
          // Draw a card from the pile
          const penaltyCard = newState.drawPile.shift();
          if (penaltyCard) {
            // Add penalty card to target player's hand
            targetPlayer.cards.push(penaltyCard);
          }
        }

        // Move Ace card to discard pile
        if (newState.pendingAction?.card) {
          newState.discardPile.push(newState.pendingAction.card);
        }

        // Clear pending action
        newState.pendingAction = null;

        // Increment turn count
        newState.turnCount += 1;

        // Transition to idle (turn complete)
        newState.subPhase = 'idle';
      }
    } else {
      // Default: Unknown card type or not implemented yet
      // Move card to discard, complete turn
      if (newState.pendingAction?.card) {
        newState.discardPile.push(newState.pendingAction.card);
      }

      // Clear pending action
      newState.pendingAction = null;

      // Increment turn count
      newState.turnCount += 1;

      // Transition to idle (turn complete)
      newState.subPhase = 'idle';
    }

    return newState;
  }

  /**
   * USE_CARD_ACTION Handler
   *
   * Flow:
   * 1. Player has drawn/swapped and now chooses to use the card's action
   * 2. Update pending action phase to indicate we're using the action
   * 3. Transition to 'awaiting_action' phase
   * 4. Next: Player will SELECT_ACTION_TARGET (if card needs target)
   *
   * Note: The actual card effect execution happens later after target selection
   */
  private static handleUseCardAction(
    state: GameState,
    _action: UseCardActionAction
  ): GameState {
    // Create new state (deep copy for safety)
    const newState = copy(state);

    // Update pending action to reflect we're now using the card's action
    if (newState.pendingAction) {
      newState.pendingAction.actionPhase = 'selecting-target';
    }

    // Transition to awaiting_action phase (ready for target selection)
    newState.subPhase = 'awaiting_action';

    return newState;
  }

  /**
   * DISCARD_CARD Handler
   *
   * Flow:
   * 1. Player has drawn a card and swapped it into their hand
   * 2. The card they removed is in pendingAction (from SWAP_CARD)
   * 3. Add that card to discard pile
   * 4. Clear pendingAction
   * 5. Transition to idle, ready to advance turn
   */
  private static handleDiscardCard(
    state: GameState,
    _action: DiscardCardAction
  ): GameState {
    // Create new state (deep copy for safety)
    const newState = copy(state);

    // The card to discard is in pendingAction (set by SWAP_CARD)
    if (newState.pendingAction?.card) {
      newState.discardPile.push(newState.pendingAction.card);
    }

    // Clear pending action
    newState.pendingAction = null;

    // Transition to idle - turn is complete
    newState.subPhase = 'idle';

    // Increment turn count (turn completed)
    newState.turnCount += 1;

    return newState;
  }

  /**
   * SWAP_CARD Handler
   *
   * Flow:
   * 1. Get the card from pendingAction
   * 2. Swap it with the card at the specified position in player's hand
   * 3. Update player's known cards if declaration was made
   * 4. Clear pending action
   * 5. Transition to idle (ready for next action: discard or use card action)
   */
  private static handleSwapCard(
    state: GameState,
    action: SwapCardAction
  ): GameState {
    const { playerId, position, declaredRank } = action.payload;

    // Create new state (deep copy for safety)
    const newState = copy(state);

    // Get the pending card (from draw)
    const pendingCard = newState.pendingAction!.card;

    // Find the player
    const playerIndex = newState.players.findIndex((p) => p.id === playerId);
    if (playerIndex === -1) {
      return state; // Should never happen due to validation
    }

    const player = newState.players[playerIndex];

    // Swap: take card from hand, put pending card in its place
    const cardFromHand = player.cards[position];
    player.cards[position] = pendingCard;

    // TODO: verify cases with J & Q for cases when he is swapping known or unknown cards from players
    // If player declared a rank, track it in known positions
    if (declaredRank) {
      // Update known card positions
      const existingKnown = player.knownCardPositions.includes(position);
      if (!existingKnown) {
        player.knownCardPositions.push(position);
      }
    }

    // Update pending action: the removed card is now what we're deciding about
    // Player will either discard it or use its action
    newState.pendingAction = {
      card: cardFromHand,
      playerId,
      actionPhase: 'choosing-action',
      targets: [],
    };

    // Transition to idle (player must now choose: discard or use action)
    // Actually, we need a different phase here - player is now choosing what to do with drawn card
    // Let's use 'selecting' to indicate they're deciding between discard/use-action
    newState.subPhase = 'selecting';

    return newState;
  }

  /**
   * PARTICIPATE_IN_TOSS_IN Handler
   *
   * Flow:
   * 1. A toss-in has been triggered (via DECLARE_KING_ACTION)
   * 2. Other players participate by discarding matching cards
   * 3. Add player to participants list
   * 4. Move matching card from hand to discard pile
   *
   * Note: Toss-in is when a King declares a rank, forcing others to discard matching cards
   */
  private static handleParticipateInTossIn(
    state: GameState,
    action: ParticipateInTossInAction
  ): GameState {
    const { playerId, position } = action.payload;

    // Create new state (deep copy for safety)
    const newState = copy(state);

    // Find the player
    const player = newState.players.find((p) => p.id === playerId);
    if (!player || !newState.activeTossIn) {
      return state;
    }

    // Get the card at the specified position
    const card = player.cards[position];
    if (!card) {
      return state;
    }

    // Verify card matches declared rank (optional validation)
    if (card.rank !== newState.activeTossIn.rank) {
      console.warn(
        `Card rank ${card.rank} doesn't match toss-in rank ${newState.activeTossIn.rank}`
      );
    }

    // Add player to participants
    if (!newState.activeTossIn.participants.includes(playerId)) {
      newState.activeTossIn.participants.push(playerId);
    }

    // Move card to discard pile
    player.cards.splice(position, 1);
    newState.discardPile.push(card);

    return newState;
  }

  /**
   * FINISH_TOSS_IN_PERIOD Handler
   *
   * Flow:
   * 1. Toss-in period is complete
   * 2. All players have had a chance to participate
   * 3. Clear the active toss-in
   * 4. Continue normal game flow
   */
  private static handleFinishTossInPeriod(
    state: GameState,
    _action: FinishTossInPeriodAction
  ): GameState {

    // Create new state (deep copy for safety)
    const newState = copy(state);

    // Clear the active toss-in
    newState.activeTossIn = null;

    // Game continues in same phase
    return newState;
  }

  /**
   * SET_COALITION_LEADER Handler
   *
   * Flow:
   * 1. Set the coalition leader for the current round
   * 2. Coalition leader is the player with the lowest score
   * 3. Other players can team up with them
   */
  private static handleSetCoalitionLeader(
    state: GameState,
    action: SetCoalitionLeaderAction
  ): GameState {
    const { leaderId } = action.payload;

    // Create new state (deep copy for safety)
    const newState = copy(state);

    // Set the coalition leader
    newState.coalitionLeaderId = leaderId;

    return newState;
  }

  /**
   * PROCESS_AI_TURN Handler
   *
   * Flow:
   * 1. Bot's turn is being processed
   * 2. This action is a placeholder for AI decision-making
   * 3. In a full implementation, this would contain bot logic
   * 4. For now, it's a no-op that maintains the state
   *
   * Note: Actual bot actions (DRAW_CARD, SWAP_CARD, etc.) are dispatched separately
   */
  private static handleProcessAITurn(
    state: GameState,
    _action: ProcessAITurnAction
  ): GameState {

    // Create new state (deep copy for safety)
    const newState = copy(state);

    // In a full implementation, this would:
    // 1. Analyze current game state
    // 2. Make AI decision (draw/take discard, which card to swap, etc.)
    // 3. Dispatch appropriate actions
    //
    // For MVP: This is just a marker action
    // Actual bot moves are dispatched as regular actions (DRAW_CARD, etc.)

    return newState;
  }

  /**
   * PEEK_SETUP_CARD Handler
   *
   * Flow:
   * 1. During setup phase, player peeks at their own cards
   * 2. Update player's known card positions
   * 3. Track which cards the player has seen
   *
   * Note: In Vinto, players start by peeking at 2 of their 4 cards
   */
  private static handlePeekSetupCard(
    state: GameState,
    action: PeekSetupCardAction
  ): GameState {
    const { playerId, position } = action.payload;

    // Create new state (deep copy for safety)
    const newState = copy(state);

    // Find the player
    const player = newState.players.find((p) => p.id === playerId);
    if (!player) {
      return state;
    }

    // Get the card at position
    const card = player.cards[position];
    if (!card) {
      return state;
    }

    // Add to known card positions (player has peeked at this card)
    const existingKnown = player.knownCardPositions.includes(position);
    if (!existingKnown) {
      player.knownCardPositions.push(position);
    }

    return newState;
  }

  /**
   * FINISH_SETUP Handler
   *
   * Flow:
   * 1. Player has finished peeking at their setup cards
   * 2. Transition from 'setup' phase to 'playing' phase
   * 3. Game can now begin normally
   */
  private static handleFinishSetup(
    state: GameState,
    _action: FinishSetupAction
  ): GameState {

    // Create new state (deep copy for safety)
    const newState = copy(state);

    // Transition from setup to playing
    if (newState.phase === 'setup') {
      newState.phase = 'playing';
      newState.subPhase = 'idle';
    }

    return newState;
  }

  /**
   * Helper: Check if phase transition is valid
   * (For future use - will integrate with phase validation)
   */
  // private static canTransitionTo(
  //   state: GameState,
  //   newPhase: GameState['phase'],
  //   newSubPhase: GameState['subPhase']
  // ): boolean {
  //   // TODO: Implement full phase transition validation
  //   // For now, allow all transitions
  //   return true;
  // }
}

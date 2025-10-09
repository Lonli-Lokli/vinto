import { GameAction, GameState } from './types';

/**
 * Validates if an action is legal in the current state
 */
export function actionValidator(
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

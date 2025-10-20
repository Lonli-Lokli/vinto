import { GameAction, GameState, NeverError } from '@vinto/shapes';

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

      // Must be in choosing phase (after drawing card)
      if (state.subPhase !== 'choosing') {
        return {
          valid: false,
          reason: `Cannot swap in phase ${state.subPhase}`,
        };
      }

      // Must have a pending action
      if (!state.pendingAction) {
        return { valid: false, reason: 'No pending action' };
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

      // Must be in choosing phase (after drawing) or selecting phase (after swapping)
      // choosing: Discard drawn card directly without swapping
      // selecting: Discard after swapping into hand
      if (state.subPhase !== 'selecting' && state.subPhase !== 'choosing') {
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

    case 'PLAY_DISCARD': {
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

      // Must be in choosing or selecting phase (after drawing/swapping, deciding to use action)
      // choosing: After drawing card, before swapping
      // selecting: After swapping, deciding to use action vs discard
      if (state.subPhase !== 'selecting' && state.subPhase !== 'choosing') {
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

      // Check if processing toss-in action
      const isProcessingTossInAction =
        state.activeTossIn && state.activeTossIn.queuedActions.length > 0;

      // For toss-in actions, validate against pendingAction.playerId
      // For normal actions, validate against currentPlayerIndex
      if (isProcessingTossInAction) {
        if (!state.pendingAction || state.pendingAction.playerId !== playerId) {
          return { valid: false, reason: 'Not your toss-in action' };
        }
      } else {
        const currentPlayer = state.players[state.currentPlayerIndex];
        if (currentPlayer.id !== playerId) {
          return { valid: false, reason: 'Not player turn' };
        }
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

      // Check if processing toss-in action
      const isProcessingTossInAction =
        state.activeTossIn && state.activeTossIn.queuedActions.length > 0;

      // For toss-in actions, validate against pendingAction.playerId
      // For normal actions, validate against currentPlayerIndex
      if (isProcessingTossInAction) {
        if (!state.pendingAction || state.pendingAction.playerId !== playerId) {
          return { valid: false, reason: 'Not your toss-in action' };
        }
      } else {
        const currentPlayer = state.players[state.currentPlayerIndex];
        if (currentPlayer.id !== playerId) {
          return { valid: false, reason: 'Not player turn' };
        }
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

    case 'EXECUTE_JACK_SWAP':
    case 'SKIP_JACK_SWAP': {
      const { playerId } = action.payload;

      // Check if processing toss-in action
      const isProcessingTossInAction =
        state.activeTossIn && state.activeTossIn.queuedActions.length > 0;

      // For toss-in actions, validate against pendingAction.playerId
      // For normal actions, validate against currentPlayerIndex
      if (isProcessingTossInAction) {
        if (!state.pendingAction || state.pendingAction.playerId !== playerId) {
          return { valid: false, reason: 'Not your toss-in action' };
        }
      } else {
        const currentPlayer = state.players[state.currentPlayerIndex];
        if (currentPlayer.id !== playerId) {
          return { valid: false, reason: 'Not player turn' };
        }
      }

      // Must be in awaiting_action phase (after selecting peek targets)
      if (state.subPhase !== 'awaiting_action') {
        return {
          valid: false,
          reason: `Cannot execute Jack action in phase ${state.subPhase}`,
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
          reason: `Jack action requires 2 targets, got ${state.pendingAction.targets.length}`,
        };
      }

      return { valid: true };
    }

    case 'EXECUTE_QUEEN_SWAP':
    case 'SKIP_QUEEN_SWAP': {
      const { playerId } = action.payload;

      // Check if processing toss-in action
      const isProcessingTossInAction =
        state.activeTossIn && state.activeTossIn.queuedActions.length > 0;

      // For toss-in actions, validate against pendingAction.playerId
      // For normal actions, validate against currentPlayerIndex
      if (isProcessingTossInAction) {
        if (!state.pendingAction || state.pendingAction.playerId !== playerId) {
          return { valid: false, reason: 'Not your toss-in action' };
        }
      } else {
        const currentPlayer = state.players[state.currentPlayerIndex];
        if (currentPlayer.id !== playerId) {
          return { valid: false, reason: 'Not player turn' };
        }
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

    case 'SELECT_KING_CARD_TARGET': {
      const { playerId, targetPlayerId, position } = action.payload;

      // Check if processing toss-in action
      const isProcessingTossInAction =
        state.activeTossIn && state.activeTossIn.queuedActions.length > 0;

      // For toss-in actions, validate against pendingAction.playerId
      // For normal actions, validate against currentPlayerIndex
      if (isProcessingTossInAction) {
        if (!state.pendingAction || state.pendingAction.playerId !== playerId) {
          return { valid: false, reason: 'Not your toss-in action' };
        }
      } else {
        const currentPlayer = state.players[state.currentPlayerIndex];
        if (currentPlayer.id !== playerId) {
          return { valid: false, reason: 'Not player turn' };
        }
      }

      // Must be in awaiting_action phase (after using King card)
      if (state.subPhase !== 'awaiting_action') {
        return {
          valid: false,
          reason: `Cannot select King card target in phase ${state.subPhase}`,
        };
      }

      // Must have a pending action with King card
      if (!state.pendingAction?.card) {
        return { valid: false, reason: 'No pending King card' };
      }

      if (state.pendingAction.card.rank !== 'K') {
        return { valid: false, reason: 'Pending card is not a King' };
      }

      // Must be in selecting-king-card action phase
      if (state.pendingAction.actionPhase !== 'selecting-king-card') {
        return {
          valid: false,
          reason: `Cannot select card in action phase ${state.pendingAction.actionPhase}`,
        };
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

    case 'DECLARE_KING_ACTION': {
      const { playerId } = action.payload;

      // Check if processing toss-in action
      const isProcessingTossInAction =
        state.activeTossIn && state.activeTossIn.queuedActions.length > 0;

      // For toss-in actions, validate against pendingAction.playerId
      // For normal actions, validate against currentPlayerIndex
      if (isProcessingTossInAction) {
        if (!state.pendingAction || state.pendingAction.playerId !== playerId) {
          return { valid: false, reason: 'Not your toss-in action' };
        }
      } else {
        const currentPlayer = state.players[state.currentPlayerIndex];
        if (currentPlayer.id !== playerId) {
          return { valid: false, reason: 'Not player turn' };
        }
      }

      // Must be in awaiting_action phase (after using King card and selecting card)
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

      // Must be in declaring-rank action phase (after selecting card)
      if (state.pendingAction.actionPhase !== 'declaring-rank') {
        return {
          valid: false,
          reason: `Cannot declare rank in action phase ${state.pendingAction.actionPhase}`,
        };
      }

      // Must have selected a card
      if (!state.pendingAction.selectedCardForKing) {
        return { valid: false, reason: 'No card selected for King action' };
      }

      return { valid: true };
    }

    case 'PARTICIPATE_IN_TOSS_IN': {
      const { playerId, position } = action.payload;

      // Must be in toss-in phase
      if (
        state.subPhase !== 'toss_queue_active' &&
        state.subPhase !== 'toss_queue_processing'
      ) {
        return {
          valid: false,
          reason: `Cannot toss in during phase ${state.subPhase}`,
        };
      }

      // Must have an active toss-in
      if (!state.activeTossIn) {
        return { valid: false, reason: 'No active toss-in' };
      }

      // Find the player
      const player = state.players.find((p) => p.id === playerId);
      if (!player) {
        return { valid: false, reason: 'Player not found' };
      }

      // Position must be valid
      if (position < 0 || position >= player.cards.length) {
        return { valid: false, reason: `Invalid card position ${position}` };
      }

      return { valid: true };
    }

    case 'PLAYER_TOSS_IN_FINISHED': {
      const { playerId } = action.payload;

      // Must have an active toss-in (primary check)
      if (!state.activeTossIn) {
        return { valid: false, reason: 'No active toss-in' };
      }

      // Find the player
      const player = state.players.find((p) => p.id === playerId);
      if (!player) {
        return { valid: false, reason: 'Player not found' };
      }

      // Player can't confirm twice
      if (state.activeTossIn.playersReadyForNextTurn.includes(playerId)) {
        return {
          valid: false,
          reason: 'Player already confirmed ready for next turn',
        };
      }

      return { valid: true };
    }

    case 'FINISH_TOSS_IN_PERIOD': {
      const { initiatorId } = action.payload;

      // Must be in toss-in phase
      if (
        state.subPhase !== 'toss_queue_active' &&
        state.subPhase !== 'toss_queue_processing'
      ) {
        return {
          valid: false,
          reason: `Cannot finish toss-in during phase ${state.subPhase}`,
        };
      }

      // Must have an active toss-in
      if (!state.activeTossIn) {
        return { valid: false, reason: 'No active toss-in' };
      }

      // Initiator must be the one who triggered toss-in
      if (state.activeTossIn.initiatorId !== initiatorId) {
        return {
          valid: false,
          reason: 'Only toss-in initiator can finish the period',
        };
      }

      return { valid: true };
    }

    case 'SET_COALITION_LEADER': {
      const { leaderId } = action.payload;

      // Must be in final phase (after Vinto called)
      if (state.phase !== 'final') {
        return {
          valid: false,
          reason: 'Coalition leader can only be set in final phase',
        };
      }

      // Must have a Vinto caller
      if (!state.vintoCallerId) {
        return {
          valid: false,
          reason: 'No Vinto caller to form coalition against',
        };
      }

      // Leader must be a player
      const leader = state.players.find((p) => p.id === leaderId);
      if (!leader) {
        return { valid: false, reason: 'Leader player not found' };
      }

      // Leader cannot be the Vinto caller
      if (leader.id === state.vintoCallerId) {
        return {
          valid: false,
          reason: 'Vinto caller cannot be coalition leader',
        };
      }

      return { valid: true };
    }

    case 'PROCESS_AI_TURN': {
      const { playerId } = action.payload;

      // Must be a bot player
      const player = state.players.find((p) => p.id === playerId);
      if (!player) {
        return { valid: false, reason: 'Player not found' };
      }

      if (!player.isBot) {
        return { valid: false, reason: 'Player is not a bot' };
      }

      // Must be the current player
      const currentPlayer = state.players[state.currentPlayerIndex];
      if (currentPlayer.id !== playerId) {
        return { valid: false, reason: 'Not player turn' };
      }

      return { valid: true };
    }

    case 'PEEK_SETUP_CARD': {
      const { playerId, position } = action.payload;

      // Must be in setup phase
      if (state.phase !== 'setup') {
        return { valid: false, reason: 'Not in setup phase' };
      }

      // Find the player
      const player = state.players.find((p) => p.id === playerId);
      if (!player) {
        return { valid: false, reason: 'Player not found' };
      }

      // Position must be valid
      if (position < 0 || position >= player.cards.length) {
        return { valid: false, reason: `Invalid card position ${position}` };
      }

      // Player shouldn't peek at same card twice during setup
      if (player.knownCardPositions.includes(position)) {
        return { valid: false, reason: 'Card already peeked' };
      }

      return { valid: true };
    }

    case 'FINISH_SETUP': {
      const { playerId } = action.payload;

      // Must be in setup phase
      if (state.phase !== 'setup') {
        return { valid: false, reason: 'Not in setup phase' };
      }

      // Find the player
      const player = state.players.find((p) => p.id === playerId);
      if (!player) {
        return { valid: false, reason: 'Player not found' };
      }

      // Player must have peeked at at least 2 cards
      if (player.knownCardPositions.length < 2) {
        return {
          valid: false,
          reason: `Must peek at 2 cards before finishing setup (peeked ${player.knownCardPositions.length})`,
        };
      }

      return { valid: true };
    }

    case 'UPDATE_DIFFICULTY':
    case 'SET_NEXT_DRAW_CARD':
      // Always valid (configuration/debug actions)
      return { valid: true };

    default:
      throw new NeverError(action);
  }
}

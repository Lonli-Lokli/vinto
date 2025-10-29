import { GameAction, GameState, NeverError } from '@vinto/shapes';

type ValidationResult =
  | {
      valid: true;
    }
  | {
      valid: false;
      reason: string;
    };
/**
 * Validates if an action is legal in the current state
 */
export function actionValidator(
  state: GameState,
  action: GameAction
): ValidationResult {
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

      // Must not have already been played
      if (state.pendingAction.card.played) {
        return { valid: false, reason: 'Card has already been played' };
      }

      return { valid: true };
    }

    case 'SELECT_ACTION_TARGET': {
      const { playerId, targetPlayerId } = action.payload;

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

      // COALITION RULE: Coalition members cannot target Vinto caller
      // Check if current player is in coalition against Vinto caller
      if (
        state.phase === 'final' &&
        state.vintoCallerId &&
        state.coalitionLeaderId
      ) {
        const currentPlayerData = isProcessingTossInAction
          ? state.players.find((p) => p.id === state.pendingAction?.playerId)
          : state.players[state.currentPlayerIndex];

        const isCoalitionMember =
          currentPlayerData && currentPlayerData.id !== state.vintoCallerId;

        if (isCoalitionMember && targetPlayerId === state.vintoCallerId) {
          return {
            valid: false,
            reason: 'Coalition members cannot target Vinto caller with actions',
          };
        }
      }

      // Position must be valid for target player
      if (
        action.payload.rank === 'Any' &&
        (action.payload.position < 0 ||
          action.payload.position >= targetPlayer.cards.length)
      ) {
        return {
          valid: false,
          reason: `Invalid position ${action.payload.position} for target player`,
        };
      }

      // For Jack (swap-cards) and Queen (peek-and-swap), ensure targets are from different players
      const actionCard = state.pendingAction.card;
      if (actionCard && (actionCard.rank === 'J' || actionCard.rank === 'Q')) {
        const existingTargets = state.pendingAction.targets || [];

        // If this is the second target, check it's from a different player
        if (existingTargets.length === 1) {
          const firstTargetPlayerId = existingTargets[0].playerId;
          if (firstTargetPlayerId === targetPlayerId) {
            return {
              valid: false,
              reason: 'Jack and Queen must target cards from different players',
            };
          }
        }
      }

      return { valid: true };
    }

    case 'SKIP_PEEK':
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

      // Must have exactly 2 targets (Jack swaps 2 cards)
      if (state.pendingAction.targets.length !== 2) {
        return {
          valid: false,
          reason: `Jack action requires 2 targets, got ${state.pendingAction.targets.length}`,
        };
      }

      if (
        state.pendingAction.targets[0].playerId ===
        state.pendingAction.targets[1].playerId
      ) {
        return {
          valid: false,
          reason: `Jack action requires 2 different players, got same ${state.pendingAction.targets[0].playerId}`,
        };
      }

      // Note: Coalition validation happens in SELECT_ACTION_TARGET
      // Targets are already validated when they were selected

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

      if (
        state.pendingAction.targets[0].playerId ===
        state.pendingAction.targets[1].playerId
      ) {
        return {
          valid: false,
          reason: `Jack action requires 2 different players, got same ${state.pendingAction.targets[0].playerId}`,
        };
      }

      // Note: Coalition validation happens in SELECT_ACTION_TARGET
      // Targets are already validated when they were selected

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

      // Must be in selecting-target action phase (after selecting card via SELECT_ACTION_TARGET)
      if (state.pendingAction.actionPhase !== 'selecting-target') {
        return {
          valid: false,
          reason: `Cannot declare rank in action phase ${state.pendingAction.actionPhase}`,
        };
      }

      const targetPlayer = state.players.find(
        (p) => p.id === state.pendingAction?.targets?.[0]?.playerId
      );
      if (!targetPlayer) {
        return { valid: false, reason: 'Target player not found' };
      }

      // Position must be valid for target player
      if (
        state.pendingAction.targets?.[0]?.position < 0 ||
        state.pendingAction.targets?.[0]?.position >= targetPlayer.cards.length
      ) {
        return {
          valid: false,
          reason: `Invalid position ${state.pendingAction.targets?.[0]?.position} for target player`,
        };
      }

      if (!state.pendingAction.targets?.[0]?.playerId) {
        return {
          valid: false,
          reason: 'No card position selected for King action',
        };
      }

      // Note: Coalition validation happens in SELECT_ACTION_TARGET
      // Target is already validated when it was selected

      return { valid: true };
    }

    case 'PARTICIPATE_IN_TOSS_IN': {
      const { playerId, positions } = action.payload;

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

      // Validate all positions
      if (!positions || positions.length === 0) {
        return { valid: false, reason: 'No positions provided' };
      }

      for (const position of positions) {
        if (position < 0 || position >= player.cards.length) {
          return { valid: false, reason: `Invalid card position ${position}` };
        }
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

    case 'EMPTY':
    case 'UPDATE_DIFFICULTY':
    case 'SET_NEXT_DRAW_CARD':
    case 'SWAP_HAND_WITH_DECK':
      // Always valid (configuration/debug actions)
      return { valid: true };

    default:
      throw new NeverError(action);
  }
}

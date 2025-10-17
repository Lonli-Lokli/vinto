// game-table-logic.ts
// Pure functions for GameTable component logic

import type { GameClient } from '@/client';
import { GameActions } from '@/engine';
import { GamePhase, GameSubPhase, PendingAction, PlayerState } from '@/shared';
import { UIStore } from '../../stores';

/**
 * Calculate if card interactions should be enabled for the human player
 */
export function shouldAllowCardInteractions(params: {
  humanPlayer: PlayerState | undefined;
  phase: GamePhase;
  subPhase: GameSubPhase;
  setupPeeksRemaining: number;
  isSelectingSwapPosition: boolean;
  waitingForTossIn: boolean;
  isAwaitingActionTarget: boolean;
  targetType: PendingAction['targetType'];
  peekTargets: PendingAction['targets'];
  hasCompletePeekSelection: boolean;
  uiStore: UIStore;
}): boolean {
  const {
    humanPlayer,
    phase,
    setupPeeksRemaining,
    isSelectingSwapPosition,
    waitingForTossIn,
    isAwaitingActionTarget,
    targetType,
    peekTargets,
    hasCompletePeekSelection,
    uiStore,
  } = params;

  if (!humanPlayer) return false;

  // For Queen action (peek-then-swap), must select from different players
  if (isAwaitingActionTarget && targetType === 'peek-then-swap') {
    // If one card already selected from this player, disable
    if (peekTargets.length > 0 && peekTargets[0].playerId === humanPlayer.id) {
      return false;
    }
    // If 2 cards already selected, disable
    if (hasCompletePeekSelection) {
      return false;
    }
    // Otherwise allow
    return true;
  }

  // For Jack action (swap-cards), must select from different players
  if (isAwaitingActionTarget && targetType === 'swap-cards') {
    // If one card already selected from this player, disable
    if (peekTargets.length > 0 && peekTargets[0].playerId === humanPlayer.id) {
      return false;
    }
    // If 2 cards already selected, disable
    if (hasCompletePeekSelection) {
      return false;
    }
    // Otherwise allow
    return true;
  }

  // For own-card peek (7/8), disable after one card is revealed
  if (
    isAwaitingActionTarget &&
    targetType === 'own-card' &&
    humanPlayer &&
    uiStore.getTemporarilyVisibleCards(humanPlayer.id).size > 0
  ) {
    return false;
  }

  // For King action (declare-action), allow selecting any card
  if (isAwaitingActionTarget && targetType === 'declare-action') {
    return true;
  }

  // Only allow interactions when it's relevant for the human player
  return (
    // During setup phase for memorization
    (phase === 'setup' && setupPeeksRemaining > 0) ||
    // When selecting swap position after drawing
    isSelectingSwapPosition ||
    // During toss-in period
    waitingForTossIn ||
    // During action target selection for own cards
    (isAwaitingActionTarget && targetType === 'own-card')
  );
}

/**
 * Calculate if opponent card interactions should be enabled for a specific player
 */
export function shouldAllowOpponentCardInteractions(params: {
  opponentPlayerId: string;
  isAwaitingActionTarget: boolean;
  targetType: PendingAction['targetType'];
  peekTargets: PendingAction['targets'];
  hasCompletePeekSelection: boolean;
}): boolean {
  const {
    opponentPlayerId,
    isAwaitingActionTarget,
    targetType,
    peekTargets,
    hasCompletePeekSelection,
  } = params;

  // For Ace action (force-draw), disable card interactions - use name buttons instead
  if (isAwaitingActionTarget && targetType === 'force-draw') {
    return false;
  }

  // For Queen action (peek-then-swap), disable when 2 cards already selected
  if (
    isAwaitingActionTarget &&
    targetType === 'peek-then-swap' &&
    hasCompletePeekSelection
  ) {
    return false;
  }

  // For Queen action (peek-then-swap), must select from different players
  if (isAwaitingActionTarget && targetType === 'peek-then-swap') {
    // If one card already selected from this player, disable this player
    if (
      peekTargets.length > 0 &&
      peekTargets[0].playerId === opponentPlayerId
    ) {
      return false;
    }
    // Otherwise allow
    return true;
  }

  // For Jack action (swap-cards), must select from different players
  if (isAwaitingActionTarget && targetType === 'swap-cards') {
    // If one card already selected from this player, disable this player
    if (
      peekTargets.length > 0 &&
      peekTargets[0].playerId === opponentPlayerId
    ) {
      return false;
    }
    // Otherwise allow
    return true;
  }

  // For opponent-card peek (9/10 action), single target only
  if (isAwaitingActionTarget && targetType === 'opponent-card') {
    // Disable all opponent cards after one has been selected
    if (peekTargets.length > 0) {
      return false;
    }
    return true;
  }

  // For King action (declare-action), allow selecting any opponent card
  if (isAwaitingActionTarget && targetType === 'declare-action') {
    return true;
  }

  return (
    isAwaitingActionTarget &&
    (targetType === 'opponent-card' ||
      targetType === 'peek-then-swap' ||
      targetType === 'swap-cards')
  );
}

/**
 * Handle card click for the human player's own cards
 */
export function handleCardClick(params: {
  position: number;
  humanPlayer: PlayerState;
  phase: GamePhase;
  subPhase: GameSubPhase;
  setupPeeksRemaining: number;
  isSelectingSwapPosition: boolean;
  waitingForTossIn: boolean;
  isAwaitingActionTarget: boolean;
  targetType: PendingAction['targetType'];
  gameClient: GameClient;
  uiStore: UIStore;
}): void {
  const {
    position,
    humanPlayer,
    phase,
    subPhase,
    setupPeeksRemaining,
    isSelectingSwapPosition,
    waitingForTossIn,
    isAwaitingActionTarget,
    targetType,
    gameClient,
    uiStore,
  } = params;

  console.log('[handleCardClick] Card clicked:', {
    position,
    humanPlayerId: humanPlayer.id,
    phase,
    subPhase,
    isSelectingSwapPosition,
    isAwaitingActionTarget,
    waitingForTossIn,
  });

  // During setup phase, allow peeking at cards for memorization
  if (phase === 'setup') {
    if (
      setupPeeksRemaining > 0 &&
      !humanPlayer.knownCardPositions.includes(position)
    ) {
      // Show the card temporarily in the UI
      uiStore.addTemporarilyVisibleCard(humanPlayer.id, position);
      // Dispatch the game action to update knownCardPositions
      gameClient.dispatch(GameActions.peekSetupCard(humanPlayer.id, position));
    }
    return;
  }

  // If selecting swap position, store the selected position in UI store
  if (isSelectingSwapPosition) {
    console.log('[GameTable] Position selected for swap:', {
      humanPlayerId: humanPlayer.id,
      position,
      currentSubPhase: subPhase,
    });

    // Store position in UI store (will show rank declaration buttons inline)
    uiStore.setSelectedSwapPosition(position);
    return;
  }

  // During toss-in period, allow tossing in cards
  if (waitingForTossIn) {
    gameClient.dispatch(
      GameActions.participateInTossIn(humanPlayer.id, position)
    );
    return;
  }

  // During King action target selection, select card without revealing
  if (isAwaitingActionTarget && targetType === 'declare-action') {
    // Don't reveal the card - just highlight it
    // Dispatch the King card target selection
    gameClient.dispatch(
      GameActions.selectKingCardTarget(humanPlayer.id, humanPlayer.id, position)
    );
    return;
  }

  // During action target selection, allow selecting target
  if (
    isAwaitingActionTarget &&
    (targetType === 'own-card' ||
      targetType === 'peek-then-swap' ||
      targetType === 'swap-cards')
  ) {
    // For peek actions, reveal the card temporarily
    if (targetType === 'own-card' || targetType === 'peek-then-swap') {
      uiStore.addTemporarilyVisibleCard(humanPlayer.id, position);
    }

    gameClient.dispatch(
      GameActions.selectActionTarget(humanPlayer.id, humanPlayer.id, position)
    );
    return;
  }
}

/**
 * Handle card click for opponent cards
 */
export function handleOpponentCardClick(params: {
  playerId: string;
  position: number;
  humanPlayer: PlayerState;
  isAwaitingActionTarget: boolean;
  targetType: PendingAction['targetType'];
  gameClient: GameClient;
  uiStore: UIStore;
}): void {
  const {
    playerId,
    position,
    humanPlayer,
    isAwaitingActionTarget,
    targetType,
    gameClient,
    uiStore,
  } = params;

  // During King action target selection for opponent cards, select without revealing
  if (isAwaitingActionTarget && targetType === 'declare-action') {
    // Don't reveal the card - just highlight it
    // Dispatch the King card target selection
    gameClient.dispatch(
      GameActions.selectKingCardTarget(humanPlayer.id, playerId, position)
    );
    return;
  }

  // During action target selection for opponent cards, Queen peek-then-swap, or Jack swaps
  if (
    isAwaitingActionTarget &&
    (targetType === 'opponent-card' ||
      targetType === 'force-draw' ||
      targetType === 'peek-then-swap' ||
      targetType === 'swap-cards')
  ) {
    // For peek actions, reveal the card temporarily
    if (targetType === 'opponent-card' || targetType === 'peek-then-swap') {
      uiStore.addTemporarilyVisibleCard(playerId, position);
    }

    gameClient.dispatch(
      GameActions.selectActionTarget(humanPlayer.id, playerId, position)
    );
  }
}

/**
 * Handle draw card action
 */
export function handleDrawCard(params: {
  currentPlayer: PlayerState | undefined;
  gameClient: GameClient;
}): void {
  const { currentPlayer, gameClient } = params;

  if (currentPlayer && currentPlayer.isHuman) {
    gameClient.dispatch(GameActions.drawCard(currentPlayer.id));
  }
}

/**
 * Get player position based on player index
 */
export function getPlayerPosition(
  player: PlayerState,
  allPlayers: PlayerState[]
): 'bottom' | 'left' | 'top' | 'right' {
  if (player.isHuman) return 'bottom';

  const playerIndex = allPlayers.indexOf(player);

  // For 4 players: human (bottom), opponent1 (left), opponent2 (top), opponent3 (right)
  if (playerIndex === 1) return 'left';
  if (playerIndex === 2) return 'top';
  return 'right';
}

/**
 * Calculate setup peeks remaining
 */
export function calculateSetupPeeksRemaining(
  humanPlayer: PlayerState | undefined
): number {
  return humanPlayer
    ? Math.max(0, 2 - humanPlayer.knownCardPositions.length)
    : 0;
}

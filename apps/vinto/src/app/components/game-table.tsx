// components/GameTable.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { useUIStore } from './di-provider';
import { PlayerArea } from './player-area';
import { DeckArea } from './deck-area';
import { useIsDesktop } from '../hooks/use-media-query';
import { useGameClient } from '@/client';
import { GameActions } from '@/engine';
import { PlayerState } from '@/shared';

export const GameTable = observer(() => {
  const isDesktop = useIsDesktop();
  const uiStore = useUIStore();
  const gameClient = useGameClient();

  // Get values from GameClient
  const currentPlayer = gameClient.currentPlayer;
  const humanPlayer = gameClient.state.players.find((p) => p.isHuman);
  const phase = gameClient.state.phase;
  const subPhase = gameClient.state.subPhase;
  const discardPile = gameClient.state.discardPile;

  // Map subPhases to old boolean flags
  const isSelectingSwapPosition = uiStore.isSelectingSwapPosition;
  const isChoosingCardAction = subPhase === 'choosing';
  const isDeclaringRank = subPhase === 'declaring_rank';
  const isAwaitingActionTarget = subPhase === 'awaiting_action';
  const waitingForTossIn =
    subPhase === 'toss_queue_active' || subPhase === 'toss_queue_processing';

  // Get action state from GameClient.pendingAction
  const pendingAction = gameClient.state.pendingAction;
  const pendingCard = pendingAction?.card;
  const swapPosition = pendingAction?.swapPosition;
  const targetType = pendingAction?.targetType;
  const peekTargets = pendingAction?.targets || [];
  const hasCompletePeekSelection = peekTargets.length === 2;

  // Calculate setup peeks remaining from GameClient state
  const setupPeeksRemaining = humanPlayer
    ? Math.max(0, 2 - humanPlayer.knownCardPositions.length)
    : 0;

  // Determine if card interactions should be enabled
  const shouldAllowCardInteractions = () => {
    if (!humanPlayer) return false;

    // For Queen action (peek-then-swap), disable when 2 cards already selected
    if (
      isAwaitingActionTarget &&
      targetType === 'peek-then-swap' &&
      hasCompletePeekSelection
    ) {
      return false;
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

    // Only allow interactions when it's relevant for the human player
    return (
      // During setup phase for memorization
      (phase === 'setup' && setupPeeksRemaining > 0) ||
      // When selecting swap position after drawing
      isSelectingSwapPosition ||
      // During toss-in period
      waitingForTossIn ||
      // During action target selection for own cards
      (isAwaitingActionTarget &&
        (targetType === 'own-card' ||
          targetType === 'peek-then-swap' ||
          targetType === 'swap-cards'))
    );
  };

  const handleCardClick = (position: number) => {
    console.log('[handleCardClick] Card clicked:', {
      position,
      humanPlayerId: humanPlayer?.id,
      phase,
      subPhase,
      isSelectingSwapPosition,
      isChoosingCardAction,
      isAwaitingActionTarget,
      waitingForTossIn,
    });

    if (!humanPlayer) return;

    // During setup phase, allow peeking at cards for memorization
    if (phase === 'setup') {
      if (
        setupPeeksRemaining > 0 &&
        !humanPlayer.knownCardPositions.includes(position)
      ) {
        // Show the card temporarily in the UI
        uiStore.addTemporarilyVisibleCard(humanPlayer.id, position);
        // Dispatch the game action to update knownCardPositions
        gameClient.dispatch(
          GameActions.peekSetupCard(humanPlayer.id, position)
        );
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
  };

  // Determine if opponent card interactions should be enabled
  const shouldAllowOpponentCardInteractions = () => {
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

    // For opponent-card peek (J action), disable after one card is revealed
    // Check if ANY player has temporarily visible cards (the peeked opponent card)
    if (
      isAwaitingActionTarget &&
      targetType === 'opponent-card' &&
      gameClient.state.players.some(
        (p) => uiStore.getTemporarilyVisibleCards(p.id).size > 0
      )
    ) {
      return false;
    }

    return (
      isAwaitingActionTarget &&
      (targetType === 'opponent-card' ||
        targetType === 'peek-then-swap' ||
        targetType === 'swap-cards')
    );
  };

  const handleOpponentCardClick = (playerId: string, position: number) => {
    if (!humanPlayer) return;

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
  };

  const handleDrawCard = () => {
    if (currentPlayer && currentPlayer.isHuman) {
      gameClient.dispatch(GameActions.drawCard(currentPlayer.id));
    }
  };

  // Calculate player positions based on index
  // Human player is always index 0 (bottom position)
  // Other players are assigned top, left, right based on total player count
  type PlayerPosition = 'bottom' | 'left' | 'top' | 'right';

  const getPlayerPosition = (player: PlayerState): PlayerPosition => {
    if (player.isHuman) return 'bottom';

    const playerIndex = gameClient.state.players.indexOf(player);

    // For 4 players: human (bottom), opponent1 (left), opponent2 (top), opponent3 (right)
    if (playerIndex === 1) return 'left';
    if (playerIndex === 2) return 'top';
    return 'right';
  };

  const playersWithPositions = gameClient.state.players.map((player) => ({
    player,
    position: getPlayerPosition(player),
  }));

  const top = playersWithPositions.find((p) => p.position === 'top');
  const left = playersWithPositions.find((p) => p.position === 'left');
  const right = playersWithPositions.find((p) => p.position === 'right');
  const bottom = playersWithPositions.find((p) => p.position === 'bottom');

  return (
    <div className="h-full flex flex-col">
      <div className="w-full h-full max-w-lg md:max-w-full mx-auto flex flex-col">
        {/* Conditionally render mobile OR desktop layout */}
        {!isDesktop ? (
          /* Mobile stacked layout: 3 rows (no overlap) */
          <div className="h-full flex flex-col bg-table-gradient rounded-lg border-2 border-primary shadow-theme-lg p-2 overflow-hidden">
            {/* Row 1: Top player */}
            {top && (
              <div className="flex justify-center flex-shrink-0 pb-2">
                <PlayerArea
                  player={top.player}
                  position={top.position}
                  isCurrentPlayer={currentPlayer?.id === top.player.id}
                  isThinking={
                    subPhase === 'ai_thinking' &&
                    currentPlayer?.id === top.player.id
                  }
                  gamePhase={phase}
                  onCardClick={
                    shouldAllowOpponentCardInteractions()
                      ? (position) =>
                          handleOpponentCardClick(top.player.id, position)
                      : undefined
                  }
                  isSelectingActionTarget={shouldAllowOpponentCardInteractions()}
                />
              </div>
            )}

            {/* Row 2: Left | Center piles | Right */}
            <div className="flex-1 flex items-center justify-between gap-2 sm:gap-3 min-h-0">
              {/* Left Player */}
              <div className="flex-1 flex justify-start items-start">
                {left && (
                  <PlayerArea
                    player={left.player}
                    position={left.position}
                    isCurrentPlayer={currentPlayer?.id === left.player.id}
                    isThinking={
                      subPhase === 'ai_thinking' &&
                      currentPlayer?.id === left.player.id
                    }
                    gamePhase={phase}
                    onCardClick={
                      shouldAllowOpponentCardInteractions()
                        ? (position) =>
                            handleOpponentCardClick(left.player.id, position)
                        : undefined
                    }
                    isSelectingActionTarget={shouldAllowOpponentCardInteractions()}
                  />
                )}
              </div>

              {/* Center draw/discard */}
              <div className="flex flex-col items-center justify-center gap-2 flex-shrink-0 relative">
                <DeckArea
                  discardPile={discardPile}
                  pendingCard={pendingCard ?? null}
                  canDrawCard={
                    !!(
                      currentPlayer?.isHuman &&
                      !isSelectingSwapPosition &&
                      !isChoosingCardAction &&
                      !isAwaitingActionTarget &&
                      !isDeclaringRank &&
                      phase === 'playing'
                    )
                  }
                  onDrawCard={handleDrawCard}
                  isMobile={true}
                  isSelectingActionTarget={isAwaitingActionTarget}
                />
              </div>

              {/* Right Player */}
              <div className="flex-1 flex justify-end items-start">
                {right && (
                  <PlayerArea
                    player={right.player}
                    position={right.position}
                    isCurrentPlayer={currentPlayer?.id === right.player.id}
                    isThinking={
                      subPhase === 'ai_thinking' &&
                      currentPlayer?.id === right.player.id
                    }
                    gamePhase={phase}
                    onCardClick={
                      shouldAllowOpponentCardInteractions()
                        ? (position) =>
                            handleOpponentCardClick(right.player.id, position)
                        : undefined
                    }
                    isSelectingActionTarget={shouldAllowOpponentCardInteractions()}
                  />
                )}
              </div>
            </div>

            {/* Row 3: Human player */}
            <div className="flex justify-center flex-shrink-0 pt-2">
              {bottom && (
                <PlayerArea
                  player={bottom.player}
                  position={bottom.position}
                  isCurrentPlayer={currentPlayer?.id === bottom.player.id}
                  isThinking={false}
                  onCardClick={
                    shouldAllowCardInteractions() ? handleCardClick : undefined
                  }
                  gamePhase={phase}
                  isSelectingSwapPosition={isSelectingSwapPosition}
                  isDeclaringRank={isDeclaringRank}
                  swapPosition={swapPosition}
                  isSelectingActionTarget={shouldAllowCardInteractions()}
                />
              )}
            </div>
          </div>
        ) : (
          /* Desktop/Tablet wide board */
          <div className="relative bg-table-gradient rounded-lg border-2 border-primary shadow-theme-lg p-3 w-full h-full min-h-0">
            {/* Top Player */}
            {top && (
              <div className="absolute top-4 left-1/2 translate-x-8">
                <PlayerArea
                  player={top.player}
                  position={top.position}
                  isCurrentPlayer={currentPlayer?.id === top.player.id}
                  isThinking={
                    subPhase === 'ai_thinking' &&
                    currentPlayer?.id === top.player.id
                  }
                  gamePhase={phase}
                  onCardClick={
                    shouldAllowOpponentCardInteractions()
                      ? (position) =>
                          handleOpponentCardClick(top.player.id, position)
                      : undefined
                  }
                  isSelectingActionTarget={shouldAllowOpponentCardInteractions()}
                />
              </div>
            )}

            {/* Left Player */}
            {left && (
              <div className="absolute left-4 top-1/2 -translate-y-1/2">
                <PlayerArea
                  player={left.player}
                  position={left.position}
                  isCurrentPlayer={currentPlayer?.id === left.player.id}
                  isThinking={
                    subPhase === 'ai_thinking' &&
                    currentPlayer?.id === left.player.id
                  }
                  gamePhase={phase}
                  onCardClick={
                    shouldAllowOpponentCardInteractions()
                      ? (position) =>
                          handleOpponentCardClick(left.player.id, position)
                      : undefined
                  }
                  isSelectingActionTarget={shouldAllowOpponentCardInteractions()}
                />
              </div>
            )}

            {/* Right Player */}
            {right && (
              <div className="absolute right-4 top-1/2 -translate-y-1/2">
                <PlayerArea
                  player={right.player}
                  position={right.position}
                  isCurrentPlayer={currentPlayer?.id === right.player.id}
                  isThinking={
                    subPhase === 'ai_thinking' &&
                    currentPlayer?.id === right.player.id
                  }
                  gamePhase={phase}
                  onCardClick={
                    shouldAllowOpponentCardInteractions()
                      ? (position) =>
                          handleOpponentCardClick(right.player.id, position)
                      : undefined
                  }
                  isSelectingActionTarget={shouldAllowOpponentCardInteractions()}
                />
              </div>
            )}

            {/* Center - Draw & Discard Piles */}
            <div className="absolute inset-16 flex items-center justify-center">
              <DeckArea
                discardPile={discardPile}
                pendingCard={pendingCard ?? null}
                canDrawCard={
                  !!(
                    currentPlayer?.isHuman &&
                    !isSelectingSwapPosition &&
                    !isChoosingCardAction &&
                    !isAwaitingActionTarget &&
                    !isDeclaringRank &&
                    phase === 'playing'
                  )
                }
                onDrawCard={handleDrawCard}
                isMobile={false}
                isSelectingActionTarget={isAwaitingActionTarget}
              />
            </div>

            {/* Human Player */}
            <div className="absolute bottom-4 left-1/3">
              {bottom && (
                <PlayerArea
                  player={bottom.player}
                  position={bottom.position}
                  isCurrentPlayer={currentPlayer?.id === bottom.player.id}
                  isThinking={false}
                  onCardClick={
                    shouldAllowCardInteractions() ? handleCardClick : undefined
                  }
                  gamePhase={phase}
                  isSelectingSwapPosition={isSelectingSwapPosition}
                  isDeclaringRank={isDeclaringRank}
                  swapPosition={swapPosition}
                  isSelectingActionTarget={shouldAllowCardInteractions()}
                />
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
});

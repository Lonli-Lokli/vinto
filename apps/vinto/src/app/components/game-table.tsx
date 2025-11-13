// components/GameTable.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { useUIStore } from './di-provider';
import { PlayerArea } from './player-area';
import { DeckArea } from './presentational';
import { useIsDesktop } from '../hooks/use-media-query';
import { useGameClient } from '@vinto/local-client';
import * as GameTableLogic from './logic/game-table-logic';

export const GameTable = observer(() => {
  const isDesktop = useIsDesktop();
  const uiStore = useUIStore();
  const gameClient = useGameClient();

  // Get values from GameClient
  const currentPlayer = gameClient.currentPlayer;
  const humanPlayer = gameClient.visualState.players.find((p) => p.isHuman);
  const phase = gameClient.visualState.phase;
  const subPhase = gameClient.visualState.subPhase;
  const discardPile = gameClient.visualState.discardPile;

  // Get toss-in information
  const tossInRanks = gameClient.visualState.activeTossIn?.ranks || [];
  const tossInQueue =
    gameClient.visualState.activeTossIn?.queuedActions.map((action) => {
      const player = gameClient.visualState.players.find(
        (p) => p.id === action.playerId
      );
      return {
        playerId: action.playerId,
        playerName: player?.nickname || 'Unknown',
        rank: action.rank,
      };
    }) || [];

  // Map subPhases to old boolean flags
  const isSelectingSwapPosition = uiStore.isSelectingSwapPosition;
  const isChoosingCardAction = subPhase === 'choosing';
  const isAwaitingActionTarget = subPhase === 'awaiting_action';
  const waitingForTossIn =
    subPhase === 'toss_queue_active' || subPhase === 'toss_queue_processing';

  // Get action state from GameClient.pendingAction
  const pendingAction = gameClient.visualState.pendingAction;
  const pendingCard = pendingAction?.card;
  const swapPosition = pendingAction?.swapPosition;
  const targetType = pendingAction?.targetType;
  const peekTargets = pendingAction?.targets || [];
  const hasCompletePeekSelection = peekTargets.length === 2;

  // Calculate setup peeks remaining from GameClient state
  const setupPeeksRemaining =
    GameTableLogic.calculateSetupPeeksRemaining(humanPlayer);

  // Get failed toss-in attempts from ActiveTossIn state
  const failedTossInAttempts =
    gameClient.visualState.roundFailedAttempts.map((attempt) => ({
      playerId: attempt.playerId,
      position: attempt.position,
    })) || [];

  // Determine if card interactions should be enabled
  const shouldAllowCardInteractions = () =>
    GameTableLogic.shouldAllowCardInteractions({
      humanPlayer,
      phase,
      subPhase,
      setupPeeksRemaining,
      isSelectingSwapPosition,
      waitingForTossIn,
      isAwaitingActionTarget,
      targetType,
      peekTargets,
      hasCompletePeekSelection,
      uiStore,
      failedTossInAttempts,
      actingPlayerId: pendingAction?.playerId || null,
    });

  const handleCardClick = (position: number) => {
    if (!humanPlayer) return;

    GameTableLogic.handleCardClick({
      position,
      humanPlayer,
      phase,
      subPhase,
      setupPeeksRemaining,
      isSelectingSwapPosition,
      waitingForTossIn,
      isAwaitingActionTarget,
      targetType,
      actingPlayerId: pendingAction?.playerId || null,
      gameClient,
      uiStore,
    });
  };

  // Determine if opponent card interactions should be enabled for a specific player
  const shouldAllowOpponentCardInteractions = (opponentPlayerId: string) =>
    GameTableLogic.shouldAllowOpponentCardInteractions({
      opponentPlayerId,
      isAwaitingActionTarget,
      targetType,
      peekTargets,
      hasCompletePeekSelection,
    });

  const handleOpponentCardClick = (playerId: string, position: number) => {
    if (!humanPlayer) return;

    GameTableLogic.handleOpponentCardClick({
      playerId,
      position,
      humanPlayer,
      isAwaitingActionTarget,
      targetType,
      actingPlayerId: pendingAction?.playerId || null,
      gameClient,
      uiStore,
    });
  };

  const handleDrawCard = () => {
    GameTableLogic.handleDrawCard({
      currentPlayer,
      gameClient,
    });
  };

  // Calculate player positions based on index
  // Human player is always index 0 (bottom position)
  // Other players are assigned top, left, right based on total player count
  const playersWithPositions = gameClient.visualState.players.map((player) => ({
    player,
    position: GameTableLogic.getPlayerPosition(
      player,
      gameClient.visualState.players
    ),
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
                    shouldAllowOpponentCardInteractions(top.player.id)
                      ? (position) =>
                          handleOpponentCardClick(top.player.id, position)
                      : undefined
                  }
                  isSelectingActionTarget={shouldAllowOpponentCardInteractions(
                    top.player.id
                  )}
                />
              </div>
            )}

            {/* Row 2: Left | Center piles | Right */}
            <div className="flex-1 flex items-start gap-1 sm:gap-2 min-h-0 pt-4">
              {/* Left Player */}
              <div className="flex-shrink-0 flex justify-start items-start">
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
                      shouldAllowOpponentCardInteractions(left.player.id)
                        ? (position) =>
                            handleOpponentCardClick(left.player.id, position)
                        : undefined
                    }
                    isSelectingActionTarget={shouldAllowOpponentCardInteractions(
                      left.player.id
                    )}
                  />
                )}
              </div>

              {/* Center draw/discard - takes remaining space and centers content */}
              <div className="flex-1 flex items-center justify-center min-w-0 pt-8">
                <DeckArea
                  discardPile={discardPile}
                  pendingCard={pendingCard ?? null}
                  tossInRanks={tossInRanks}
                  tossInQueue={tossInQueue}
                  canDrawCard={
                    !!(
                      currentPlayer?.isHuman &&
                      !isSelectingSwapPosition &&
                      !isChoosingCardAction &&
                      !isAwaitingActionTarget &&
                      phase === 'playing'
                    )
                  }
                  onDrawCard={handleDrawCard}
                  isMobile={true}
                  isSelectingActionTarget={isAwaitingActionTarget}
                />
              </div>

              {/* Right Player */}
              <div className="flex-shrink-0 flex justify-end items-start">
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
                      shouldAllowOpponentCardInteractions(right.player.id)
                        ? (position) =>
                            handleOpponentCardClick(right.player.id, position)
                        : undefined
                    }
                    isSelectingActionTarget={shouldAllowOpponentCardInteractions(
                      right.player.id
                    )}
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
              <div className="absolute top-4 left-1/2 transform -translate-x-1/2">
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
                    shouldAllowOpponentCardInteractions(top.player.id)
                      ? (position) =>
                          handleOpponentCardClick(top.player.id, position)
                      : undefined
                  }
                  isSelectingActionTarget={shouldAllowOpponentCardInteractions(
                    top.player.id
                  )}
                />
              </div>
            )}

            {/* Left Player */}
            {left && (
              <div className="absolute left-4 top-1/2 transform -translate-y-1/2">
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
                    shouldAllowOpponentCardInteractions(left.player.id)
                      ? (position) =>
                          handleOpponentCardClick(left.player.id, position)
                      : undefined
                  }
                  isSelectingActionTarget={shouldAllowOpponentCardInteractions(
                    left.player.id
                  )}
                />
              </div>
            )}

            {/* Right Player */}
            {right && (
              <div className="absolute right-4 top-1/2 transform -translate-y-1/2">
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
                    shouldAllowOpponentCardInteractions(right.player.id)
                      ? (position) =>
                          handleOpponentCardClick(right.player.id, position)
                      : undefined
                  }
                  isSelectingActionTarget={shouldAllowOpponentCardInteractions(
                    right.player.id
                  )}
                />
              </div>
            )}

            {/* Center - Draw & Discard Piles */}
            <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
              <DeckArea
                discardPile={discardPile}
                pendingCard={pendingCard ?? null}
                tossInRanks={tossInRanks}
                tossInQueue={tossInQueue}
                canDrawCard={
                  !!(
                    currentPlayer?.isHuman &&
                    !isSelectingSwapPosition &&
                    !isChoosingCardAction &&
                    !isAwaitingActionTarget &&
                    phase === 'playing'
                  )
                }
                onDrawCard={handleDrawCard}
                isMobile={false}
                isSelectingActionTarget={isAwaitingActionTarget}
              />
            </div>

            {/* Human Player */}
            <div className="absolute bottom-4 left-1/2 transform -translate-x-1/2">
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

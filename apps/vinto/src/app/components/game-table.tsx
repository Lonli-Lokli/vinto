// components/GameTable.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import {
  useGameStore,
  usePlayerStore,
  useGamePhaseStore,
  useActionStore,
  useTossInStore,
  useDeckStore,
} from './di-provider';
import { PlayerArea } from './player-area';
import { Card } from './card';

export const GameTable = observer(() => {
  const gameStore = useGameStore();
  const { currentPlayer, humanPlayer, players, setupPeeksRemaining } =
    usePlayerStore();
  const {
    phase,
    isSelectingSwapPosition,
    isChoosingCardAction,
    isDeclaringRank,
    isAwaitingActionTarget,
  } = useGamePhaseStore();
  const actionStore = useActionStore();
  const { actionContext, pendingCard, swapPosition } = actionStore;
  const tossInStore = useTossInStore();
  const { waitingForTossIn } = tossInStore;
  const { discardPile } = useDeckStore();

  // Calculate final scores if in scoring phase
  const finalScores =
    phase === 'scoring' ? gameStore.calculateFinalScores() : undefined;

  // Determine if card interactions should be enabled
  const shouldAllowCardInteractions = () => {
    if (!humanPlayer) return false;

    // For Queen action (peek-then-swap), disable when 2 cards already selected
    if (
      isAwaitingActionTarget &&
      actionContext?.targetType === 'peek-then-swap' &&
      actionStore.hasCompletePeekSelection
    ) {
      return false;
    }

    // For own-card peek (7/8), disable after one card is revealed
    if (
      isAwaitingActionTarget &&
      actionContext?.targetType === 'own-card' &&
      humanPlayer.temporarilyVisibleCards.size > 0
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
        (actionContext?.targetType === 'own-card' ||
          actionContext?.targetType === 'peek-then-swap' ||
          actionContext?.targetType === 'swap-cards'))
    );
  };

  const handleCardClick = (position: number) => {
    if (!humanPlayer) return;

    // During setup phase, allow peeking at cards for memorization
    if (phase === 'setup') {
      if (
        setupPeeksRemaining > 0 &&
        !humanPlayer.knownCardPositions.has(position)
      ) {
        gameStore.peekCard(humanPlayer.id, position);
      }
      return;
    }

    // If selecting swap position, perform the swap
    if (isSelectingSwapPosition) {
      gameStore.swapCard(position);
      return;
    }

    // During toss-in period, allow tossing in cards
    if (waitingForTossIn) {
      gameStore.tossInCard(humanPlayer.id, position);
      return;
    }

    // During action target selection, allow selecting target
    if (
      isAwaitingActionTarget &&
      (actionContext?.targetType === 'own-card' ||
        actionContext?.targetType === 'peek-then-swap' ||
        actionContext?.targetType === 'swap-cards')
    ) {
      gameStore.selectActionTarget(humanPlayer.id, position);
      return;
    }
  };

  // Determine if opponent card interactions should be enabled
  const shouldAllowOpponentCardInteractions = () => {
    // For Queen action (peek-then-swap), disable when 2 cards already selected
    if (
      isAwaitingActionTarget &&
      actionContext?.targetType === 'peek-then-swap' &&
      actionStore.hasCompletePeekSelection
    ) {
      return false;
    }

    // For opponent-card peek (9/10), disable after one card is revealed
    if (
      isAwaitingActionTarget &&
      actionContext?.targetType === 'opponent-card' &&
      humanPlayer &&
      humanPlayer.temporarilyVisibleCards.size > 0
    ) {
      return false;
    }

    return (
      isAwaitingActionTarget &&
      (actionContext?.targetType === 'opponent-card' ||
        actionContext?.targetType === 'force-draw' ||
        actionContext?.targetType === 'peek-then-swap' ||
        actionContext?.targetType === 'swap-cards')
    );
  };

  const handleOpponentCardClick = (playerId: string, position: number) => {
    console.log('handleOpponentCardClick called:', {
      playerId,
      position,
      isAwaitingActionTarget,
      targetType: actionContext?.targetType,
    });

    // During action target selection for opponent cards, Queen peek-then-swap, or Jack swaps
    if (
      isAwaitingActionTarget &&
      (actionContext?.targetType === 'opponent-card' ||
        actionContext?.targetType === 'force-draw' ||
        actionContext?.targetType === 'peek-then-swap' ||
        actionContext?.targetType === 'swap-cards')
    ) {
      console.log('Calling selectActionTarget');
      const result = gameStore.selectActionTarget(playerId, position);
      console.log('selectActionTarget result:', result);
      return;
    } else {
      console.log(
        'DEBUG - conditions not met, not calling selectActionTarget',
        {
          isAwaitingActionTarget,
          targetType: actionContext?.targetType,
        }
      );
    }
  };

  const handleDrawCard = () => {
    if (currentPlayer && currentPlayer.isHuman) {
      gameStore.drawCard();
    }
  };

  const playersById = {
    top: players.find((p) => p.position === 'top'),
    left: players.find((p) => p.position === 'left'),
    right: players.find((p) => p.position === 'right'),
  };
  const top = playersById.top;
  const left = playersById.left;
  const right = playersById.right;

  return (
    <div className="h-full flex flex-col">
      <div className="w-full h-full max-w-lg md:max-w-full mx-auto flex flex-col">
        {/* Mobile stacked layout: 3 rows (no overlap) */}
        <div className="md:hidden h-full flex flex-col bg-gradient-to-br from-poker-green-600 to-poker-green-700 rounded-lg border border-poker-green-800 shadow-lg p-2 overflow-hidden">
          {/* Row 1: Top player */}
          {top && (
            <div className="flex justify-center flex-shrink-0 pb-2">
              <PlayerArea
                player={top}
                isCurrentPlayer={currentPlayer?.id === top.id}
                isThinking={
                  gameStore.aiThinking && currentPlayer?.id === top.id
                }
                gamePhase={phase}
                finalScores={finalScores}
                onCardClick={
                  shouldAllowOpponentCardInteractions()
                    ? (position) => handleOpponentCardClick(top.id, position)
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
                  player={left}
                  isCurrentPlayer={currentPlayer?.id === left.id}
                  isThinking={
                    gameStore.aiThinking && currentPlayer?.id === left.id
                  }
                  gamePhase={phase}
                  finalScores={finalScores}
                  onCardClick={
                    shouldAllowOpponentCardInteractions()
                      ? (position) =>
                          handleOpponentCardClick(left.id, position)
                      : undefined
                  }
                  isSelectingActionTarget={shouldAllowOpponentCardInteractions()}
                />
              )}
            </div>

            {/* Center draw/discard */}
            <div className="flex flex-col items-center justify-center gap-2 flex-shrink-0 relative">
              {/* Draw Pile */}
              <div className="text-center" data-deck-pile="true">
                <Card
                  size="lg"
                  clickable={
                    currentPlayer?.isHuman &&
                    !isSelectingSwapPosition &&
                    !isChoosingCardAction &&
                    !isAwaitingActionTarget &&
                    !isDeclaringRank &&
                    phase === 'playing'
                  }
                  onClick={handleDrawCard}
                />
                <div className="mt-1 text-2xs text-white font-medium bg-black/30 rounded px-2 py-0.5">
                  DRAW
                </div>
              </div>

              {/* Drawn Card (when choosing action, selecting swap position, or declaring rank) */}
              {pendingCard &&
                (isChoosingCardAction ||
                  isSelectingSwapPosition ||
                  isDeclaringRank) && (
                  <div className="text-center">
                    <div className="relative">
                      <Card
                        card={pendingCard}
                        revealed={true}
                        size="lg"
                        highlighted={true}
                      />
                      <div className="absolute -top-1.5 -left-1.5 w-4 h-4 bg-amber-500 text-white rounded-full text-2xs font-bold flex items-center justify-center animate-pulse">
                        !
                      </div>
                    </div>
                    <div className="mt-1 text-2xs text-white font-medium bg-amber-600/80 rounded px-2 py-0.5">
                      DRAWN
                    </div>
                    {isChoosingCardAction && pendingCard.action && (
                      <div className="mt-1 text-2xs text-white bg-blue-600/80 rounded px-1 py-0.5">
                        {pendingCard.action}
                      </div>
                    )}
                  </div>
                )}

              {/* Discard Pile */}
              <div className="text-center" data-discard-pile="true">
                <Card
                  card={discardPile[0]}
                  revealed={discardPile.length > 0}
                  size="lg"
                />
                <div className="mt-1 text-2xs text-white font-medium bg-black/30 rounded px-2 py-0.5">
                  DISCARD
                </div>
              </div>
            </div>

            {/* Right Player */}
            <div className="flex-1 flex justify-end items-start">
              {right && (
                <PlayerArea
                  player={right}
                  isCurrentPlayer={currentPlayer?.id === right.id}
                  isThinking={
                    gameStore.aiThinking && currentPlayer?.id === right.id
                  }
                  gamePhase={phase}
                  finalScores={finalScores}
                  onCardClick={
                    shouldAllowOpponentCardInteractions()
                      ? (position) =>
                          handleOpponentCardClick(right.id, position)
                      : undefined
                  }
                  isSelectingActionTarget={shouldAllowOpponentCardInteractions()}
                />
              )}
            </div>
          </div>

          {/* Row 3: Human player */}
          <div className="flex justify-center flex-shrink-0 pt-2">
            {humanPlayer && (
              <PlayerArea
                player={humanPlayer}
                isCurrentPlayer={currentPlayer?.id === humanPlayer.id}
                isThinking={false}
                onCardClick={
                  shouldAllowCardInteractions() ? handleCardClick : undefined
                }
                gamePhase={phase}
                finalScores={finalScores}
                isSelectingSwapPosition={isSelectingSwapPosition}
                isDeclaringRank={isDeclaringRank}
                swapPosition={swapPosition}
                isSelectingActionTarget={shouldAllowCardInteractions()}
              />
            )}
          </div>
        </div>

        {/* Desktop/Tablet wide board */}
        <div className="hidden md:block relative bg-gradient-to-br from-poker-green-600 to-poker-green-700 rounded-lg border border-poker-green-800 shadow-lg p-3 w-full h-full min-h-0">
          {/* Top Player */}
          {top && (
            <div className="absolute top-4 left-1/2 -translate-x-1/2">
              <PlayerArea
                player={top}
                isCurrentPlayer={currentPlayer?.id === top.id}
                isThinking={
                  gameStore.aiThinking && currentPlayer?.id === top.id
                }
                gamePhase={phase}
                finalScores={finalScores}
                onCardClick={
                  shouldAllowOpponentCardInteractions()
                    ? (position) => handleOpponentCardClick(top.id, position)
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
                player={left}
                isCurrentPlayer={currentPlayer?.id === left.id}
                isThinking={
                  gameStore.aiThinking && currentPlayer?.id === left.id
                }
                gamePhase={phase}
                finalScores={finalScores}
                onCardClick={
                  shouldAllowOpponentCardInteractions()
                    ? (position) => handleOpponentCardClick(left.id, position)
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
                player={right}
                isCurrentPlayer={currentPlayer?.id === right.id}
                isThinking={
                  gameStore.aiThinking && currentPlayer?.id === right.id
                }
                gamePhase={phase}
                finalScores={finalScores}
                onCardClick={
                  shouldAllowOpponentCardInteractions()
                    ? (position) => handleOpponentCardClick(right.id, position)
                    : undefined
                }
                isSelectingActionTarget={shouldAllowOpponentCardInteractions()}
              />
            </div>
          )}

          {/* Center - Draw & Discard Piles */}
          <div className="absolute inset-0 flex items-center justify-center">
            <div className="flex gap-12 items-center">
              {/* Draw Pile */}
              <div className="text-center">
                <Card
                  size="lg"
                  clickable={
                    currentPlayer?.isHuman &&
                    !isSelectingSwapPosition &&
                    !isChoosingCardAction &&
                    !isAwaitingActionTarget &&
                    !isDeclaringRank &&
                    phase === 'playing'
                  }
                  onClick={handleDrawCard}
                />
                <div className="mt-2 text-xs text-white font-medium bg-black/30 rounded px-2 py-1">
                  DRAW
                </div>
              </div>

              {/* Drawn Card (when choosing action, selecting swap position, or declaring rank) */}
              {pendingCard &&
                (isChoosingCardAction ||
                  isSelectingSwapPosition ||
                  isDeclaringRank) && (
                  <div className="text-center">
                    <div className="relative">
                      <Card
                        card={pendingCard}
                        revealed={true}
                        size="lg"
                        highlighted={true}
                      />
                      <div className="absolute -top-2 -left-2 w-5 h-5 bg-amber-500 text-white rounded-full text-xs font-bold flex items-center justify-center animate-pulse">
                        !
                      </div>
                    </div>
                    <div className="mt-2 text-xs text-white font-medium bg-amber-600/80 rounded px-2 py-1">
                      DRAWN
                    </div>
                    {isChoosingCardAction && pendingCard.action && (
                      <div className="mt-1 text-2xs text-white bg-blue-600/80 rounded px-2 py-0.5">
                        {pendingCard.action}
                      </div>
                    )}
                  </div>
                )}

              {/* Discard Pile */}
              <div className="text-center">
                <Card
                  card={discardPile[0]}
                  revealed={discardPile.length > 0}
                  size="lg"
                />
                <div className="mt-2 text-xs text-white font-medium bg-black/30 rounded px-2 py-1">
                  DISCARD
                </div>
              </div>
            </div>
          </div>

          {/* Human Player */}
          <div className="absolute bottom-4 left-1/2 -translate-x-1/2">
            {humanPlayer && (
              <PlayerArea
                player={humanPlayer}
                isCurrentPlayer={currentPlayer?.id === humanPlayer.id}
                isThinking={false}
                onCardClick={
                  shouldAllowCardInteractions() ? handleCardClick : undefined
                }
                gamePhase={phase}
                finalScores={finalScores}
                isSelectingSwapPosition={isSelectingSwapPosition}
                isDeclaringRank={isDeclaringRank}
                swapPosition={swapPosition}
                isSelectingActionTarget={shouldAllowCardInteractions()}
              />
            )}
          </div>
        </div>
      </div>
    </div>
  );
});

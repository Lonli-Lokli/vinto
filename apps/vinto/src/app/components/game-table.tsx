// components/GameTable.tsx
'use client';

import React from 'react';
import { useGameStore } from '../stores/game-store';
import { PlayerArea } from './player-area';
import { Card } from './card';

export function GameTable() {
  const {
    players,
    currentPlayerIndex,
    aiThinking,
    phase,
    isSelectingSwapPosition,
    isChoosingCardAction,
    isAwaitingActionTarget,
    actionContext,
    setupPeeksRemaining,
    waitingForTossIn,
    tossInTimer,
    pendingCard,
    discardPile,
    drawCard,
    peekCard,
    swapCard,
    tossInCard,
    selectActionTarget,
    calculateFinalScores,
  } = useGameStore();

  const currentPlayer = players[currentPlayerIndex];
  const humanPlayer = players.find((p) => p.isHuman);

  // Calculate final scores if in scoring phase
  const finalScores = phase === 'scoring' ? calculateFinalScores() : undefined;

  const handleCardClick = (position: number) => {
    if (!humanPlayer) return;

    // During setup phase, allow peeking at cards for memorization
    if (phase === 'setup') {
      if (
        setupPeeksRemaining > 0 &&
        !humanPlayer.knownCardPositions.has(position)
      ) {
        peekCard(humanPlayer.id, position);
      }
      return;
    }

    // If selecting swap position, perform the swap
    if (isSelectingSwapPosition) {
      swapCard(position);
      return;
    }

    // During toss-in period, allow tossing in cards
    if (waitingForTossIn) {
      tossInCard(humanPlayer.id, position);
      return;
    }

    // During action target selection, allow selecting target
    if (isAwaitingActionTarget && actionContext?.targetType === 'own-card') {
      selectActionTarget(humanPlayer.id, position);
      return;
    }
  };

  const handleOpponentCardClick = (playerId: string, position: number) => {
    // During action target selection for opponent cards
    if (
      isAwaitingActionTarget &&
      actionContext?.targetType === 'opponent-card'
    ) {
      selectActionTarget(playerId, position);
      return;
    }
  };

  const handleDrawCard = () => {
    if (currentPlayer && currentPlayer.isHuman) {
      drawCard();
    }
  };
  const playersById = {
    top: players.find((p) => p.position === 'top'),
    left: players.find((p) => p.position === 'left'),
    right: players.find((p) => p.position === 'right'),
  };

  return (
    <div className="p-2 sm:p-4">
      <div className="max-w-lg mx-auto">
        {/* Mobile stacked layout: 3 rows (no overlap) */}
        <div className="md:hidden flex flex-col gap-3 bg-gradient-to-br from-emerald-600 to-emerald-700 rounded-3xl border-4 border-emerald-800 shadow-2xl p-3">
          {/* Row 1: Top player */}
          {playersById.top && (
            <div className="flex justify-center">
              <div className="relative">
                {currentPlayer?.id === playersById.top.id && (
                  <div className="absolute -top-2 -right-2 w-4 h-4 bg-green-500 rounded-full border-2 border-white shadow-md animate-pulse z-10">
                    <div className="absolute inset-0 bg-green-300 rounded-full animate-ping"></div>
                  </div>
                )}
                <PlayerArea
                  player={playersById.top}
                  isCurrentPlayer={currentPlayer?.id === playersById.top.id}
                  isThinking={
                    aiThinking && currentPlayer?.id === playersById.top.id
                  }
                  gamePhase={phase}
                  finalScores={finalScores}
                  onCardClick={(position) =>
                    handleOpponentCardClick(playersById.top!.id, position)
                  }
                />
              </div>
            </div>
          )}

          {/* Row 2: Left | Center piles | Right */}
          <div className="flex items-center justify-between gap-3">
            {/* Left Player */}
            <div className="flex-1 flex justify-start">
              {playersById.left && (
                <div className="relative">
                  {currentPlayer?.id === playersById.left.id && (
                    <div className="absolute -top-2 -right-2 w-4 h-4 bg-green-500 rounded-full border-2 border-white shadow-md animate-pulse z-10">
                      <div className="absolute inset-0 bg-green-300 rounded-full animate-ping"></div>
                    </div>
                  )}
                  <PlayerArea
                    player={playersById.left}
                    isCurrentPlayer={currentPlayer?.id === playersById.left.id}
                    isThinking={
                      aiThinking && currentPlayer?.id === playersById.left.id
                    }
                    gamePhase={phase}
                    finalScores={finalScores}
                    onCardClick={(position) =>
                      handleOpponentCardClick(playersById.left!.id, position)
                    }
                  />
                </div>
              )}
            </div>

            {/* Center draw/discard */}
            <div className="flex flex-col items-center justify-center gap-3">
              {/* Draw Pile */}
              <div className="text-center">
                <Card
                  size="md"
                  clickable={
                    currentPlayer?.isHuman &&
                    !isSelectingSwapPosition &&
                    phase === 'playing'
                  }
                  onClick={handleDrawCard}
                />
                <div className="mt-1 text-[10px] text-white font-semibold bg-black/20 rounded px-2 py-0.5">
                  DRAW
                </div>
              </div>

              {/* Drawn Card (when choosing action or selecting swap position) */}
              {pendingCard && (isChoosingCardAction || isSelectingSwapPosition) && (
                <div className="text-center">
                  <div className="relative">
                    <Card
                      card={pendingCard}
                      revealed={true}
                      size="md"
                      highlighted={true}
                    />
                    <div className="absolute -top-1.5 -left-1.5 w-5 h-5 bg-yellow-400 text-black rounded-full text-xs font-bold flex items-center justify-center animate-pulse">
                      !
                    </div>
                  </div>
                  <div className="mt-1 text-[10px] text-white font-semibold bg-yellow-500/80 rounded px-2 py-0.5">
                    DRAWN
                  </div>
                  {isChoosingCardAction && pendingCard.action && (
                    <div className="mt-1 text-[9px] text-white bg-blue-500/80 rounded px-1 py-0.5">
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
                  size="md"
                />
                <div className="mt-1 text-[10px] text-white font-semibold bg-black/20 rounded px-2 py-0.5">
                  DISCARD
                </div>
              </div>
            </div>

            {/* Toss-in Timer */}
            {waitingForTossIn && tossInTimer > 0 && (
              <div className="absolute top-1/2 left-1/2 transform -translate-x-1/2 -translate-y-1/2 pointer-events-none">
                <div className="bg-yellow-500 text-white font-bold px-3 py-2 rounded-xl shadow-lg border-2 border-yellow-600 animate-pulse">
                  <div className="text-center">
                    <div className="text-lg font-black">{tossInTimer}</div>
                    <div className="text-[10px] leading-tight">TOSS IN</div>
                  </div>
                </div>
              </div>
            )}

            {/* Right Player */}
            <div className="flex-1 flex justify-end">
              {playersById.right && (
                <div className="relative">
                  {currentPlayer?.id === playersById.right.id && (
                    <div className="absolute -top-2 -left-2 w-4 h-4 bg-green-500 rounded-full border-2 border-white shadow-md animate-pulse z-10">
                      <div className="absolute inset-0 bg-green-300 rounded-full animate-ping"></div>
                    </div>
                  )}
                  <PlayerArea
                    player={playersById.right}
                    isCurrentPlayer={currentPlayer?.id === playersById.right.id}
                    isThinking={
                      aiThinking && currentPlayer?.id === playersById.right.id
                    }
                    gamePhase={phase}
                    finalScores={finalScores}
                    onCardClick={(position) =>
                      handleOpponentCardClick(playersById.right!.id, position)
                    }
                  />
                </div>
              )}
            </div>
          </div>

          {/* Row 3: Human player */}
          <div className="flex justify-center">
            {humanPlayer && (
              <div className="relative">
                {currentPlayer?.id === humanPlayer.id && (
                  <div className="absolute -top-2 -right-2 w-4 h-4 bg-green-500 rounded-full border-2 border-white shadow-md animate-pulse z-10">
                    <div className="absolute inset-0 bg-green-300 rounded-full animate-ping"></div>
                  </div>
                )}
                <PlayerArea
                  player={humanPlayer}
                  isCurrentPlayer={currentPlayer?.id === humanPlayer.id}
                  isThinking={false}
                  onCardClick={handleCardClick}
                  gamePhase={phase}
                  finalScores={finalScores}
                  isSelectingSwapPosition={isSelectingSwapPosition}
                />
              </div>
            )}
          </div>
        </div>

        {/* Desktop/Tablet square board */}
        <div className="hidden md:block relative bg-gradient-to-br from-emerald-600 to-emerald-700 rounded-3xl aspect-square border-4 border-emerald-800 shadow-2xl p-3 sm:p-6">
          {/* Top Player */}
          {playersById.top && (
            <div className="absolute top-4 left-1/2 -translate-x-1/2">
              <div className="relative">
                {currentPlayer?.id === playersById.top.id && (
                  <div className="absolute -top-2 -right-2 w-5 h-5 bg-green-500 rounded-full border-2 border-white shadow-md animate-pulse z-10">
                    <div className="absolute inset-0 bg-green-300 rounded-full animate-ping"></div>
                  </div>
                )}
                <PlayerArea
                  player={playersById.top}
                  isCurrentPlayer={currentPlayer?.id === playersById.top.id}
                  isThinking={
                    aiThinking && currentPlayer?.id === playersById.top.id
                  }
                  gamePhase={phase}
                  finalScores={finalScores}
                  onCardClick={(position) =>
                    handleOpponentCardClick(playersById.top!.id, position)
                  }
                />
              </div>
            </div>
          )}

          {/* Left Player */}
          {playersById.left && (
            <div className="absolute left-4 top-1/2 -translate-y-1/2">
              <div className="relative">
                {currentPlayer?.id === playersById.left.id && (
                  <div className="absolute -top-2 -right-2 w-5 h-5 bg-green-500 rounded-full border-2 border-white shadow-md animate-pulse z-10">
                    <div className="absolute inset-0 bg-green-300 rounded-full animate-ping"></div>
                  </div>
                )}
                <PlayerArea
                  player={playersById.left}
                  isCurrentPlayer={currentPlayer?.id === playersById.left.id}
                  isThinking={
                    aiThinking && currentPlayer?.id === playersById.left.id
                  }
                  gamePhase={phase}
                  finalScores={finalScores}
                  onCardClick={(position) =>
                    handleOpponentCardClick(playersById.left!.id, position)
                  }
                />
              </div>
            </div>
          )}

          {/* Right Player */}
          {playersById.right && (
            <div className="absolute right-4 top-1/2 -translate-y-1/2">
              <div className="relative">
                {currentPlayer?.id === playersById.right.id && (
                  <div className="absolute -top-2 -left-2 w-5 h-5 bg-green-500 rounded-full border-2 border-white shadow-md animate-pulse z-10">
                    <div className="absolute inset-0 bg-green-300 rounded-full animate-ping"></div>
                  </div>
                )}
                <PlayerArea
                  player={playersById.right}
                  isCurrentPlayer={currentPlayer?.id === playersById.right.id}
                  isThinking={
                    aiThinking && currentPlayer?.id === playersById.right.id
                  }
                  gamePhase={phase}
                  finalScores={finalScores}
                  onCardClick={(position) =>
                    handleOpponentCardClick(playersById.right!.id, position)
                  }
                />
              </div>
            </div>
          )}

          {/* Center - Draw & Discard Piles */}
          <div className="absolute inset-0 flex items-center justify-center">
            <div className="flex gap-8">
              {/* Draw Pile */}
              <div className="text-center">
                <Card
                  size="lg"
                  clickable={
                    currentPlayer?.isHuman &&
                    !isSelectingSwapPosition &&
                    phase === 'playing'
                  }
                  onClick={handleDrawCard}
                />
                <div className="mt-2 text-xs text-white font-semibold bg-black/20 rounded px-2 py-1">
                  DRAW
                </div>
              </div>

              {/* Drawn Card (when choosing action or selecting swap position) */}
              {pendingCard && (isChoosingCardAction || isSelectingSwapPosition) && (
                <div className="text-center">
                  <div className="relative">
                    <Card
                      card={pendingCard}
                      revealed={true}
                      size="lg"
                      highlighted={true}
                    />
                    <div className="absolute -top-2 -left-2 w-6 h-6 bg-yellow-400 text-black rounded-full text-sm font-bold flex items-center justify-center animate-pulse">
                      !
                    </div>
                  </div>
                  <div className="mt-2 text-xs text-white font-semibold bg-yellow-500/80 rounded px-2 py-1">
                    DRAWN
                  </div>
                  {isChoosingCardAction && pendingCard.action && (
                    <div className="mt-1 text-[10px] text-white bg-blue-500/80 rounded px-2 py-0.5">
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
                <div className="mt-2 text-xs text-white font-semibold bg-black/20 rounded px-2 py-1">
                  DISCARD
                </div>
              </div>
            </div>

            {/* Toss-in Timer (Desktop) */}
            {waitingForTossIn && tossInTimer > 0 && (
              <div className="absolute top-1/2 left-1/2 transform -translate-x-1/2 -translate-y-1/2 pointer-events-none">
                <div className="bg-yellow-500 text-white font-bold px-4 py-3 rounded-xl shadow-xl border-2 border-yellow-600 animate-pulse">
                  <div className="text-center">
                    <div className="text-2xl font-black">{tossInTimer}</div>
                    <div className="text-xs leading-tight">TOSS IN</div>
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* Human Player */}
          <div className="absolute bottom-4 left-1/2 -translate-x-1/2">
            {humanPlayer && (
              <div className="relative">
                {currentPlayer?.id === humanPlayer.id && (
                  <div className="absolute -top-2 -right-2 w-5 h-5 bg-green-500 rounded-full border-2 border-white shadow-md animate-pulse z-10">
                    <div className="absolute inset-0 bg-green-300 rounded-full animate-ping"></div>
                  </div>
                )}
                <PlayerArea
                  player={humanPlayer}
                  isCurrentPlayer={currentPlayer?.id === humanPlayer.id}
                  isThinking={false}
                  onCardClick={handleCardClick}
                  gamePhase={phase}
                  finalScores={finalScores}
                  isSelectingSwapPosition={isSelectingSwapPosition}
                />
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

// components/GameTable.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { gameStore } from '../stores/game-store-mobx';
import { PlayerArea } from './player-area';
import { Card } from './card';

export const GameTable = observer(() => {
  const currentPlayer = gameStore.players[gameStore.currentPlayerIndex];
  const humanPlayer = gameStore.players.find((p) => p.isHuman);

  // Calculate final scores if in scoring phase
  const finalScores =
    gameStore.phase === 'scoring'
      ? gameStore.calculateFinalScores()
      : undefined;

  const handleCardClick = (position: number) => {
    if (!humanPlayer) return;

    // During setup phase, allow peeking at cards for memorization
    if (gameStore.phase === 'setup') {
      if (
        gameStore.setupPeeksRemaining > 0 &&
        !humanPlayer.knownCardPositions.has(position)
      ) {
        gameStore.peekCard(humanPlayer.id, position);
      }
      return;
    }

    // If selecting swap position, perform the swap
    if (gameStore.isSelectingSwapPosition) {
      gameStore.swapCard(position);
      return;
    }

    // During toss-in period, allow tossing in cards
    if (gameStore.waitingForTossIn) {
      gameStore.tossInCard(humanPlayer.id, position);
      return;
    }

    // During action target selection, allow selecting target
    if (
      gameStore.isAwaitingActionTarget &&
      (gameStore.actionContext?.targetType === 'own-card' ||
        gameStore.actionContext?.targetType === 'peek-then-swap' ||
        gameStore.actionContext?.targetType === 'swap-cards')
    ) {
      gameStore.selectActionTarget(humanPlayer.id, position);
      return;
    }
  };

  const handleOpponentCardClick = (playerId: string, position: number) => {
    // During action target selection for opponent cards, Queen peek-then-swap, or Jack swaps
    if (
      gameStore.isAwaitingActionTarget &&
      (gameStore.actionContext?.targetType === 'opponent-card' ||
        gameStore.actionContext?.targetType === 'force-draw' ||
        gameStore.actionContext?.targetType === 'peek-then-swap' ||
        gameStore.actionContext?.targetType === 'swap-cards')
    ) {
      gameStore.selectActionTarget(playerId, position);
      return;
    }
  };

  const handleDrawCard = () => {
    if (currentPlayer && currentPlayer.isHuman) {
      gameStore.drawCard();
    }
  };

  const playersById = {
    top: gameStore.players.find((p) => p.position === 'top'),
    left: gameStore.players.find((p) => p.position === 'left'),
    right: gameStore.players.find((p) => p.position === 'right'),
  };
  const top = playersById.top;
  const left = playersById.left;
  const right = playersById.right;

  return (
    <div className="p-1 sm:p-2">
      <div className="w-full max-w-lg md:max-w-full mx-auto">
        {/* Mobile stacked layout: 3 rows (no overlap) */}
        <div className="md:hidden flex flex-col gap-3 bg-gradient-to-br from-emerald-600 to-emerald-700 rounded-3xl border-4 border-emerald-800 shadow-2xl p-3">
          {/* Row 1: Top player */}
          {top && (
            <div className="flex justify-center">
              <PlayerArea
                player={top}
                isCurrentPlayer={currentPlayer?.id === top.id}
                isThinking={
                  gameStore.aiThinking && currentPlayer?.id === top.id
                }
                gamePhase={gameStore.phase}
                finalScores={finalScores}
                onCardClick={(position) =>
                  handleOpponentCardClick(top.id, position)
                }
              />
            </div>
          )}

          {/* Row 2: Left | Center piles | Right */}
          <div className="flex items-center justify-between gap-3">
            {/* Left Player */}
            <div className="flex-1 flex justify-start">
              {left && (
                <div className="relative">
                  <PlayerArea
                    player={left}
                    isCurrentPlayer={currentPlayer?.id === left.id}
                    isThinking={
                      gameStore.aiThinking && currentPlayer?.id === left.id
                    }
                    gamePhase={gameStore.phase}
                    finalScores={finalScores}
                    onCardClick={(position) =>
                      handleOpponentCardClick(left.id, position)
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
                    !gameStore.isSelectingSwapPosition &&
                    !gameStore.isChoosingCardAction &&
                    !gameStore.isAwaitingActionTarget &&
                    !gameStore.isDeclaringRank &&
                    gameStore.phase === 'playing'
                  }
                  onClick={handleDrawCard}
                />
                <div className="mt-1 text-[10px] text-white font-semibold bg-black/20 rounded px-2 py-0.5">
                  DRAW
                </div>
              </div>

              {/* Drawn Card (when choosing action, selecting swap position, or declaring rank) */}
              {gameStore.pendingCard &&
                (gameStore.isChoosingCardAction ||
                  gameStore.isSelectingSwapPosition ||
                  gameStore.isDeclaringRank) && (
                  <div className="text-center">
                    <div className="relative">
                      <Card
                        card={gameStore.pendingCard}
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
                    {gameStore.isChoosingCardAction &&
                      gameStore.pendingCard.action && (
                        <div className="mt-1 text-[9px] text-white bg-blue-500/80 rounded px-1 py-0.5">
                          {gameStore.pendingCard.action}
                        </div>
                      )}
                  </div>
                )}

              {/* Discard Pile */}
              <div className="text-center">
                <Card
                  card={gameStore.discardPile[0]}
                  revealed={gameStore.discardPile.length > 0}
                  size="md"
                />
                <div className="mt-1 text-[10px] text-white font-semibold bg-black/20 rounded px-2 py-0.5">
                  DISCARD
                </div>
              </div>
            </div>

            {/* Toss-in Timer */}
            {gameStore.waitingForTossIn && (
              <div className="absolute top-1/2 left-1/2 transform -translate-x-1/2 -translate-y-1/2 pointer-events-none">
                <div className="bg-yellow-500 text-white font-bold px-3 py-2 rounded-xl shadow-lg border-2 border-yellow-600 animate-pulse">
                  <div className="text-center">
                    <div className="text-lg font-black">
                      {gameStore.tossInTimer}
                    </div>
                    <div className="text-[10px] leading-tight">TOSS IN</div>
                  </div>
                </div>
              </div>
            )}

            {/* Right Player */}
            <div className="flex-1 flex justify-end">
              {right && (
                <div className="relative">
                  <PlayerArea
                    player={right}
                    isCurrentPlayer={currentPlayer?.id === right.id}
                    isThinking={
                      gameStore.aiThinking && currentPlayer?.id === right.id
                    }
                    gamePhase={gameStore.phase}
                    finalScores={finalScores}
                    onCardClick={(position) =>
                      handleOpponentCardClick(right.id, position)
                    }
                  />
                </div>
              )}
            </div>
          </div>

          {/* Row 3: Human player */}
          <div className="flex justify-center">
            {humanPlayer && (
              <PlayerArea
                player={humanPlayer}
                isCurrentPlayer={currentPlayer?.id === humanPlayer.id}
                isThinking={false}
                onCardClick={handleCardClick}
                gamePhase={gameStore.phase}
                finalScores={finalScores}
                isSelectingSwapPosition={gameStore.isSelectingSwapPosition}
                isDeclaringRank={gameStore.isDeclaringRank}
                swapPosition={gameStore.swapPosition}
              />
            )}
          </div>
        </div>

        {/* Desktop/Tablet wide board */}
        <div
          className="hidden md:block relative bg-gradient-to-br from-emerald-600 to-emerald-700 rounded-2xl border-4 border-emerald-800 shadow-2xl p-4 w-full"
          style={{ aspectRatio: '16/10', maxHeight: '70vh' }}
        >
          {/* Top Player */}
          {top && (
            <div className="absolute top-4 left-1/2 -translate-x-1/2">
              <PlayerArea
                player={top}
                isCurrentPlayer={currentPlayer?.id === top.id}
                isThinking={
                  gameStore.aiThinking && currentPlayer?.id === top.id
                }
                gamePhase={gameStore.phase}
                finalScores={finalScores}
                onCardClick={(position) =>
                  handleOpponentCardClick(top.id, position)
                }
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
                gamePhase={gameStore.phase}
                finalScores={finalScores}
                onCardClick={(position) =>
                  handleOpponentCardClick(left.id, position)
                }
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
                gamePhase={gameStore.phase}
                finalScores={finalScores}
                onCardClick={(position) =>
                  handleOpponentCardClick(right.id, position)
                }
              />
            </div>
          )}

          {/* Center - Draw & Discard Piles */}
          <div className="absolute inset-0 flex items-center justify-center">
            <div className="flex gap-12 items-center">
              {/* Draw Pile */}
              <div className="text-center">
                <Card
                  size="xl"
                  clickable={
                    currentPlayer?.isHuman &&
                    !gameStore.isSelectingSwapPosition &&
                    !gameStore.isChoosingCardAction &&
                    !gameStore.isAwaitingActionTarget &&
                    !gameStore.isDeclaringRank &&
                    gameStore.phase === 'playing'
                  }
                  onClick={handleDrawCard}
                />
                <div className="mt-2 text-xs text-white font-semibold bg-black/20 rounded px-2 py-1">
                  DRAW
                </div>
              </div>

              {/* Drawn Card (when choosing action, selecting swap position, or declaring rank) */}
              {gameStore.pendingCard &&
                (gameStore.isChoosingCardAction ||
                  gameStore.isSelectingSwapPosition ||
                  gameStore.isDeclaringRank) && (
                  <div className="text-center">
                    <div className="relative">
                      <Card
                        card={gameStore.pendingCard}
                        revealed={true}
                        size="xl"
                        highlighted={true}
                      />
                      <div className="absolute -top-2 -left-2 w-6 h-6 bg-yellow-400 text-black rounded-full text-sm font-bold flex items-center justify-center animate-pulse">
                        !
                      </div>
                    </div>
                    <div className="mt-2 text-xs text-white font-semibold bg-yellow-500/80 rounded px-2 py-1">
                      DRAWN
                    </div>
                    {gameStore.isChoosingCardAction &&
                      gameStore.pendingCard.action && (
                        <div className="mt-1 text-[10px] text-white bg-blue-500/80 rounded px-2 py-0.5">
                          {gameStore.pendingCard.action}
                        </div>
                      )}
                  </div>
                )}

              {/* Discard Pile */}
              <div className="text-center">
                <Card
                  card={gameStore.discardPile[0]}
                  revealed={gameStore.discardPile.length > 0}
                  size="xl"
                />
                <div className="mt-2 text-xs text-white font-semibold bg-black/20 rounded px-2 py-1">
                  DISCARD
                </div>
              </div>
            </div>

            {/* Toss-in Timer (Desktop) */}
            {gameStore.waitingForTossIn && (
              <div className="absolute top-1/2 left-1/2 transform -translate-x-1/2 -translate-y-1/2 pointer-events-none">
                <div className="bg-yellow-500 text-white font-bold px-4 py-3 rounded-xl shadow-xl border-2 border-yellow-600 animate-pulse">
                  <div className="text-center">
                    <div className="text-2xl font-black">
                      {gameStore.tossInTimer}
                    </div>
                    <div className="text-xs leading-tight">TOSS IN</div>
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* Human Player */}
          <div className="absolute bottom-4 left-1/2 -translate-x-1/2">
            {humanPlayer && (
              <PlayerArea
                player={humanPlayer}
                isCurrentPlayer={currentPlayer?.id === humanPlayer.id}
                isThinking={false}
                onCardClick={handleCardClick}
                gamePhase={gameStore.phase}
                finalScores={finalScores}
                isSelectingSwapPosition={gameStore.isSelectingSwapPosition}
                isDeclaringRank={gameStore.isDeclaringRank}
                swapPosition={gameStore.swapPosition}
              />
            )}
          </div>
        </div>
      </div>
    </div>
  );
});

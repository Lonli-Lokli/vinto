// components/GameTable.tsx
'use client';

import React from 'react';
import { Player, Card as CardType } from '../shapes';
import { PlayerArea } from './player-area';
import { Card } from './card';

interface GameTableProps {
  players: Player[];
  currentPlayer?: Player;
  humanPlayer: Player;
  aiThinking: boolean;
  gamePhase: 'setup' | 'playing' | 'final' | 'scoring';
  finalScores?: { [playerId: string]: number };
  isSelectingSwapPosition: boolean;
  pendingCard: CardType | null;
  discardPile: CardType[];
  onCardClick: (position: number) => void;
  onDrawCard: () => void;
}

export function GameTable({
  players,
  currentPlayer,
  humanPlayer,
  aiThinking,
  gamePhase,
  finalScores,
  isSelectingSwapPosition,
  pendingCard,
  discardPile,
  onCardClick,
  onDrawCard
}: GameTableProps) {
  const playersById = {
    top: players.find(p => p.position === 'top'),
    left: players.find(p => p.position === 'left'),
    right: players.find(p => p.position === 'right')
  };

  return (
    <div className="p-2 sm:p-4">
      <div className="max-w-lg mx-auto">
        {/* Mobile stacked layout: 3 rows (no overlap) */}
  <div className="md:hidden flex flex-col gap-3 bg-gradient-to-br from-emerald-600 to-emerald-700 rounded-3xl border-4 border-emerald-800 shadow-2xl p-3">
          {/* Row 1: Top player */}
          {playersById.top && (
            <div className="flex justify-center">
              <PlayerArea
                player={playersById.top}
                isCurrentPlayer={currentPlayer?.id === playersById.top.id}
                isThinking={aiThinking && currentPlayer?.id === playersById.top.id}
                gamePhase={gamePhase}
                finalScores={finalScores}
              />
            </div>
          )}

          {/* Row 2: Left | Center piles | Right */}
          <div className="flex items-center justify-between gap-3">
            {/* Left Player */}
            <div className="flex-1 flex justify-start">
              {playersById.left && (
                <PlayerArea
                  player={playersById.left}
                  isCurrentPlayer={currentPlayer?.id === playersById.left.id}
                  isThinking={aiThinking && currentPlayer?.id === playersById.left.id}
                  gamePhase={gamePhase}
                  finalScores={finalScores}
                />
              )}
            </div>

            {/* Center draw/discard */}
            <div className="flex flex-col items-center justify-center gap-3">
              {/* Draw Pile */}
              <div className="text-center">
                <Card
                  size="md"
                  clickable={currentPlayer?.isHuman && !isSelectingSwapPosition && gamePhase === 'playing'}
                  onClick={onDrawCard}
                />
                <div className="mt-1 text-[10px] text-white font-semibold bg-black/20 rounded px-2 py-0.5">
                  DRAW
                </div>
              </div>

              {/* Drawn Card (when selecting swap position) */}
              {pendingCard && isSelectingSwapPosition && (
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

            {/* Right Player */}
            <div className="flex-1 flex justify-end">
              {playersById.right && (
                <PlayerArea
                  player={playersById.right}
                  isCurrentPlayer={currentPlayer?.id === playersById.right.id}
                  isThinking={aiThinking && currentPlayer?.id === playersById.right.id}
                  gamePhase={gamePhase}
                  finalScores={finalScores}
                />
              )}
            </div>
          </div>

          {/* Row 3: Human player */}
          <div className="flex justify-center">
            <PlayerArea
              player={humanPlayer}
              isCurrentPlayer={currentPlayer?.id === humanPlayer.id}
              isThinking={false}
              onCardClick={onCardClick}
              gamePhase={gamePhase}
              finalScores={finalScores}
              isSelectingSwapPosition={isSelectingSwapPosition}
            />
          </div>
        </div>

        {/* Desktop/Tablet square board */}
        <div className="hidden md:block relative bg-gradient-to-br from-emerald-600 to-emerald-700 rounded-3xl aspect-square border-4 border-emerald-800 shadow-2xl p-3 sm:p-6">

          {/* Top Player */}
          {playersById.top && (
            <div className="absolute top-4 left-1/2 -translate-x-1/2">
              <PlayerArea
                player={playersById.top}
                isCurrentPlayer={currentPlayer?.id === playersById.top.id}
                isThinking={aiThinking && currentPlayer?.id === playersById.top.id}
                gamePhase={gamePhase}
                finalScores={finalScores}
              />
            </div>
          )}

          {/* Left Player */}
          {playersById.left && (
            <div className="absolute left-4 top-1/2 -translate-y-1/2">
              <PlayerArea
                player={playersById.left}
                isCurrentPlayer={currentPlayer?.id === playersById.left.id}
                isThinking={aiThinking && currentPlayer?.id === playersById.left.id}
                gamePhase={gamePhase}
                finalScores={finalScores}
              />
            </div>
          )}

          {/* Right Player */}
          {playersById.right && (
            <div className="absolute right-4 top-1/2 -translate-y-1/2">
              <PlayerArea
                player={playersById.right}
                isCurrentPlayer={currentPlayer?.id === playersById.right.id}
                isThinking={aiThinking && currentPlayer?.id === playersById.right.id}
                gamePhase={gamePhase}
                finalScores={finalScores}
              />
            </div>
          )}

          {/* Center - Draw & Discard Piles */}
          <div className="absolute inset-0 flex items-center justify-center">
            <div className="flex gap-8">
              {/* Draw Pile */}
              <div className="text-center">
                <Card
                  size="lg"
                  clickable={currentPlayer?.isHuman && !isSelectingSwapPosition && gamePhase === 'playing'}
                  onClick={onDrawCard}
                />
                <div className="mt-2 text-xs text-white font-semibold bg-black/20 rounded px-2 py-1">
                  DRAW
                </div>
              </div>

              {/* Drawn Card (when selecting swap position) */}
              {pendingCard && isSelectingSwapPosition && (
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
          </div>

          {/* Human Player */}
          <div className="absolute bottom-4 left-1/2 -translate-x-1/2">
            <PlayerArea
              player={humanPlayer}
              isCurrentPlayer={currentPlayer?.id === humanPlayer.id}
              isThinking={false}
              onCardClick={onCardClick}
              gamePhase={gamePhase}
              finalScores={finalScores}
              isSelectingSwapPosition={isSelectingSwapPosition}
            />
          </div>
        </div>
      </div>
    </div>
  );
}
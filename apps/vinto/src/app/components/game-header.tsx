// components/GameHeader.tsx - Client Component (compact)
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { gameStore } from '../stores/game-store';

export const GameHeader = observer(() => {
  const currentPlayer = gameStore.players[gameStore.currentPlayerIndex];

  const getPhaseDisplay = () => {
    if (gameStore.phase === 'scoring') return 'Final Scores';
    if (gameStore.finalTurnTriggered) return `Final • ${gameStore.phase}`;
    return `R${gameStore.roundNumber} • ${gameStore.phase} • T${gameStore.turnCount}`;
  };

  const getCurrentPlayerDisplay = () => {
    if (!currentPlayer || gameStore.phase === 'scoring') return null;
    if (currentPlayer.isHuman) return `${currentPlayer.avatar} Your turn!`;
    return `${currentPlayer.avatar} ${currentPlayer.name}`;
  };

  return (
    <div className="bg-white/70 backdrop-blur-md border-b border-gray-200 flex-shrink-0">
      <div className="max-w-6xl mx-auto px-3 py-1">
        <div className="flex items-center justify-between gap-4">
          {/* Left: Settings */}
          <div className="flex items-center gap-2">
            {/* Difficulty */}
            <div className="flex items-center gap-1">
              {(['basic', 'moderate', 'hard', 'ultimate'] as const).map(
                (level) => (
                  <button
                    key={level}
                    onClick={() => gameStore.updateDifficulty(level)}
                    className={`px-2 py-1 rounded text-[10px] font-semibold transition-colors ${
                      gameStore.difficulty === level
                        ? 'bg-blue-500 text-white'
                        : 'bg-gray-100 hover:bg-gray-200 text-gray-700'
                    }`}
                    title={`Difficulty: ${level}`}
                  >
                    {level[0].toUpperCase()}
                  </button>
                )
              )}
            </div>

            {/* Toss-in time settings */}
            <div className="hidden sm:flex items-center gap-1 ml-2">
              <span className="text-xs text-gray-500 mr-1">Toss:</span>
              {([5, 7, 10] as const).map((time) => (
                <button
                  key={time}
                  onClick={() => gameStore.updateTossInTime(time)}
                  className={`px-1.5 py-0.5 rounded text-[9px] font-semibold transition-colors ${
                    gameStore.tossInTimeConfig === time
                      ? 'bg-green-500 text-white'
                      : 'bg-gray-100 hover:bg-gray-200 text-gray-700'
                  }`}
                  title={`Toss-in time: ${time}s`}
                >
                  {time}s
                </button>
              ))}
            </div>
          </div>

          {/* Center: Title + Game Info */}
          <div className="flex items-center gap-3">
            <h1 className="text-xl font-bold text-transparent bg-clip-text bg-gradient-to-r from-emerald-600 to-blue-600">
              VINTO
            </h1>
            <div className="text-sm text-gray-600 font-medium">
              {getPhaseDisplay()}
            </div>
            {getCurrentPlayerDisplay() && (
              <div className="text-sm font-medium text-blue-600 hidden sm:block">
                {getCurrentPlayerDisplay()}
              </div>
            )}
          </div>

          {/* Right: Cards Left */}
          <div className="flex items-center gap-1">
            <div className="text-base font-semibold text-gray-700">
              {gameStore.drawPile.length}
            </div>
            <div className="text-xs text-gray-500">cards</div>
          </div>
        </div>
      </div>
    </div>
  );
});

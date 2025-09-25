// components/GameHeader.tsx - Client Component (compact)
'use client';

import React from 'react';
import { useGameStore } from '../stores/game-store';

export function GameHeader() {
  const {
    phase,
    roundNumber,
    turnCount,
    finalTurnTriggered,
    drawPile,
    players,
    currentPlayerIndex,
    difficulty,
    tossInTimeConfig,
    updateDifficulty,
    updateTossInTime,
  } = useGameStore();

  const currentPlayer = players[currentPlayerIndex];
  const getPhaseDisplay = () => {
    if (phase === 'scoring') return 'Final Scores';
    if (finalTurnTriggered) return `Final • ${phase}`;
    return `R${roundNumber} • ${phase} • T${turnCount}`;
  };

  const getCurrentPlayerDisplay = () => {
    if (!currentPlayer || phase === 'scoring') return null;
    if (currentPlayer.isHuman) return `${currentPlayer.avatar} Your turn!`;
    return `${currentPlayer.avatar} ${currentPlayer.name}`;
  };

  return (
    <div className="sticky top-0 z-10 bg-white/70 backdrop-blur-md border-b border-gray-200">
      <div className="max-w-4xl mx-auto px-3 py-2">
        <div className="flex items-center justify-between gap-4">
          {/* Left: Settings */}
          <div className="flex items-center gap-2">
            {/* Difficulty */}
            <div className="flex items-center gap-1">
              {(['basic', 'moderate', 'hard', 'ultimate'] as const).map((level) => (
                <button
                  key={level}
                  onClick={() => updateDifficulty(level)}
                  className={`px-2 py-1 rounded text-[10px] font-semibold transition-colors ${
                    difficulty === level
                      ? 'bg-blue-500 text-white'
                      : 'bg-gray-100 hover:bg-gray-200 text-gray-700'
                  }`}
                  title={`Difficulty: ${level}`}
                >
                  {level[0].toUpperCase()}
                </button>
              ))}
            </div>

            {/* Toss-in settings moved to a settings icon/menu later */}
            <div className="hidden sm:flex items-center gap-1 ml-2">
              <span className="text-xs text-gray-500">Toss: {tossInTimeConfig}s</span>
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
              {drawPile.length}
            </div>
            <div className="text-xs text-gray-500">cards</div>
          </div>
        </div>
      </div>
    </div>
  );
}
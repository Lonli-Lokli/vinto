// components/GameHeader.tsx - Client Component
'use client';

import React from 'react';
import { Difficulty } from '../shapes';

interface GameHeaderProps {
  phase: 'setup' | 'playing' | 'final' | 'scoring';
  roundNumber: number;
  turnCount: number;
  maxTurns: number;
  finalTurnTriggered: boolean;
  drawPileCount: number;
  currentPlayer: any;
  difficulty: Difficulty;
  onDifficultyChange: (diff: Difficulty) => void;
}

export function GameHeader({
  phase,
  roundNumber,
  turnCount,
  maxTurns,
  finalTurnTriggered,
  drawPileCount,
  currentPlayer,
  difficulty,
  onDifficultyChange
}: GameHeaderProps) {
  const getPhaseDisplay = () => {
    if (phase === 'scoring') return 'Final Scores';
    if (finalTurnTriggered) return `Final Turn • ${phase}`;
    return `Round ${roundNumber} • ${phase} • Turn ${turnCount}/${maxTurns}`;
  };

  const getCurrentPlayerDisplay = () => {
    if (!currentPlayer || phase === 'scoring') return null;
    return `${currentPlayer.avatar} ${currentPlayer.name}'s Turn${currentPlayer.isHuman ? ' (Your turn!)' : ''}`;
  };

  return (
    <div className="sticky top-0 z-10 bg-white/80 backdrop-blur-md border-b border-gray-200">
      <div className="max-w-lg mx-auto px-4 py-3">
        <div className="flex items-center justify-between">
          {/* Difficulty Selector */}
          <div className="flex gap-1">
            {(['easy', 'medium', 'hard'] as const).map((level) => (
              <button
                key={level}
                onClick={() => onDifficultyChange(level)}
                className={`px-3 py-1 rounded-lg text-xs font-semibold transition-all ${
                  difficulty === level
                    ? 'bg-blue-500 text-white shadow-md'
                    : 'bg-gray-100 hover:bg-gray-200 text-gray-700'
                }`}
              >
                {level.toUpperCase()}
              </button>
            ))}
          </div>

          {/* Title */}
          <div className="text-center">
            <h1 className="text-xl font-bold text-transparent bg-clip-text bg-gradient-to-r from-emerald-600 to-blue-600">
              VINTO
            </h1>
            <div className="text-xs text-gray-500">
              {getPhaseDisplay()}
            </div>
            {getCurrentPlayerDisplay() && (
              <div className="text-xs font-semibold text-blue-600 mt-1">
                {getCurrentPlayerDisplay()}
              </div>
            )}
          </div>

          {/* Game Info */}
          <div className="text-right">
            <div className="text-sm font-semibold text-gray-700">
              {drawPileCount}
            </div>
            <div className="text-xs text-gray-500">cards left</div>
          </div>
        </div>
      </div>
    </div>
  );
}
// components/GameHeader.tsx - Client Component (compact)
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
  onDifficultyChange,
}: GameHeaderProps) {
  const getPhaseDisplay = () => {
    if (phase === 'scoring') return 'Final Scores';
    if (finalTurnTriggered) return `Final • ${phase}`;
    return `R${roundNumber} • ${phase} • T${turnCount}/${maxTurns}`;
  };

  const getCurrentPlayerDisplay = () => {
    if (!currentPlayer || phase === 'scoring') return null;
    if (currentPlayer.isHuman) return `${currentPlayer.avatar} Your turn!`;
    return `${currentPlayer.avatar} ${currentPlayer.name}`;
  };

  return (
    <div className="sticky top-0 z-10 bg-white/70 backdrop-blur-md border-b border-gray-200">
      <div className="max-w-lg mx-auto px-3 py-1.5">
        <div className="grid grid-cols-3 items-center">
          {/* Difficulty Selector */}
          <div className="flex gap-1">
            {(['basic', 'moderate', 'hard', 'ultimate'] as const).map((level) => (
              <button
                key={level}
                onClick={() => onDifficultyChange(level)}
                className={`px-2 py-0.5 rounded-md text-[10px] font-semibold transition-colors ${
                  difficulty === level
                    ? 'bg-blue-500 text-white'
                    : 'bg-gray-100 hover:bg-gray-200 text-gray-700'
                }`}
                aria-pressed={difficulty === level}
                aria-label={`Set difficulty ${level}`}
                title={`Difficulty: ${level}`}
              >
                <span className="hidden md:inline">{level.toUpperCase()}</span>
                <span className="md:hidden">{level[0].toUpperCase()}</span>
              </button>
            ))}
          </div>

          {/* Title + Phase + Current Player */}
          <div className="text-center">
            <h1 className="text-lg md:text-xl font-bold text-transparent bg-clip-text bg-gradient-to-r from-emerald-600 to-blue-600">
              VINTO
            </h1>
            <div className="text-[11px] md:text-xs text-gray-500 leading-tight">
              {getPhaseDisplay()}
            </div>
            {getCurrentPlayerDisplay() && (
              <div className={`mt-0.5 font-medium text-blue-600 leading-tight ${currentPlayer?.isHuman ? 'block' : 'hidden md:block'} text-[11px] md:text-xs`}>
                {getCurrentPlayerDisplay()}
              </div>
            )}
          </div>

          {/* Game Info */}
          <div className="text-right">
            <div className="text-sm md:text-base font-semibold text-gray-700 leading-tight">
              {drawPileCount}
            </div>
            <div className="text-[10px] md:text-xs text-gray-500 leading-tight">cards left</div>
          </div>
        </div>
      </div>
    </div>
  );
}
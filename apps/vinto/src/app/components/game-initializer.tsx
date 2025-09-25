// components/GameInitializer.tsx
'use client';

import React, { useEffect } from 'react';
import { useGameStore } from '../stores/game-store';

export function GameInitializer() {
  const {
    players,
    currentPlayerIndex,
    aiThinking,
    sessionActive,
    difficulty,
    initGame,
    makeAIMove,
  } = useGameStore();

  // Initialize game on mount
  useEffect(() => {
    if (players.length === 0) {
      initGame();
    }
  }, [players.length, initGame]);

  // Handle AI turns
  useEffect(() => {
    const currentPlayer = players[currentPlayerIndex];
    if (
      currentPlayer &&
      !currentPlayer.isHuman &&
      !aiThinking &&
      sessionActive
    ) {
      const timer = setTimeout(() => {
        makeAIMove(difficulty);
      }, 1000);
      return () => clearTimeout(timer);
    }
    return () => {
      /* empty */
    };
  }, [
    currentPlayerIndex,
    aiThinking,
    makeAIMove,
    difficulty,
    players,
    sessionActive,
  ]);

  // Loading state
  if (!sessionActive || players.length === 0) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-emerald-50 to-blue-50 flex items-center justify-center">
        <div className="text-center">
          <div className="text-6xl mb-4 animate-bounce">🎮</div>
          <div className="text-xl font-semibold text-gray-700 mb-2">
            Setting up Vinto game...
          </div>
          <div className="text-sm text-gray-500">Connecting to AI servers</div>
        </div>
      </div>
    );
  }

  return null;
}
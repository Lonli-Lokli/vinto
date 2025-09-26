// components/GameInitializer.tsx
'use client';

import React, { useEffect } from 'react';
import { observer } from 'mobx-react-lite';
import { gameStore } from '../stores/game-store-mobx';

export const GameInitializer = observer(() => {
  // Initialize game on mount
  useEffect(() => {
    if (gameStore.players.length === 0) {
      gameStore.initGame();
    }
  }, [gameStore.players.length]);

  // Handle AI turns
  useEffect(() => {
    const currentPlayer = gameStore.players[gameStore.currentPlayerIndex];

    // Update vinto call availability whenever turn state changes
    gameStore.updateVintoCallAvailabilityPublic();

    // Clear temporary card visibility on each turn change
    gameStore.clearTemporaryCardVisibilityPublic();

    if (
      currentPlayer &&
      !currentPlayer.isHuman &&
      !gameStore.aiThinking &&
      gameStore.sessionActive
    ) {
      const timer = setTimeout(() => {
        gameStore.makeAIMove(gameStore.difficulty);
      }, gameStore.tossInTimeConfig * 1_000); // as its in seconds
      return () => clearTimeout(timer);
    }
    return () => {
      /* empty */
    };
  }, [
    gameStore.currentPlayerIndex,
    gameStore.aiThinking,
    gameStore.tossInTimeConfig,
    gameStore.difficulty,
    gameStore.players,
    gameStore.sessionActive,
  ]);

  // Loading state
  if (!gameStore.sessionActive || gameStore.players.length === 0) {
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
});

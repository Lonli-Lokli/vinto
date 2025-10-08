// components/GameInitializer.tsx
'use client';

import React, { useEffect } from 'react';
import { observer } from 'mobx-react-lite';
import { useGameStore, usePlayerStore } from './di-provider';

export const GameInitializer = observer(() => {
  const gameStore = useGameStore();
  const { players } = usePlayerStore();
  // Initialize game on mount
  useEffect(() => {
    if (players.length === 0) {
      void gameStore.initGame();
    }
  }, [gameStore, players.length]);

  // Loading state
  if (!gameStore.sessionActive || players.length === 0) {
    return (
      <div className="min-h-screen bg-page-gradient flex items-center justify-center">
        <div className="text-center">
          <div className="text-6xl mb-4 animate-bounce">🎮</div>
          <div className="text-xl font-semibold text-primary mb-2">
            Setting up Vinto game...
          </div>
        </div>
      </div>
    );
  }

  return null;
});

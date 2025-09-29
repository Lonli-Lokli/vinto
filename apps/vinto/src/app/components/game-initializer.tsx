// components/GameInitializer.tsx
'use client';

import React, { useEffect } from 'react';
import { observer } from 'mobx-react-lite';
import { gameStore } from '../stores/game-store';
import { getPlayerStore } from '../stores/player-store';

export const GameInitializer = observer(() => {
  const { players } = getPlayerStore();
  // Initialize game on mount
  useEffect(() => {
    if (players.length === 0) {
      gameStore.initGame();
    }
  }, []);

  // Loading state
  if (!gameStore.sessionActive || players.length === 0) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-emerald-50 to-blue-50 flex items-center justify-center">
        <div className="text-center">
          <div className="text-6xl mb-4 animate-bounce">🎮</div>
          <div className="text-xl font-semibold text-gray-700 mb-2">
            Setting up Vinto game...
          </div>
        </div>
      </div>
    );
  }

  return null;
});

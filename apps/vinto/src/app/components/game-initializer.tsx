// components/GameInitializer.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { useGameClient } from '@/client';

export const GameInitializer = observer(() => {
  const gameClient = useGameClient();

  // Check if game is properly initialized
  // GameClient is already initialized via GameClientProvider with quickStartGame()
  const isGameActive = gameClient.state.phase !== 'final';
  const hasPlayers = gameClient.state.players.length > 0;

  // Loading state - show only if game is not properly initialized
  if (!isGameActive || !hasPlayers) {
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

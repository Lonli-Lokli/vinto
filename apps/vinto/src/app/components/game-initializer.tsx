// components/GameInitializer.tsx
'use client';

import React from 'react';
import { useGameClientInitialized } from '@/client';

export const GameInitializer = () => {
  const isInitialized = useGameClientInitialized();

  // Show loading screen while GameClient is initializing
  // (waiting for useEffect to complete: AnimationService setup, BotAI initialization, etc.)
  if (!isInitialized) {
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
};

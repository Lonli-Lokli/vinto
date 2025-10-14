// components/GameInitializer.tsx
'use client';

import React, { useEffect } from 'react';
import { useGameClient, useGameClientInitialized } from '@/client';
import { HeadlessService } from '../services/headless-service';
import { useUIStore } from './di-provider';

export const GameInitializer = () => {
  const client = useGameClient();
  const uiStore = useUIStore();

  const isInitialized = useGameClientInitialized();

  useEffect(() => {
    const headlessService = new HeadlessService(client, uiStore);
    console.log('[GameInitializer] HeadlessService initialized');

    // Cleanup on unmount
    return () => {
      headlessService.dispose();
    };
  }, [client, uiStore]);

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

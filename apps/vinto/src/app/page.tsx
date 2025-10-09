// app/page.tsx
import React from 'react';
import { ToastProvider } from './components/toast-provider';
import { GameInitializer } from './components/game-initializer';
import { GameLayout } from './components/game-layout';
import { GameContent } from './components/game-content';
import { GameClientDebugProvider } from '@/client';

export default function VintoGame() {
  return (
    <GameClientDebugProvider>
      <GameLayout>
        <ToastProvider />
        <GameInitializer />
        <GameContent />
      </GameLayout>
    </GameClientDebugProvider>
  );
}

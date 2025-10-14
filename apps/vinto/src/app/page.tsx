// app/page.tsx
import React from 'react';
import { GameInitializer } from './components/game-initializer';
import { GameContent } from './components/game-content';
import { GameClientDebugProvider } from '@/client';
import { GameLayout, ToastProvider } from './components/presentational';

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

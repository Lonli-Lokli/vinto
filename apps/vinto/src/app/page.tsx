// app/page.tsx
import React from 'react';
import { ToastProvider } from './components/toast-provider';
import { GameInitializer } from './components/game-initializer';
import { GameLayout } from './components/game-layout';
import { GameContent } from './components/game-content';

export default function VintoGame() {
  return (
    <GameLayout>
      <ToastProvider />
      <GameInitializer />
      <GameContent />
    </GameLayout>
  );
}

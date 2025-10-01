// app/page.tsx
import React from 'react';
import { ToastProvider } from './components/toast-provider';
import { GameHeader } from './components/game-header';
import { GameInitializer } from './components/game-initializer';
import { GameLayout } from './components/game-layout';
import { MiddleArea } from './components/middle-area';
import { BottomArea } from './components/bottom-area';

export default function VintoGame() {
  return (
    <GameLayout>
      <ToastProvider />
      <GameInitializer />

      {/* Fixed Header */}
      <GameHeader />

      {/* Main Game Area - flexible height */}
      <MiddleArea />

      {/* Fixed Bottom Area - Actions and Controls stacked vertically */}
      <BottomArea />
    </GameLayout>
  );
}

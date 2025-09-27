// app/page.tsx
import React from 'react';
import { ToastProvider } from './components/toast-provider';
import { GameHeader } from './components/game-header';
import { FinalScores } from './components/final-scores';
import { GameInitializer } from './components/game-initializer';
import { GamePhaseIndicators } from './components/game-phase-indicators';
import { CardActionChoice } from './components/card-action-choice';
import { RankDeclaration } from './components/rank-declaration';
import { GameTable } from './components/game-table';
import { GameControls } from './components/game-controls';
import { ActionTargetSelector } from './components/action-target-selector';

export default function VintoGame() {
  return (
    <div className="bg-gradient-to-br from-emerald-50 to-blue-50 h-[100dvh] overflow-hidden flex flex-col">
      <ToastProvider />
      <GameInitializer />

      {/* Header - takes its natural height */}
      <GameHeader />

      {/* Main content area - takes remaining height - stacked vertically for all screen sizes */}
      <div className="flex-1 overflow-y-auto">
        <div className="p-2 md:p-4 space-y-2 md:space-y-4">
          <GameTable />
          <FinalScores />
          <GamePhaseIndicators />
          <CardActionChoice />
          <ActionTargetSelector />
          <RankDeclaration />
          <GameControls />
        </div>
      </div>
    </div>
  );
}

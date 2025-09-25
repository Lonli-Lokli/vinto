// app/page.tsx
import React from 'react';
import { ToastProvider } from './components/toast-provider';
import { GameHeader } from './components/game-header';
import { GameTable } from './components/game-table';
import { GameControls } from './components/game-controls';
import { GamePhaseIndicators } from './components/game-phase-indicators';
import { FinalScores } from './components/final-scores';
import { CardActionChoice } from './components/card-action-choice';
import { ActionTargetSelector } from './components/action-target-selector';
import { RankDeclaration } from './components/rank-declaration';
import { GameInitializer } from './components/game-initializer';

export default function VintoGame() {
  return (
    <div className="bg-gradient-to-br from-emerald-50 to-blue-50">
      <ToastProvider />
      <GameInitializer />
      <GameHeader />

      {/* Mobile: Stack all components vertically */}
      <div className="md:hidden pb-4">
        <GameTable />
        <FinalScores />
        <GamePhaseIndicators />
        <CardActionChoice />
        <ActionTargetSelector />
        <RankDeclaration />
        <GameControls />
      </div>

      {/* Desktop: Two-column layout */}
      <div className="hidden md:flex h-screen overflow-hidden">
        {/* Main Game Area */}
        <div className="flex-1 p-4 flex flex-col">
          <div className="flex-1 flex flex-col justify-center">
            <GameTable />
            <FinalScores />
            <GamePhaseIndicators />
          </div>
          <GameControls />
        </div>

        {/* Side Panel for Actions */}
        <div className="w-80 bg-white/30 backdrop-blur-sm border-l border-white/20 p-4 flex flex-col overflow-hidden">
          <div className="flex-1 overflow-y-auto space-y-4">
            <CardActionChoice />
            <ActionTargetSelector />
            <RankDeclaration />
          </div>
        </div>
      </div>
    </div>
  );
}

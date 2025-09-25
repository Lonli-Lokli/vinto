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
    <div className="bg-gradient-to-br from-emerald-50 to-blue-50 pb-4">
      <ToastProvider />
      <GameInitializer />
      <GameHeader />
      <GameTable />
      <FinalScores />
      <GamePhaseIndicators />
      <CardActionChoice />
      <ActionTargetSelector />
      <RankDeclaration />
      <GameControls />
    </div>
  );
}

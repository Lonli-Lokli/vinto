// app/page.tsx
import React from 'react';
import { ToastProvider } from './components/toast-provider';
import { GameHeader } from './components/game-header';
import { GameTable } from './components/game-table';
import { GameControls } from './components/game-controls';
import { GamePhaseIndicators } from './components/game-phase-indicators';
import { AIAnalysis } from './components/ai-analysis';
import { FinalScores } from './components/final-scores';
import { CardActionChoice } from './components/card-action-choice';
import { GameInitializer } from './components/game-initializer';

export default function VintoGame() {
  return (
    <div className="bg-gradient-to-br from-emerald-50 to-blue-50 pb-4">
      <ToastProvider />
      <GameInitializer />
      <GameHeader />
      <GameTable />
      <AIAnalysis />
      <FinalScores />
      <GamePhaseIndicators />
      <CardActionChoice />
      <GameControls />
    </div>
  );
}

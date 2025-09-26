// app/page.tsx
import React from 'react';
import { ToastProvider } from './components/toast-provider';
import { GameHeader } from './components/game-header-mobx';
import { FinalScores } from './components/final-scores-mobx';
import { GameInitializer } from './components/game-initializer-mobx';
import { GamePhaseIndicators as GamePhaseIndicatorsMobX } from './components/game-phase-indicators-mobx';
import { CardActionChoice as CardActionChoiceMobX } from './components/card-action-choice-mobx';
import { RankDeclaration as RankDeclarationMobX } from './components/rank-declaration-mobx';
import { GameTable as GameTableMobX } from './components/game-table-mobx';
import { GameControls as GameControlsMobX } from './components/game-controls-mobx';
import { ActionTargetSelector as ActionTargetSelectorMobX } from './components/action-target-selector-mobx';

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
          <GameTableMobX />
          <FinalScores />
          <GamePhaseIndicatorsMobX />
          <CardActionChoiceMobX />
          <ActionTargetSelectorMobX />
          <RankDeclarationMobX />
          <GameControlsMobX />
        </div>
      </div>
    </div>
  );
}

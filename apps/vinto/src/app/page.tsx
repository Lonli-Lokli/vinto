// app/page.tsx
'use client';

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
import { useViewport } from './hooks/use-viewport';
import { WaitingIndicator } from './components/waiting-indicator';

export default function VintoGame() {
  const viewport = useViewport();

  // CSS custom properties for dynamic height calculations
  const style = {
    '--viewport-height': `${viewport.visualHeight}px`,
    '--safe-area-inset-bottom': 'env(safe-area-inset-bottom)',
  } as React.CSSProperties;

  return (
    <div
      className="bg-gradient-to-br from-emerald-50 to-blue-50 overflow-hidden flex flex-col"
      style={{
        ...style,
        height:
          viewport.visualHeight > 0 ? `${viewport.visualHeight}px` : '100dvh',
      }}
    >
      <ToastProvider />
      <GameInitializer />

      {/* Fixed Header */}
      <div className="sticky top-0 z-50 flex-shrink-0">
        <GameHeader />
      </div>

      {/* Main Game Area - flexible height */}
      <div className="flex-1 flex flex-col min-h-0 relative">
        {/* Game Table - takes most available space */}
        <div className="flex-1 overflow-hidden">
          <GameTable />
        </div>

        {/* Modal Overlays - only for final scores in center */}
        <div className="absolute inset-0 z-40 pointer-events-none flex items-center justify-center p-4">
          <div className="pointer-events-auto">
            <FinalScores />
          </div>
        </div>
      </div>

      {/* Fixed Bottom Area - Actions and Controls stacked vertically */}
      <div
        className="sticky bottom-0 z-50 flex-shrink-0 bg-gradient-to-t from-white/95 to-transparent backdrop-blur-sm overflow-visible"
        style={{
          paddingBottom: 'max(0.5rem, env(safe-area-inset-bottom))',
          minHeight: '200px', // Fixed minimum height to prevent jumps
        }}
      >
        <div className="h-full flex flex-col justify-end overflow-visible">
          <div className="space-y-2 overflow-visible">
            {/* Game Phase Indicators */}
            <GamePhaseIndicators />

            {/* Action UI Components - stacked vertically */}
            <CardActionChoice />
            <ActionTargetSelector />
            <RankDeclaration />

            {/* Main Game Controls */}
            <GameControls />

            <WaitingIndicator />
          </div>
        </div>
      </div>
    </div>
  );
}

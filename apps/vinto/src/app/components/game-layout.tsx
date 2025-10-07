// components/game-layout.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { useViewport } from '../hooks/use-viewport';
import { DIProvider, useGamePhaseStore, useGameStore } from './di-provider';
import { ReplayControls } from './replay-controls';
import { AnimatedCardOverlay } from './animated-card';
import { ErrorBoundary } from './error-boundary';
import { CoalitionLeaderModal, VintoConfirmationModal } from './modals';
import { CoalitionTurnIndicator } from './coalition-turn-indicator';

interface GameLayoutProps {
  children: React.ReactNode;
}

/**
 * Inner component that needs access to stores
 */
const GameLayoutInner = observer(({ children }: GameLayoutProps) => {
  const viewport = useViewport();
  const gamePhaseStore = useGamePhaseStore();
  const gameStore = useGameStore();

  // CSS custom properties for dynamic height calculations
  const style = {
    '--viewport-height': `${viewport.visualHeight}px`,
    '--safe-area-inset-bottom': 'env(safe-area-inset-bottom)',
  } as React.CSSProperties;

  return (
    <div
      className="bg-gradient-to-br from-emerald-50 to-blue-50 overflow-y-auto flex flex-col"
      style={{
        ...style,
        height:
          viewport.visualHeight > 0 ? `${viewport.visualHeight}px` : '100dvh',
      }}
    >
      {children}
      <ReplayControls />
      <AnimatedCardOverlay />
      <CoalitionLeaderModal />
      <CoalitionTurnIndicator />

      {/* Vinto Confirmation Modal */}
      <VintoConfirmationModal
        isOpen={gamePhaseStore.showVintoConfirmation}
        onConfirm={() => void gameStore.callVinto()}
        onCancel={() => gamePhaseStore.closeVintoConfirmation()}
      />
    </div>
  );
});

GameLayoutInner.displayName = 'GameLayoutInner';

/**
 * Client component handling viewport-specific logic and DI setup
 */
export function GameLayout({ children }: GameLayoutProps) {
  return (
    <ErrorBoundary>
      <DIProvider>
        <GameLayoutInner>{children}</GameLayoutInner>
      </DIProvider>
    </ErrorBoundary>
  );
}

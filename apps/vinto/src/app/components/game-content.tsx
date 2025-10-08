// components/GameContent.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { useGamePhaseStore, useGameStore, usePlayerStore } from './di-provider';
import { GameHeader } from './game-header';
import { MiddleArea } from './middle-area';
import { BottomArea } from './bottom-area';
import { CoalitionTurnIndicator } from './coalition-turn-indicator';
import { AnimatedCardOverlay } from './animated-card';
import { CoalitionLeaderModal, VintoConfirmationModal } from './modals';
import { ReplayControls } from './replay-controls';

/**
 * Component that conditionally renders the main game UI
 * Only shows when the game is loaded and active
 */
export const GameContent = observer(() => {
  const gameStore = useGameStore();
  const { players } = usePlayerStore();

  const gamePhaseStore = useGamePhaseStore();

  // Only render game content when game is fully loaded
  if (!gameStore.sessionActive || players.length === 0) {
    return null;
  }

  return (
    <>
      {/* Fixed Header */}
      <GameHeader />

      {/* Main Game Area - flexible height */}
      <MiddleArea />

      {/* Fixed Bottom Area - Actions and Controls stacked vertically */}
      <BottomArea />

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
    </>
  );
});

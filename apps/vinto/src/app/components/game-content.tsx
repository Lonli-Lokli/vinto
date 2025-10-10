// components/GameContent.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { GameHeader } from './game-header';
import { MiddleArea } from './middle-area';
import { BottomArea } from './bottom-area';
import { CoalitionTurnIndicator } from './coalition-turn-indicator';
import { AnimatedCardOverlay } from './animated-card';
import { CoalitionLeaderModal, VintoConfirmationModal } from './modals';
import { useGameClient } from '@/client';

/**
 * Component that conditionally renders the main game UI
 * Only shows when the game is loaded and active
 */
export const GameContent = observer(() => {
  const gameClient = useGameClient();

  const sessionActive = gameClient.state.phase !== 'scoring';

  // Only render game content when game is fully loaded
  if (!sessionActive || gameClient.state.players.length === 0) {
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

      <AnimatedCardOverlay />
      <CoalitionLeaderModal />
      <CoalitionTurnIndicator />

      {/* Vinto Confirmation Modal */}
      <VintoConfirmationModal />
    </>
  );
});

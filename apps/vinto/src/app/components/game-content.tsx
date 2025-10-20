// components/GameContent.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { GameHeader } from './game-header';
import { MiddleArea, BottomArea } from './presentational';
import { CoalitionTurnIndicator } from './coalition-turn-indicator';
import { CoalitionLeaderModal, VintoConfirmationModal } from './modals';
import { useGameClientInitialized } from '@vinto/local-client';
import { AnimatedCardOverlay } from './animated-card';

/**
 * Component that conditionally renders the main game UI
 * Only shows when the game is fully initialized and active
 */
export const GameContent = observer(() => {
  const isInitialized = useGameClientInitialized();

  // Wait for GameClient to be fully initialized (AnimationService, BotAI, etc.)
  if (!isInitialized) {
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

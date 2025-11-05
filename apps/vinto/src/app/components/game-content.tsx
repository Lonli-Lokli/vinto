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
import { PenaltyIndicatorOverlay } from './penalty-indicator-overlay';
import { useIsDesktop } from '../hooks/use-is-desktop';

/**
 * Component that conditionally renders the main game UI
 * Only shows when the game is fully initialized and active
 */
export const GameContent = observer(() => {
  const isInitialized = useGameClientInitialized();
  const isDesktop = useIsDesktop();

  // Wait for GameClient to be fully initialized (AnimationService, BotAI, etc.)
  if (!isInitialized) {
    return null;
  }

  return (
    <>
      {/* Fixed Header */}
      <GameHeader />

      {/* Main Game Layout */}
      {isDesktop ? (
          /* Desktop Layout: Middle area fills all available space, Bottom area on right */
          <div className="flex flex-1 min-h-0 justify-center items-stretch w-full h-full">
            <div className="flex-1 min-w-0 w-full h-full">
              <MiddleArea />
            </div>
            <div className="w-96 flex-shrink-0 border-l border-primary bg-surface-primary/80">
              <BottomArea />
            </div>
          </div>
      ) : (
        /* Mobile Layout: Vertical stack */
        <>
          <MiddleArea />
          <BottomArea />
        </>
      )}

      <AnimatedCardOverlay />
      <PenaltyIndicatorOverlay />
      <CoalitionLeaderModal />
      <CoalitionTurnIndicator />

      {/* Vinto Confirmation Modal */}
      <VintoConfirmationModal />
    </>
  );
});

'use client';

import { observer } from 'mobx-react-lite';
import { ActionTargetSelector } from '../action-target-selector';
import { CoalitionStatus } from '../coalition-status';
import { FinalScores } from '../final-scores';
import { GameControls } from '../game-controls';
import { GamePhaseIndicators } from '../game-phase-indicators';
import { RankDeclaration } from '../rank-declaration';
import { WaitingIndicator } from '../waiting-indicator';
import { useCardAnimationStore } from '../di-provider';
import { useGameClient } from '@vinto/local-client';

export const UserControlsArea = observer(() => {
  const animationStore = useCardAnimationStore();
  const gameClient = useGameClient();
  const hideGameControls = gameClient.isFinalTurn && gameClient.visualState.phase !== 'scoring';
  const hasBlockingAnimations = animationStore.hasBlockingAnimations;

  return (
    <div
      className="sticky bottom-0 z-40 flex-shrink-0 from-white/95 to-transparent backdrop-blur-sm w-full"
      style={{
        height: '25vh',
        minHeight: '100px',
        maxHeight: '260px',
      }}
    >
      <div className="h-full w-full relative">
        {/* Coalition Status - Always visible during final round, even during animations */}
        <CoalitionStatus />
        {hideGameControls ? null : hasBlockingAnimations ? (
          <WaitingIndicator />
        ) : (
          // Normal content when not blocking
          <div className="h-full w-full" key="controls-content">
            <GamePhaseIndicators />
            <ActionTargetSelector />
            <RankDeclaration />
            <GameControls />
            <WaitingIndicator />
            <FinalScores />
          </div>
        )}
      </div>
    </div>
  );
});

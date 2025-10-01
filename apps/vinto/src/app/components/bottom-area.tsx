import { ActionTargetSelector } from './action-target-selector';
import { CardActionChoice } from './card-action-choice';
import { GameControls } from './game-controls';
import { GamePhaseIndicators } from './game-phase-indicators';
import { RankDeclaration } from './rank-declaration';
import { WaitingIndicator } from './waiting-indicator';

export function BottomArea() {
  return (
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
  );
}

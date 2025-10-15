import { ActionTargetSelector } from '../action-target-selector';
import { FinalScores } from '../final-scores';
import { GameControls } from '../game-controls';
import { GamePhaseIndicators } from '../game-phase-indicators';
import { RankDeclaration } from '../rank-declaration';
import { WaitingIndicator } from '../waiting-indicator';

export function BottomArea() {
  return (
    <div
      className="sticky bottom-0 z-40 flex-shrink-0 from-white/95 to-transparent backdrop-blur-sm"
      style={{
        height: '25vh', // Fixed height as percentage of viewport
        minHeight: '100px', // Minimum height for usability
        maxHeight: '260px', // Maximum height to prevent taking too much space
      }}
    >
      <div className="h-full w-full">
        {/* Only one of these components will be visible at a time, each takes full space */}
        <GamePhaseIndicators />
        <ActionTargetSelector />
        <RankDeclaration />
        <GameControls />
        <WaitingIndicator />
        <FinalScores />
      </div>
    </div>
  );
}

import { FinalScores } from './final-scores';
import { GameTable } from './game-table';

export function MiddleArea() {
  return (
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
  );
}

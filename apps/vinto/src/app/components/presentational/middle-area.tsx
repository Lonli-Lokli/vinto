import { GameTable } from '../game-table';

export function MiddleArea() {
  return (
    <div
      className="flex-1 flex flex-col min-h-0 h-full relative py-1"
      data-testid="middle-area"
    >
      {/* Game Table - takes most available space */}
      <div className="flex-1 overflow-visible">
        <GameTable />
      </div>
    </div>
  );
}

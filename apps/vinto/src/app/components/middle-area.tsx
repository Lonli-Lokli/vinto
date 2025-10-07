import { GameTable } from './game-table';

export function MiddleArea() {
  return (
    <div className="flex-1 flex flex-col min-h-0 relative">
      {/* Game Table - takes most available space */}
      <div className="flex-1 overflow-hidden">
        <GameTable />
      </div>
    </div>
  );
}

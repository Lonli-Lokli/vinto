// components/GameInfo.tsx - Server Component
import React from 'react';

interface GameInfoProps {
  drawPileCount: number;
  gameId: string;
}

export function GameInfo({ drawPileCount, gameId }: GameInfoProps) {
  return (
    <>
      {/* Game Info in header */}
      <div className="text-right">
        <div className="text-sm font-semibold text-primary">
          {drawPileCount}
        </div>
        <div className="text-xs text-secondary">cards left</div>
      </div>

      {/* Game Status at bottom */}
      <div className="mt-6 text-center text-sm text-secondary space-y-1">
        <div className="text-xs text-muted">Game ID: {gameId.slice(-8)}</div>
      </div>
    </>
  );
}

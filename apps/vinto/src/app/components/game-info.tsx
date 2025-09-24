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
        <div className="text-sm font-semibold text-gray-700">
          {drawPileCount}
        </div>
        <div className="text-xs text-gray-500">cards left</div>
      </div>

      {/* Game Status at bottom */}
      <div className="mt-6 text-center text-sm text-gray-600 space-y-1">
        <div className="text-xs text-gray-400">Game ID: {gameId.slice(-8)}</div>
      </div>
    </>
  );
}

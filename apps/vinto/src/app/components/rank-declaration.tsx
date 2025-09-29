// components/RankDeclaration.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { gameStore } from '../stores/game-store';
import { Rank } from '../shapes';
import { ALL_RANKS } from '../lib/game-helpers';

export const RankDeclaration = observer(() => {
  if (!gameStore.isDeclaringRank || gameStore.swapPosition === null) {
    return null;
  }

  const currentPlayer = gameStore.players[gameStore.currentPlayerIndex];
  if (!currentPlayer) return null;



  const handleRankClick = (rank: Rank) => {
    gameStore.declareRank(rank);
  };

  return (
    <div className="mt-2 max-w-lg mx-auto px-2 min-h-[140px]">
      <div className="bg-gradient-to-br from-yellow-50 to-orange-50 border-2 border-yellow-300 rounded-xl p-3 shadow-lg h-full flex flex-col justify-between">
        <div className="text-center mb-3">
          <h3 className="text-sm sm:text-base font-bold text-yellow-800">
            🎯 Declare Card Rank
          </h3>
          <p className="text-xs text-yellow-700 mt-1">
            You drew <strong>{gameStore.pendingCard?.rank}</strong> and are
            swapping it with your card at position{' '}
            <strong>{gameStore.swapPosition + 1}</strong>
          </p>
          <p className="text-xs text-yellow-700 mt-1">
            Declare what rank you think your position{' '}
            {gameStore.swapPosition + 1} card is:
          </p>
          <div className="text-[10px] text-yellow-600 mt-1 bg-yellow-100 rounded px-2 py-1">
            ✅ Correct = Use {gameStore.pendingCard?.rank}&apos;s action • ❌
            Wrong = Get penalty card
          </div>
        </div>

        {/* Rank Selection Grid */}
        <div className="grid grid-cols-4 gap-1.5 mb-3">
          {ALL_RANKS.map((rank) => (
            <button
              key={rank}
              onClick={() => handleRankClick(rank)}
              className="bg-gradient-to-br from-yellow-400 to-yellow-500 hover:from-yellow-500 hover:to-yellow-600 text-white font-bold py-2 px-2 rounded-lg shadow transition-all duration-150 text-xs sm:text-sm"
              title={`Declare ${rank}`}
            >
              {rank}
            </button>
          ))}
        </div>

        {/* Action Buttons */}
        <div className="flex gap-2">
          <button
            onClick={() => gameStore.skipDeclaration()}
            className="flex-1 bg-gradient-to-r from-gray-500 to-gray-600 hover:from-gray-600 hover:to-gray-700 text-white font-medium py-2 px-3 rounded-lg shadow transition-colors text-xs sm:text-sm"
            title="Skip declaration and just swap the card"
          >
            Skip Declaration
          </button>
        </div>

        <div className="text-center text-[9px] text-yellow-600 mt-2">
          Choose wisely! Wrong declaration results in penalty card.
        </div>
      </div>
    </div>
  );
});

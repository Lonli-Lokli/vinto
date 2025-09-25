// components/RankDeclaration.tsx
'use client';

import React from 'react';
import { useGameStore } from '../stores/game-store';
import { Rank } from '../shapes';

export function RankDeclaration() {
  const { isDeclaringRank, declareRank, cancelSwap } = useGameStore();

  if (!isDeclaringRank) {
    return null;
  }
  const ranks: Rank[] = ['2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K', 'A'];

  return (
    <div className="mt-3 sm:mt-4 max-w-lg mx-auto px-2">
      <div className="bg-white/90 backdrop-blur-sm border border-gray-200 rounded-xl p-3 sm:p-4 shadow-md">
        <div className="text-center mb-3">
          <h3 className="text-sm sm:text-base font-semibold text-gray-800">Declare Card Rank</h3>
          <p className="text-xs text-gray-600 mt-1">
            Declare the rank of your discarded card. Correct = action plays, incorrect = penalty card!
          </p>
        </div>

        {/* Rank Selection Grid */}
        <div className="grid grid-cols-7 gap-1.5 mb-3">
          {ranks.map((rank) => (
            <button
              key={rank}
              onClick={() => declareRank(rank)}
              className="flex items-center justify-center bg-gradient-to-r from-blue-600 to-blue-700 hover:from-blue-700 hover:to-blue-800 text-white font-semibold py-2 px-2 rounded-lg shadow transition-colors text-sm"
              aria-label={`Declare ${rank}`}
              title={`Declare ${rank}`}
            >
              {rank}
            </button>
          ))}
        </div>

        {/* Cancel Button */}
        <button
          onClick={cancelSwap}
          className="w-full bg-gradient-to-r from-gray-600 to-gray-700 hover:from-gray-700 hover:to-gray-800 text-white font-semibold py-2.5 px-4 rounded-lg shadow transition-colors"
          aria-label="Cancel swap"
          title="Cancel swap and pass turn"
        >
          Cancel Swap
        </button>
      </div>
    </div>
  );
}
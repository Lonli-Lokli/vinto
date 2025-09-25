// components/RankDeclaration.tsx
'use client';

import React from 'react';
import { useGameStore } from '../stores/game-store';
import { Rank } from '../shapes';

export function RankDeclaration() {
  const {
    isDeclaringRank,
    swapPosition,
    players,
    currentPlayerIndex,
    declareRank,
    skipDeclaration
  } = useGameStore();

  if (!isDeclaringRank || swapPosition === null) {
    return null;
  }

  const currentPlayer = players[currentPlayerIndex];
  if (!currentPlayer) return null;

  const ranks: Rank[] = ['2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K', 'A'];

  const handleRankClick = (rank: Rank) => {
    declareRank(rank);
  };

  return (
    <div className="mt-2 max-w-lg mx-auto px-2">
      <div className="bg-gradient-to-br from-yellow-50 to-orange-50 border-2 border-yellow-300 rounded-xl p-3 shadow-lg">
        <div className="text-center mb-3">
          <h3 className="text-sm sm:text-base font-bold text-yellow-800">
            🎯 Declare Card Rank
          </h3>
          <p className="text-xs text-yellow-700 mt-1">
            Declare the rank of the card you&apos;re replacing at position {swapPosition + 1}
          </p>
          <div className="text-[10px] text-yellow-600 mt-1 bg-yellow-100 rounded px-2 py-1">
            ✅ Correct = Use card&apos;s action • ❌ Wrong = Get penalty card
          </div>
        </div>

        {/* Rank Selection Grid */}
        <div className="grid grid-cols-4 gap-1.5 mb-3">
          {ranks.map((rank) => (
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
            onClick={skipDeclaration}
            className="flex-1 bg-gradient-to-r from-gray-500 to-gray-600 hover:from-gray-600 hover:to-gray-700 text-white font-medium py-2 px-3 rounded-lg shadow transition-colors text-xs sm:text-sm"
            title="Skip declaration and just swap the card"
          >
            Skip Declaration
          </button>
        </div>

        <div className="text-center text-[9px] text-yellow-600 mt-2">
          Choose wisely! Wrong declarations result in penalty cards.
        </div>
      </div>
    </div>
  );
}
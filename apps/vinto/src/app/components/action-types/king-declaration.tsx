// components/action-types/KingDeclaration.tsx
'use client';

import React from 'react';
import { gameStore } from '@/app/stores/game-store';

export function KingDeclaration() {
  const actionCards = ['7', '8', '9', '10', 'J', 'Q', 'K', 'A'] as const;
  const nonActionCards = ['2', '3', '4', '5', '6', 'Joker'] as const;

  return (
    <div className="w-full max-w-4xl mx-auto px-3 min-h-[140px]">
      <div className="bg-yellow-50 border border-yellow-300 rounded-lg p-3 md:p-4 shadow-md h-full flex flex-col">
        <div className="text-center mb-3">
          <h3 className="text-sm md:text-base font-semibold text-yellow-800 mb-1">
            👑 King Declaration
          </h3>
          <p className="text-xs md:text-sm text-yellow-600">
            Choose which card action to execute
          </p>
        </div>

        <div className="flex-1 flex flex-col justify-center space-y-3">
          {/* Action cards - primary focus */}
          <div>
            <div className="text-xs md:text-sm font-medium text-yellow-700 mb-2 text-center">
              Action Cards
            </div>
            <div className="grid grid-cols-4 md:grid-cols-8 gap-2 md:gap-3">
              {actionCards.map((rank) => (
                <button
                  key={rank}
                  onClick={() => gameStore.declareKingAction(rank)}
                  className="bg-yellow-600 hover:bg-yellow-700 text-white font-bold py-2 px-4 rounded-lg transition-colors text-base min-h-[2.5rem] md:min-h-[3rem] flex items-center justify-center"
                  title={`Execute ${rank} action`}
                >
                  {rank}
                </button>
              ))}
            </div>
          </div>

          {/* Non-action cards - secondary, smaller */}
          <div>
            <div className="text-xs font-medium text-gray-500 mb-1 text-center">
              No Action Cards
            </div>
            <div className="grid grid-cols-6 gap-1 md:gap-2">
              {nonActionCards.map((rank) => (
                <button
                  key={rank}
                  onClick={() => gameStore.declareKingAction(rank)}
                  className="bg-gray-400 hover:bg-gray-500 text-white font-medium py-2 px-4 rounded transition-colors text-base flex items-center justify-center"
                  title={`Declare ${rank} (no action)`}
                >
                  {rank}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

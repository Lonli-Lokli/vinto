// components/action-types/KingDeclaration.tsx
'use client';

import React from 'react';
import { useGameStore } from '../di-provider';

export function KingDeclaration() {
  const gameStore = useGameStore();
  const actionCards = ['7', '8', '9', '10', 'J', 'Q', 'K', 'A'] as const;
  const nonActionCards = ['2', '3', '4', '5', '6', 'Joker'] as const;

  return (
    <div className="w-full max-w-4xl mx-auto px-3 min-h-[140px]">
      <div className="bg-white border border-gray-300 rounded p-2 shadow-sm h-full flex flex-col">
        <div className="text-center mb-2">
          <h3 className="text-xs md:text-sm font-semibold text-gray-800 mb-1">
            👑 King Declaration
          </h3>
          <p className="text-2xs md:text-xs text-gray-600">
            Choose which card action to execute
          </p>
        </div>

        <div className="flex-1 flex flex-col justify-center space-y-2">
          {/* Action cards - primary focus */}
          <div>
            <div className="text-2xs md:text-xs font-medium text-gray-600 mb-1 text-center">
              Action Cards
            </div>
            <div className="grid grid-cols-4 md:grid-cols-8 gap-1 md:gap-2">
              {actionCards.map((rank) => (
                <button
                  key={rank}
                  onClick={() => gameStore.declareKingAction(rank)}
                  className="bg-amber-500 hover:bg-amber-600 text-white font-bold py-1.5 px-3 rounded transition-colors text-sm min-h-[2rem] md:min-h-[2.5rem] flex items-center justify-center"
                  title={`Execute ${rank} action`}
                >
                  {rank}
                </button>
              ))}
            </div>
          </div>

          {/* Non-action cards - secondary, smaller */}
          <div>
            <div className="text-2xs font-medium text-gray-500 mb-1 text-center">
              No Action Cards
            </div>
            <div className="grid grid-cols-6 gap-1">
              {nonActionCards.map((rank) => (
                <button
                  key={rank}
                  onClick={() => gameStore.declareKingAction(rank)}
                  className="bg-gray-400 hover:bg-gray-500 text-white font-medium py-1.5 px-2 rounded transition-colors text-xs flex items-center justify-center"
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

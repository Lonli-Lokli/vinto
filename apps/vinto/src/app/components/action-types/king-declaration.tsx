// components/action-types/KingDeclaration.tsx
'use client';

import React from 'react';
import { HelpPopover } from '../help-popover';
import { useGameStore } from '../di-provider';

export function KingDeclaration() {
  const gameStore = useGameStore();
  // K cannot declare itself - only the action cards that can be executed
  const actionCards = ['7', '8', '9', '10', 'J', 'Q', 'A'] as const;
  const nonActionCards = ['2', '3', '4', '5', '6', 'K', 'Joker'] as const;

  return (
    <div className="w-full h-full px-3 py-2">
      <div className="bg-white border border-gray-300 rounded-lg p-4 shadow-sm h-full flex flex-col">
        <div className="text-center mb-2">
          <div className="flex flex-row items-center justify-center gap-2">
            <h3 className="text-xs md:text-sm font-semibold text-gray-800 leading-tight">
              👑 King Declaration
            </h3>
            <HelpPopover title="King Declaration" rank="K" />
          </div>
          <p className="text-xs md:text-sm text-gray-600 mt-1 leading-normal">
            Choose which card action to execute
          </p>
        </div>

        <div className="flex-1 flex flex-col justify-center space-y-2">
          {/* Action cards - primary focus */}
          <div>
            <div className="text-xs md:text-sm font-medium text-gray-600 mb-1 text-center leading-tight">
              Action Cards
            </div>
            <div className="grid grid-cols-4 md:grid-cols-7 gap-1 md:gap-2">
              {actionCards.map((rank) => (
                <button
                  key={rank}
                  onClick={() => gameStore.declareKingAction(rank)}
                  className="bg-amber-500 hover:bg-amber-600 active:bg-amber-700 text-white font-bold py-1.5 px-3 rounded transition-colors text-sm min-h-[44px] flex items-center justify-center"
                  title={`Execute ${rank} action`}
                >
                  {rank}
                </button>
              ))}
            </div>
          </div>

          {/* Non-action cards - secondary, smaller */}
          <div>
            <div className="text-xs font-medium text-gray-500 mb-1 text-center leading-tight">
              No Action Cards
            </div>
            <div className="grid grid-cols-6 gap-1">
              {nonActionCards.map((rank) => (
                <button
                  key={rank}
                  onClick={() => gameStore.declareKingAction(rank)}
                  className="bg-gray-400 hover:bg-gray-500 active:bg-gray-600 text-white font-medium py-1.5 px-2 rounded transition-colors text-xs flex items-center justify-center min-h-[44px]"
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

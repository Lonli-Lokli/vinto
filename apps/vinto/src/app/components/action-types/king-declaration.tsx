// components/action-types/KingDeclaration.tsx
'use client';

import React from 'react';
import { gameStore } from '@/app/stores/game-store';

export function KingDeclaration() {
  return (
    <div className="max-w-lg mx-auto px-3">
      <div className="bg-indigo-50 border border-indigo-300 rounded-lg p-3 shadow-md">
        <div className="text-center mb-2">
          <h3 className="text-sm font-semibold text-indigo-800 mb-1">
            👑 King Declaration
          </h3>
          <p className="text-xs text-indigo-600">
            Choose which card action to execute
          </p>
        </div>
        <div className="grid grid-cols-4 gap-1">
          {(
            [
              '2',
              '3',
              '4',
              '5',
              '6',
              '7',
              '8',
              '9',
              '10',
              'J',
              'Q',
              'K',
              'A',
              'Joker',
            ] as const
          ).map((rank) => (
            <button
              key={rank}
              onClick={() => gameStore.declareKingAction(rank)}
              className="bg-indigo-600 hover:bg-indigo-700 text-white font-medium py-1.5 px-2 rounded transition-colors text-xs"
            >
              {rank}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

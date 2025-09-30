// components/action-types/QueenAction.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { getActionStore } from '@/app/stores/action-store';
import { gameStore } from '@/app/stores/game-store';

export const QueenAction = observer(() => {
  const actionStore = getActionStore();
  if (!actionStore.actionContext) return null;
  const {
    peekTargets,
    actionContext: { action },
  } = actionStore;
  const { executeQueenSwap, skipQueenSwap } = gameStore;

  const hasBothCards = peekTargets.length === 2;

  return (
    <div className="w-full max-w-4xl mx-auto px-3 py-2 min-h-[140px]">
      <div className="bg-white/95 backdrop-blur-sm border border-gray-300 rounded-lg p-2 shadow-sm h-full flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-xs md:text-sm font-semibold text-gray-800">
            👑 {action}
          </h3>
          <div className="text-2xs md:text-xs text-gray-500">
            {peekTargets.length}/2 selected
          </div>
        </div>

        {/* Instructions or Peeked Cards Info */}
        <div className="flex-1 flex flex-col justify-center mb-2">
          {!hasBothCards ? (
            <p className="text-xs text-gray-600 text-center">
              Select two cards from different players to peek at
            </p>
          ) : (
            <div className="text-xs text-gray-600 text-center">
              <div className="mb-1">Peeked cards:</div>
              <div className="text-sm font-medium text-gray-800">
                {peekTargets
                  .map(
                    (target) => `${target.card?.rank} (${target.card?.value})`
                  )
                  .join(' • ')}
              </div>
            </div>
          )}
        </div>

        {/* Action Buttons - only show when both cards selected */}
        {hasBothCards && (
          <div className="grid grid-cols-2 gap-2">
            <button
              onClick={() => executeQueenSwap()}
              className="bg-emerald-600 hover:bg-emerald-700 text-white font-semibold py-2 px-4 rounded shadow-sm transition-colors text-sm"
            >
              🔄 Swap Cards
            </button>
            <button
              onClick={() => skipQueenSwap()}
              className="bg-slate-600 hover:bg-slate-700 text-white font-semibold py-2 px-4 rounded shadow-sm transition-colors text-sm"
            >
              ⏭️ Skip
            </button>
          </div>
        )}
      </div>
    </div>
  );
});

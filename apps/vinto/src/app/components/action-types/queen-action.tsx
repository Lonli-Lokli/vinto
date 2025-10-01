// components/action-types/QueenAction.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { useActionStore, useGameStore } from '../di-provider';

export const QueenAction = observer(() => {
  const gameStore = useGameStore();
  const actionStore = useActionStore();
  if (!actionStore.actionContext) return null;
  const {
    peekTargets,
    actionContext: { action },
  } = actionStore;
  const { executeQueenSwap, skipQueenSwap } = gameStore;

  const hasBothCards = peekTargets.length === 2;

  return (
    <div className="w-full h-full px-3 py-2">
      <div className="bg-white/95 backdrop-blur-sm border border-gray-300 rounded-lg p-4 shadow-sm h-full flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-xs md:text-sm font-semibold text-gray-800">
            👑 {action}
          </h3>
          <div className="text-2xs md:text-xs text-gray-500">
            {peekTargets.length}/2 selected
          </div>
        </div>

        {/* Instructions - only show when cards not yet selected */}
        <div className="flex-1 flex flex-col justify-center mb-4">
          {!hasBothCards && (
            <p className="text-base text-gray-600 text-center">
              Select two cards from different players to peek at
            </p>
          )}
        </div>

        {/* Action Buttons - only show when both cards selected */}
        {hasBothCards && (
          <div className="grid grid-cols-2 gap-4">
            <button
              onClick={() => executeQueenSwap()}
              className="bg-emerald-600 hover:bg-emerald-700 text-white font-semibold py-4 px-6 rounded-lg shadow-sm transition-colors text-base"
            >
              🔄 Swap Cards
            </button>
            <button
              onClick={() => skipQueenSwap()}
              className="bg-slate-600 hover:bg-slate-700 text-white font-semibold py-4 px-6 rounded-lg shadow-sm transition-colors text-base"
            >
              ⏭️ Skip
            </button>
          </div>
        )}
      </div>
    </div>
  );
});

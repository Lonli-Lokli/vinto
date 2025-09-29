// components/action-types/QueenAction.tsx
'use client';

import React from 'react';
import { getActionStore } from '@/app/stores/action-store';
import { gameStore } from '@/app/stores/game-store';

export function QueenAction() {
  const actionStore = getActionStore();
  if (!actionStore.actionContext) return null;
  const {
    peekTargets,

    actionContext: { action },
  } = actionStore;
  const { executeQueenSwap, skipQueenSwap } = gameStore;
  return (
    <div className="w-full max-w-4xl mx-auto px-3 min-h-[140px]">
      <div className="bg-white border border-gray-300 rounded p-2 shadow-sm h-full flex flex-col justify-center">
        <div className="text-center">
          <h3 className="text-xs font-semibold text-gray-800 mb-1">
            👑 {action}
          </h3>
          <p className="text-2xs text-gray-600 mb-2">
            Select two cards to peek at ({peekTargets.length}/2 selected)
          </p>
          {peekTargets.length > 0 && (
            <div className="text-2xs text-gray-500 mb-2">
              Peeked:
              {peekTargets
                .map(
                  (target, i) => `${target.card?.rank} (${target.card?.value})`
                )
                .join(', ')}
            </div>
          )}
          {peekTargets.length === 2 && (
            <div className="flex gap-2">
              <button
                onClick={() => executeQueenSwap()}
                className="flex-1 bg-emerald-600 hover:bg-emerald-700 text-white font-medium py-1.5 px-3 rounded text-sm"
              >
                Swap Cards
              </button>
              <button
                onClick={() => skipQueenSwap()}
                className="flex-1 bg-slate-600 hover:bg-slate-700 text-white font-medium py-1.5 px-3 rounded text-sm"
              >
                Skip Swap
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

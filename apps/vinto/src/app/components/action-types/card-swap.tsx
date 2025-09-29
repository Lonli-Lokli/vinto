// components/action-types/CardSwap.tsx
'use client';

import React from 'react';
import { gameStore } from '@/app/stores/game-store';

export function CardSwap() {
  if (!gameStore.actionContext) return null;
  const { action } = gameStore.actionContext;
  const swapTargets = gameStore.swapTargets;
  return (
    <div className="max-w-lg mx-auto px-3 min-h-[140px]">
      <div className="bg-purple-50 border border-purple-300 rounded-lg p-3 shadow-md h-full flex flex-col justify-center">
        <div className="text-center">
          <h3 className="text-sm font-semibold text-purple-800 mb-1">
            🔄 {action}
          </h3>
          <p className="text-xs text-purple-600 mb-2">
            Select two cards to swap ({swapTargets.length}/2 selected)
          </p>
          {swapTargets.length > 0 && (
            <div className="text-xs text-purple-500">
              Selected:
              {swapTargets
                .map(
                  (target) =>
                    `P${target.playerId.slice(-1)} pos ${target.position + 1}`
                )
                .join(', ')}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

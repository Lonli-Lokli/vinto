// components/action-types/CardSwap.tsx
'use client';

import { getActionStore } from '@/app/stores/action-store';
import React from 'react';


export function CardSwap() {
  const actionStore = getActionStore();
  if (!actionStore.actionContext) return null;
  const { action } = actionStore.actionContext;
  const swapTargets = actionStore.swapTargets;
  return (
    <div className="w-full max-w-4xl mx-auto px-3 min-h-[140px]">
      <div className="bg-white border border-gray-300 rounded p-2 shadow-sm h-full flex flex-col justify-center">
        <div className="text-center">
          <h3 className="text-xs font-semibold text-gray-800 mb-1">
            🔄 {action}
          </h3>
          <p className="text-2xs text-gray-600 mb-2">
            Select two cards to swap ({swapTargets.length}/2 selected)
          </p>
          {swapTargets.length > 0 && (
            <div className="text-2xs text-gray-500">
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

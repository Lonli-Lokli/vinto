// components/action-types/AceAction.tsx
'use client';

import { useActionStore, useGameStore } from '../di-provider';
import React from 'react';
import { observer } from 'mobx-react-lite';

export const AceAction = observer(() => {
  const actionStore = useActionStore();
  const gameStore = useGameStore();
  if (!actionStore.actionContext) return null;
  const { action } = actionStore.actionContext;

  return (
    <div className="w-full h-full px-3 py-2">
      <div className="bg-white/95 backdrop-blur-sm border border-gray-300 rounded-lg p-4 shadow-sm h-full flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-xs md:text-sm font-semibold text-gray-800">
            🎯 {action}
          </h3>
        </div>

        {/* Instructions */}
        <div className="flex-1 flex flex-col justify-center mb-2">
          <p className="text-xs text-gray-600 text-center">
            Click on an opponent to force them to draw a card
          </p>
        </div>

        {/* Skip Button */}
        <button
          onClick={() => gameStore.confirmPeekCompletion()}
          className="w-full bg-slate-600 hover:bg-slate-700 text-white font-semibold py-2 px-4 rounded shadow-sm transition-colors text-sm"
        >
          ⏭️ Skip
        </button>
      </div>
    </div>
  );
});

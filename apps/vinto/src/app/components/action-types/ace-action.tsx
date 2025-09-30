// components/action-types/AceAction.tsx
'use client';

import { getActionStore } from '@/app/stores/action-store';
import React from 'react';
import { observer } from 'mobx-react-lite';

export const AceAction = observer(() => {
  const actionStore = getActionStore();
  if (!actionStore.actionContext) return null;
  const { action } = actionStore.actionContext;

  return (
    <div className="w-full max-w-4xl mx-auto px-3 py-2 min-h-[140px]">
      <div className="bg-white/95 backdrop-blur-sm border border-gray-300 rounded-lg p-2 shadow-sm h-full flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-xs md:text-sm font-semibold text-gray-800">
            🎯 {action}
          </h3>
        </div>

        {/* Instructions */}
        <div className="flex-1 flex flex-col justify-center">
          <p className="text-xs text-gray-600 text-center">
            Click on an opponent to force them to draw a card
          </p>
        </div>
      </div>
    </div>
  );
});

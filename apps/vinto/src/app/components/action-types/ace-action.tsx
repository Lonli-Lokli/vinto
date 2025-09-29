// components/action-types/AceAction.tsx
'use client';

import { getActionStore } from '@/app/stores/action-store';
import React from 'react';

export function AceAction() {
  const actionStore = getActionStore();
  if (!actionStore.actionContext) return null;
  const { action } = actionStore.actionContext;
  return (
    <div className="w-full max-w-4xl mx-auto px-3 min-h-[140px]">
      <div className="bg-orange-50 border border-orange-300 rounded-lg p-3 md:p-4 shadow-md h-full flex flex-col justify-center">
        <div className="text-center">
          <h3 className="text-sm font-semibold text-orange-800 mb-1">
            🎯 {action}
          </h3>
          <p className="text-xs text-orange-600">
            Click on an opponent to force them to draw a card
          </p>
        </div>
      </div>
    </div>
  );
}

// components/action-types/AceAction.tsx
'use client';

import { gameStore } from '@/app/stores/game-store';
import React from 'react';

export function AceAction() {
  if (!gameStore.actionContext) return null;
  const { action } = gameStore.actionContext;
  return (
    <div className="max-w-lg mx-auto px-3">
      <div className="bg-orange-50 border border-orange-300 rounded-lg p-3 shadow-md">
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

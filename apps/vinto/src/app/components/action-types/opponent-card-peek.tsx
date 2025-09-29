// components/action-types/OpponentCardPeek.tsx
'use client';

import React from 'react';
import { gameStore } from '@/app/stores/game-store';

export function OpponentCardPeek() {
  if (!gameStore.actionContext) return null;
  const { action } = gameStore.actionContext;
  return (
    <div className="max-w-lg mx-auto px-3">
      <div className="bg-red-50 border border-red-300 rounded-lg p-3 shadow-md">
        <div className="text-center">
          <h3 className="text-sm font-semibold text-red-800 mb-1">
            🔍 {action}
          </h3>
          <p className="text-xs text-red-600">
            Click on an opponent&apos;s card to peek at it
          </p>
        </div>
      </div>
    </div>
  );
}

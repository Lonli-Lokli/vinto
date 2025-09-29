// components/action-types/OpponentCardPeek.tsx
'use client';

import React from 'react';
import { getActionStore } from '@/app/stores/action-store';

export function OpponentCardPeek() {
  const actionStore = getActionStore();

  if (!actionStore.actionContext) return null;
  const { action } = actionStore.actionContext;
  return (
    <div className="w-full max-w-4xl mx-auto px-3 min-h-[140px]">
      <div className="bg-blue-50 border border-blue-300 rounded-lg p-3 md:p-4 shadow-md h-full flex flex-col justify-center">
        <div className="text-center">
          <h3 className="text-sm font-semibold text-blue-800 mb-1">
            🔍 {action}
          </h3>
          <p className="text-xs text-blue-600">
            Click on an opponent&apos;s card to peek at it
          </p>
        </div>
      </div>
    </div>
  );
}

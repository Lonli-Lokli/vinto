// components/action-types/OwnCardPeek.tsx
'use client';

import { getActionStore } from '@/app/stores/action-store';
import React from 'react';

export function OwnCardPeek() {
  const actionStore = getActionStore();
  const { action } = actionStore.actionContext ?? {};
  return (
    <div className="w-full max-w-4xl mx-auto px-3 min-h-[140px]">
      <div className="bg-white border border-gray-300 rounded p-2 shadow-sm h-full flex flex-col justify-center">
        <div className="text-center">
          <h3 className="text-xs font-semibold text-gray-800 mb-1">
            👁️ {action}
          </h3>
          <p className="text-2xs text-gray-600">
            Click on one of your cards to peek at it
          </p>
        </div>
      </div>
    </div>
  );
}

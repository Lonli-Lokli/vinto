// components/action-types/OwnCardPeek.tsx
'use client';

import React from 'react';

export function OwnCardPeek() {
  return (
    <div className="bg-blue-50 rounded-lg p-3 mb-3">
      <div className="text-center text-sm text-blue-800">
        🔍 <strong>Peek Action</strong>
        <br />
        <span className="text-xs text-blue-600">
          Select a card from your hand to reveal its value
        </span>
      </div>
      <div className="text-center">
        <p className="text-xs text-gray-500 mt-2">
          Click on one of your cards to peek at it
        </p>
      </div>
    </div>
  );
}
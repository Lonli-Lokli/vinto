// components/CardActionChoice.tsx
'use client';

import React from 'react';
import { useGameStore } from '../stores/game-store';

export function CardActionChoice() {
  const {
    isChoosingCardAction,
    pendingCard,
    chooseSwap,
    choosePlayCard,
    cancelSwap
  } = useGameStore();

  if (!isChoosingCardAction || !pendingCard) {
    return null;
  }

  return (
    <div className="mt-2 max-w-lg mx-auto px-2">
      <div className="bg-white/90 backdrop-blur-sm border border-gray-200 rounded-xl p-3 shadow-md">
        <div className="text-center mb-3">
          <h3 className="text-sm sm:text-base font-semibold text-gray-800">Choose Action</h3>
          <p className="text-xs text-gray-600 mt-1">
            What would you like to do with the drawn card?
          </p>
        </div>

        {/* Action buttons */}
        <div className="flex gap-2 mb-2">
          {/* Swap Option */}
          <button
            onClick={chooseSwap}
            className="flex items-center justify-center gap-1.5 bg-gradient-to-r from-blue-600 to-blue-700 hover:from-blue-700 hover:to-blue-800 text-white font-semibold py-2 px-3 rounded-lg shadow transition-colors flex-1"
            aria-label="Swap with existing card"
            title="Replace one of your cards with this drawn card"
          >
            <span className="text-sm">🔄</span>
            <span className="text-xs sm:text-sm">Swap</span>
          </button>

          {/* Play/Discard Option */}
          <button
            onClick={choosePlayCard}
            className="flex items-center justify-center gap-1.5 bg-gradient-to-r from-green-600 to-green-700 hover:from-green-700 hover:to-green-800 text-white font-semibold py-2 px-3 rounded-lg shadow transition-colors flex-1"
            aria-label={pendingCard.action ? "Play action card" : "Discard card"}
            title={pendingCard.action ? `Play ${pendingCard.rank} action: ${pendingCard.action}` : `Discard ${pendingCard.rank}`}
          >
            <span className="text-sm">{pendingCard.action ? '⚡' : '🗑️'}</span>
            <span className="text-xs sm:text-sm">{pendingCard.action ? 'Play' : 'Discard'}</span>
          </button>
        </div>

        {/* Help text */}
        <div className="text-center text-[10px] text-gray-500 mb-2">
          <strong>Swap:</strong> Replace card • <strong>{pendingCard.action ? 'Play' : 'Discard'}:</strong> {pendingCard.action ? 'Use action' : 'To discard'}
        </div>

        {/* Cancel Button */}
        <button
          onClick={cancelSwap}
          className="w-full bg-gradient-to-r from-gray-600 to-gray-700 hover:from-gray-700 hover:to-gray-800 text-white font-medium py-1.5 px-4 rounded-lg shadow transition-colors text-xs sm:text-sm"
          aria-label="Cancel and pass turn"
          title="Cancel and pass turn"
        >
          Cancel & Pass
        </button>
      </div>
    </div>
  );
}
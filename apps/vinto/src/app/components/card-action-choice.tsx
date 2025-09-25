// components/CardActionChoice.tsx
'use client';

import React from 'react';
import { useGameStore } from '../stores/game-store';
import { Card } from './card';

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
    <div className="mt-3 sm:mt-4 max-w-lg mx-auto px-2">
      <div className="bg-white/90 backdrop-blur-sm border border-gray-200 rounded-xl p-3 sm:p-4 shadow-md">
        <div className="text-center mb-3">
          <h3 className="text-sm sm:text-base font-semibold text-gray-800">Choose Action</h3>
          <p className="text-xs text-gray-600 mt-1">
            What would you like to do with the drawn card?
          </p>
        </div>

        {/* Show the drawn card */}
        <div className="flex justify-center mb-4">
          <div className="text-center">
            <Card card={pendingCard} revealed={true} size="md" />
            <div className="mt-1 text-xs text-gray-600">
              {pendingCard.rank} - {pendingCard.action ? `Action: ${pendingCard.action}` : 'No action'}
            </div>
          </div>
        </div>

        {/* Action buttons */}
        <div className="grid grid-cols-2 gap-2 sm:gap-3 mb-3">
          {/* Swap Option */}
          <button
            onClick={chooseSwap}
            className="flex flex-col items-center justify-center gap-1 bg-gradient-to-r from-blue-600 to-blue-700 hover:from-blue-700 hover:to-blue-800 text-white font-semibold py-3 px-3 rounded-lg shadow transition-colors"
            aria-label="Swap with existing card"
            title="Replace one of your cards with this drawn card"
          >
            <span className="text-lg">🔄</span>
            <span className="text-xs sm:text-sm">Swap</span>
          </button>

          {/* Play/Discard Option */}
          <button
            onClick={choosePlayCard}
            className="flex flex-col items-center justify-center gap-1 bg-gradient-to-r from-green-600 to-green-700 hover:from-green-700 hover:to-green-800 text-white font-semibold py-3 px-3 rounded-lg shadow transition-colors"
            aria-label={pendingCard.action ? "Play action card" : "Discard card"}
            title={pendingCard.action ? `Play ${pendingCard.rank} action: ${pendingCard.action}` : `Discard ${pendingCard.rank}`}
          >
            <span className="text-lg">{pendingCard.action ? '⚡' : '🗑️'}</span>
            <span className="text-xs sm:text-sm">{pendingCard.action ? 'Play' : 'Discard'}</span>
          </button>
        </div>

        {/* Help text */}
        <div className="text-center text-[11px] sm:text-xs text-gray-500 mb-3">
          <strong>Swap:</strong> Replace one of your cards (declare rank)
          <br />
          <strong>{pendingCard.action ? 'Play' : 'Discard'}:</strong> {pendingCard.action ? 'Execute action immediately' : 'Put directly to discard pile'}
        </div>

        {/* Cancel Button */}
        <button
          onClick={cancelSwap}
          className="w-full bg-gradient-to-r from-gray-600 to-gray-700 hover:from-gray-700 hover:to-gray-800 text-white font-semibold py-2.5 px-4 rounded-lg shadow transition-colors"
          aria-label="Cancel and pass turn"
          title="Cancel and pass turn"
        >
          Cancel & Pass
        </button>
      </div>
    </div>
  );
}
// components/CardActionChoice.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { gameStore } from '../stores/game-store';
import { getGamePhaseStore } from '../stores/game-phase-store';
import { getActionStore } from '../stores/action-store';

export const CardActionChoice = observer(() => {
  const gamePhaseStore = getGamePhaseStore();
  const { pendingCard } = getActionStore();
  if (!gamePhaseStore.isChoosingCardAction || !pendingCard) {
    return null;
  }

  return (
    <div className="mt-2 w-full max-w-4xl mx-auto px-2 min-h-[140px]">
      <div className="bg-white/90 backdrop-blur-sm border border-gray-200 rounded-xl p-3 md:p-4 shadow-md h-full flex flex-col justify-between">
        <div className="text-center mb-3">
          <h3 className="text-base font-semibold text-gray-800">
            Choose Action
          </h3>
          <p className="text-xs text-gray-600 mt-1">
            What would you like to do with the drawn card?
          </p>
        </div>

        {/* Action buttons */}
        <div className="flex gap-2 mb-2">
          {/* Swap Option */}
          <button
            onClick={() => gameStore.chooseSwap()}
            className="flex items-center justify-center gap-1.5 bg-gradient-to-r from-blue-600 to-blue-700 hover:from-blue-700 hover:to-blue-800 text-white font-semibold py-2 px-4 rounded-lg shadow-md transition-colors flex-1"
            aria-label="Swap with existing card"
            title="Replace one of your cards with this drawn card"
          >
            <span className="text-sm">🔄</span>
            <span className="text-sm">Swap</span>
          </button>

          {/* Play/Discard Option */}
          <button
            onClick={() => gameStore.choosePlayCard()}
            className="flex items-center justify-center gap-1.5 bg-gradient-to-r from-green-600 to-green-700 hover:from-green-700 hover:to-green-800 text-white font-semibold py-2 px-4 rounded-lg shadow-md transition-colors flex-1"
            aria-label={
              pendingCard.action ? 'Play action card' : 'Discard card'
            }
            title={
              pendingCard.action
                ? `Play ${pendingCard.rank} action: ${pendingCard.action}`
                : `Discard ${pendingCard.rank}`
            }
          >
            <span className="text-sm">{pendingCard.action ? '⚡' : '🗑️'}</span>
            <span className="text-sm">
              {pendingCard.action ? 'Play' : 'Discard'}
            </span>
          </button>
        </div>

        {/* Help text */}
        <div className="text-center text-2xs text-gray-500 mb-2">
          <strong>Swap:</strong> Replace one of your cards •{' '}
          <strong>{pendingCard.action ? 'Play' : 'Discard'}:</strong>{' '}
          {pendingCard.action ? 'Use action' : 'Discard directly'}
        </div>

        {/* Discard Button */}
        <button
          onClick={() => gameStore.discardCard()}
          className="w-full bg-gradient-to-r from-gray-600 to-gray-700 hover:from-gray-700 hover:to-gray-800 text-white font-medium py-2 px-4 rounded-lg shadow-md transition-colors text-base"
          aria-label="Discard drawn card"
          title="Discard drawn card without using action"
        >
          Discard
        </button>
      </div>
    </div>
  );
});

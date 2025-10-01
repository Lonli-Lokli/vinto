// components/CardActionChoice.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import {
  useGameStore,
  useGamePhaseStore,
  useActionStore,
  usePlayerStore,
} from './di-provider';

export const CardActionChoice = observer(() => {
  const gameStore = useGameStore();
  const gamePhaseStore = useGamePhaseStore();
  const { pendingCard } = useActionStore();
  const { currentPlayer } = usePlayerStore();

  // Only show for human players
  if (
    !gamePhaseStore.isChoosingCardAction ||
    !pendingCard ||
    currentPlayer?.isBot
  ) {
    return null;
  }

  return (
    <div className="mt-2 w-full max-w-4xl mx-auto px-2 min-h-[140px]">
      <div className="bg-white/95 backdrop-blur-sm border border-gray-300 rounded-lg p-2 shadow-sm h-full flex flex-col justify-between">
        <div className="text-center mb-2">
          <h3 className="text-sm font-semibold text-gray-800">Choose Action</h3>
          <p className="text-xs text-gray-600 mt-1">
            What would you like to do with the drawn card?
          </p>
        </div>

        {/* Action buttons */}
        <div className="flex gap-2 mb-2">
          {/* Swap Option */}
          <button
            onClick={() => gameStore.chooseSwap()}
            className="flex items-center justify-center gap-1.5 bg-blue-600 hover:bg-blue-700 text-white font-semibold py-1.5 px-3 rounded shadow-sm transition-colors flex-1 text-sm"
            aria-label="Swap with existing card"
            title="Replace one of your cards with this drawn card"
          >
            <span>🔄</span>
            <span>Swap</span>
          </button>

          {/* Play/Discard Option */}
          <button
            onClick={() => gameStore.choosePlayCard()}
            className="flex items-center justify-center gap-1.5 bg-emerald-600 hover:bg-emerald-700 text-white font-semibold py-1.5 px-3 rounded shadow-sm transition-colors flex-1 text-sm"
            aria-label={
              pendingCard.action ? 'Play action card' : 'Discard card'
            }
            title={
              pendingCard.action
                ? `Play ${pendingCard.rank} action: ${pendingCard.action}`
                : `Discard ${pendingCard.rank}`
            }
          >
            <span>{pendingCard.action ? '⚡' : '🗑️'}</span>
            <span>{pendingCard.action ? 'Play' : 'Discard'}</span>
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
          className="w-full bg-slate-600 hover:bg-slate-700 text-white font-medium py-1.5 px-3 rounded shadow-sm transition-colors text-sm"
          aria-label="Discard drawn card"
          title="Discard drawn card without using action"
        >
          Discard
        </button>
      </div>
    </div>
  );
});

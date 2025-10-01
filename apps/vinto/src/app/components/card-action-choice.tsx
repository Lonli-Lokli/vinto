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
    <div className="w-full h-full px-3 py-2">
      <div className="h-full bg-white/95 backdrop-blur-sm border border-gray-300 rounded-lg p-4 shadow-sm flex flex-col justify-between">
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
        <div className="text-center text-2xs text-gray-500">
          <strong>Swap:</strong> Replace one of your cards •{' '}
          <strong>{pendingCard.action ? 'Play' : 'Discard'}:</strong>{' '}
          {pendingCard.action ? 'Execute the action' : 'Discard without action'}
        </div>
      </div>
    </div>
  );
});

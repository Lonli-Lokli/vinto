// components/GamePhaseIndicators.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import {
  useGameStore,
  useGamePhaseStore,
  usePlayerStore,
  useTossInStore,
  useDeckStore,
} from './di-provider';

export const GamePhaseIndicators = observer(() => {
  const gameStore = useGameStore();
  const { phase, isSelectingSwapPosition } = useGamePhaseStore();
  const { setupPeeksRemaining } = usePlayerStore();
  const tossInStore = useTossInStore();
  const { waitingForTossIn } = tossInStore;
  const { discardPile } = useDeckStore();

  // Setup Phase Instructions
  if (phase === 'setup' && gameStore.sessionActive) {
    return (
      <div className="w-full px-3 py-1">
        <div className="bg-white border border-gray-300 rounded-lg p-3 shadow-sm">
          <div className="text-center space-y-2">
            <div className="text-sm font-semibold text-gray-800">
              🔍 Memory Phase
            </div>
            <div className="text-xs text-gray-700">
              Click any 2 of your cards to memorize them. They will be hidden
              during the game!
            </div>
            <div className="text-xs font-medium text-gray-600">
              Peeks remaining: {setupPeeksRemaining}
            </div>
            <button
              onClick={() => gameStore.finishSetup()}
              disabled={setupPeeksRemaining > 0}
              className={`py-1.5 px-3 rounded text-sm font-semibold text-white transition-colors ${
                setupPeeksRemaining > 0
                  ? 'bg-gray-400 cursor-not-allowed'
                  : 'bg-blue-500 hover:bg-blue-600 cursor-pointer'
              }`}
            >
              Start Game
            </button>
          </div>
        </div>
      </div>
    );
  }

  // Toss-in Period
  if (waitingForTossIn) {
    const topDiscardRank = discardPile.length > 0 ? discardPile[discardPile.length - 1].rank : '';

    return (
      <div className="w-full px-3 py-1">
        <div className="bg-white border border-gray-300 rounded-lg p-2 sm:p-3 shadow-sm">
          <div className="flex flex-col sm:flex-row items-center justify-between gap-2">
            {/* Info section */}
            <div className="flex-1 text-center sm:text-left">
              <div className="text-sm font-semibold text-gray-800">
                ⚡ Toss-in Time!
              </div>
              <div className="text-xs text-gray-700">
                {topDiscardRank ? `Toss matching ${topDiscardRank} cards` : 'Toss matching cards'} or continue
              </div>
              <div className="text-xs text-gray-600">
                Wrong guess = penalty card
              </div>
            </div>

            {/* Continue button */}
            <button
              onClick={() => gameStore.finishTossInPeriod()}
              className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded-lg shadow-sm transition-colors text-sm whitespace-nowrap"
            >
              Continue ▶
            </button>
          </div>
        </div>
      </div>
    );
  }

  // Card Selection Instructions
  if (isSelectingSwapPosition) {
    return (
      <div className="w-full px-3 py-1">
        <div className="bg-white border border-gray-300 rounded-lg p-3 shadow-sm">
          <div className="text-center space-y-2">
            <div className="text-sm font-semibold text-gray-800">
              🔄 Select a card to replace
            </div>
            <div className="text-xs text-gray-700">
              Click on one of your cards to swap it with the drawn card
            </div>
            <button
              onClick={() => gameStore.discardCard()}
              className="bg-slate-600 hover:bg-slate-700 text-white font-medium py-1.5 px-3 rounded shadow-sm transition-colors text-xs"
            >
              Discard Instead
            </button>
          </div>
        </div>
      </div>
    );
  }

  // Return null if no indicators needed
  return null;
});

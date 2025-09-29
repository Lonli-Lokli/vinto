// components/GamePhaseIndicators.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { gameStore } from '../stores/game-store';
import { getGamePhaseStore } from '../stores/game-phase-store';
import { getPlayerStore } from '../stores/player-store';
import { getTossInStore } from '../stores/toss-in-store';
import { getActionStore } from '../stores/action-store';
import { getDeckStore } from '../stores/deck-store';

export const GamePhaseIndicators = observer(() => {
  const { phase, isSelectingSwapPosition } = getGamePhaseStore();
  const { setupPeeksRemaining } = getPlayerStore();
  const { waitingForTossIn } = getTossInStore();
  const { tossInTimer } = getActionStore();
  const { discardPile } = getDeckStore();
  return (
    <div className="w-full max-w-lg md:max-w-full mx-auto">
      {/* Setup Phase Instructions */}
      {phase === 'setup' && gameStore.sessionActive && (
        <div className="mt-4 sm:mt-6 bg-white border border-gray-300 rounded-lg p-3 shadow-sm mx-2">
          <div className="text-center">
            <div className="text-base font-semibold text-gray-800 mb-2">
              🔍 Memory Phase
            </div>
            <div className="text-sm text-gray-700 mb-2">
              Click any 2 of your cards to memorize them. They will be hidden
              during the game!
            </div>
            <div className="text-sm font-medium text-gray-600 mb-2">
              Peeks remaining: {setupPeeksRemaining}
            </div>

            <button
              disabled={setupPeeksRemaining > 0}
              onClick={() => gameStore.finishSetup()}
              className="bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 disabled:hover:bg-gray-400 disabled:cursor-not-allowed text-white font-medium py-2 px-4 rounded shadow-sm transition-colors text-sm"
            >
              Start Game
            </button>
          </div>
        </div>
      )}

      {/* Toss-in Period */}
      {waitingForTossIn && (
        <div className="mt-4 sm:mt-6 bg-white border border-gray-300 rounded-lg p-3 shadow-sm mx-2">
          <div className="text-center">
            <div className="text-base font-semibold text-gray-800 mb-2">
              ⚡ Toss-in Time! ({tossInTimer}s)
            </div>
            <div className="text-sm text-gray-700 mb-2">
              If you have a matching card ({discardPile[0]?.rank}), click it to
              toss-in!
            </div>
            <div className="text-xs text-gray-600">
              Warning: Wrong guess = penalty card
            </div>
          </div>
        </div>
      )}

      {/* Card Selection Instructions */}
      {isSelectingSwapPosition && (
        <div className="mt-4 sm:mt-6 bg-white border border-gray-300 rounded-lg p-3 shadow-sm mx-2">
          <div className="text-center">
            <div className="text-base font-semibold text-gray-800 mb-2">
              🔄 Select a card to replace
            </div>
            <div className="text-sm text-gray-700 mb-2">
              Click on one of your cards to swap it with the drawn card
            </div>
            <button
              onClick={() => gameStore.discardCard()}
              className="bg-slate-600 hover:bg-slate-700 text-white font-medium py-1.5 px-3 rounded shadow-sm transition-colors text-sm"
            >
              Discard Instead
            </button>
          </div>
        </div>
      )}
    </div>
  );
});

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
        <div className="mt-4 sm:mt-6 bg-blue-50 border-2 border-blue-300 rounded-2xl p-4 shadow-lg mx-2">
          <div className="text-center">
            <div className="text-xl font-semibold text-blue-800 mb-2">
              🔍 Memory Phase
            </div>
            <div className="text-base text-blue-700 mb-3">
              Click any 2 of your cards to memorize them. They will be hidden
              during the game!
            </div>
            <div className="text-base font-medium text-blue-600 mb-3">
              Peeks remaining: {setupPeeksRemaining}
            </div>

            <button
              disabled={setupPeeksRemaining > 0}
              onClick={() => gameStore.finishSetup()}
              className="bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 disabled:hover:bg-gray-400 disabled:cursor-not-allowed text-white font-medium py-3 px-6 rounded-lg transition-colors"
            >
              Start Game
            </button>
          </div>
        </div>
      )}

      {/* Toss-in Period */}
      {waitingForTossIn && (
        <div className="mt-4 sm:mt-6 bg-orange-50 border-2 border-orange-300 rounded-2xl p-4 shadow-lg mx-2">
          <div className="text-center">
            <div className="text-xl font-semibold text-orange-800 mb-2">
              ⚡ Toss-in Time! ({tossInTimer}s)
            </div>
            <div className="text-base text-orange-700 mb-2">
              If you have a matching card ({discardPile[0]?.rank}), click it to
              toss-in!
            </div>
            <div className="text-sm text-orange-600">
              Warning: Wrong guess = penalty card
            </div>
          </div>
        </div>
      )}

      {/* Card Selection Instructions */}
      {isSelectingSwapPosition && (
        <div className="mt-4 sm:mt-6 bg-yellow-50 border-2 border-yellow-300 rounded-2xl p-4 shadow-lg mx-2">
          <div className="text-center">
            <div className="text-xl font-semibold text-yellow-800 mb-2">
              🔄 Select a card to replace
            </div>
            <div className="text-base text-yellow-700 mb-3">
              Click on one of your cards to swap it with the drawn card
            </div>
            <button
              onClick={() => gameStore.discardCard()}
              className="bg-gray-500 hover:bg-gray-600 text-white font-medium py-2 px-4 rounded-lg transition-colors"
            >
              Discard Instead
            </button>
          </div>
        </div>
      )}
    </div>
  );
});

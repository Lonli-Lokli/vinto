// components/GamePhaseIndicators.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { gameStore } from '../stores/game-store-mobx';

export const GamePhaseIndicators = observer(() => {
  // DEBUG: Log toss-in state
  React.useEffect(() => {
    if (gameStore.waitingForTossIn || gameStore.tossInTimer > 0) {
      const currentPlayer = gameStore.players[gameStore.currentPlayerIndex];
      console.log('DEBUG Toss-in state:', {
        waitingForTossIn: gameStore.waitingForTossIn,
        tossInTimer: gameStore.tossInTimer,
        discardTop: gameStore.discardPile[0]?.rank,
        aiThinking: gameStore.aiThinking,
        currentPlayer: currentPlayer?.name,
        isCurrentHuman: currentPlayer?.isHuman,
      });
    }
  }, [
    gameStore.waitingForTossIn,
    gameStore.tossInTimer,
    gameStore.discardPile,
    gameStore.aiThinking,
    gameStore.currentPlayerIndex,
    gameStore.players,
  ]);

  return (
    <>
      {/* DEBUG: Show toss-in state */}
      {(gameStore.waitingForTossIn || gameStore.tossInTimer > 0) && (
        <div className="mt-2 mx-auto max-w-lg bg-red-100 border border-red-400 rounded p-2 text-xs">
          DEBUG: waitingForTossIn={String(gameStore.waitingForTossIn)},
          tossInTimer={gameStore.tossInTimer}, discardTop=
          {gameStore.discardPile[0]?.rank || 'none'}, aiThinking=
          {String(gameStore.aiThinking)}, currentPlayer=
          {gameStore.players[gameStore.currentPlayerIndex]?.name}
        </div>
      )}

      {/* Setup Phase Instructions */}
      {gameStore.phase === 'setup' && (
        <div className="mt-4 sm:mt-6 mx-auto max-w-lg bg-blue-50 border-2 border-blue-300 rounded-2xl p-4 shadow-lg mx-2">
          <div className="text-center">
            <div className="text-lg font-semibold text-blue-800 mb-2">
              🔍 Memory Phase
            </div>
            <div className="text-sm text-blue-700 mb-3">
              Click any 2 of your cards to memorize them. They will be hidden
              during the game!
            </div>
            <div className="text-sm font-medium text-blue-600 mb-3">
              Peeks remaining: {gameStore.setupPeeksRemaining}
            </div>
            {gameStore.setupPeeksRemaining === 0 && (
              <button
                onClick={() => gameStore.finishSetup()}
                className="bg-blue-600 hover:bg-blue-700 text-white font-medium py-3 px-6 rounded-lg transition-colors"
              >
                Start Game
              </button>
            )}
          </div>
        </div>
      )}

      {/* Toss-in Period */}
      {gameStore.waitingForTossIn && (
        <div className="mt-4 sm:mt-6 mx-auto max-w-lg bg-orange-50 border-2 border-orange-300 rounded-2xl p-4 shadow-lg mx-2">
          <div className="text-center">
            <div className="text-lg font-semibold text-orange-800 mb-2">
              ⚡ Toss-in Time! ({gameStore.tossInTimer}s)
            </div>
            <div className="text-sm text-orange-700 mb-2">
              If you have a matching card ({gameStore.discardPile[0]?.rank}),
              click it to toss-in!
            </div>
            <div className="text-xs text-orange-600">
              Warning: Wrong guess = penalty card
            </div>
          </div>
        </div>
      )}

      {/* Card Selection Instructions */}
      {gameStore.isSelectingSwapPosition && (
        <div className="mt-4 sm:mt-6 mx-auto max-w-lg bg-yellow-50 border-2 border-yellow-300 rounded-2xl p-4 shadow-lg mx-2">
          <div className="text-center">
            <div className="text-lg font-semibold text-yellow-800 mb-2">
              🔄 Select a card to replace
            </div>
            <div className="text-sm text-yellow-700 mb-3">
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
    </>
  );
});

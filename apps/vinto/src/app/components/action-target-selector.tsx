// components/ActionTargetSelector.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { gameStore } from '../stores/game-store';

export const ActionTargetSelector = observer(() => {
  if (!gameStore.isAwaitingActionTarget || !gameStore.actionContext) {
    return null;
  }

  const { action, playerId, targetType } = gameStore.actionContext;
  const actionPlayer = gameStore.players.find((p) => p.id === playerId);
  const humanPlayer = gameStore.players.find((p) => p.isHuman);

  if (!actionPlayer || !humanPlayer) {
    return null;
  }

  const renderActionContent = () => {
    switch (targetType) {
      case 'own-card':
        return (
          <div className="max-w-lg mx-auto px-2">
            <div className="bg-blue-50 border-2 border-blue-300 rounded-xl p-3 shadow-lg">
              <div className="text-center mb-3">
                <h3 className="text-lg font-semibold text-blue-800 mb-2">
                  👁️ {action}
                </h3>
                <p className="text-sm text-blue-700">
                  Click on one of your cards to peek at it
                </p>
              </div>
              <div className="text-center text-xs text-blue-600">
                Choose which of your cards to peek at
              </div>
            </div>
          </div>
        );

      case 'opponent-card':
        return (
          <div className="max-w-lg mx-auto px-2">
            <div className="bg-red-50 border-2 border-red-300 rounded-xl p-3 shadow-lg">
              <div className="text-center mb-3">
                <h3 className="text-lg font-semibold text-red-800 mb-2">
                  🔍 {action}
                </h3>
                <p className="text-sm text-red-700">
                  Click on an opponent&apos;s card to peek at it
                </p>
              </div>
              <div className="text-center text-xs text-red-600">
                Choose which opponent card to peek at
              </div>
            </div>
          </div>
        );

      case 'swap-cards':
        return (
          <div className="max-w-lg mx-auto px-2">
            <div className="bg-purple-50 border-2 border-purple-300 rounded-xl p-3 shadow-lg">
              <div className="text-center mb-3">
                <h3 className="text-lg font-semibold text-purple-800 mb-2">
                  🔄 {action}
                </h3>
                <p className="text-sm text-purple-700">
                  Select two cards to swap ({gameStore.swapTargets.length}/2
                  selected)
                </p>
                {gameStore.swapTargets.length > 0 && (
                  <div className="text-xs text-purple-600 mt-2">
                    Selected:{' '}
                    {gameStore.swapTargets
                      .map(
                        (target, i) =>
                          `Player ${target.playerId} position ${
                            target.position + 1
                          }`
                      )
                      .join(', ')}
                  </div>
                )}
              </div>
            </div>
          </div>
        );

      case 'peek-then-swap':
        return (
          <div className="max-w-lg mx-auto px-2">
            <div className="bg-yellow-50 border-2 border-yellow-300 rounded-xl p-3 shadow-lg">
              <div className="text-center mb-3">
                <h3 className="text-lg font-semibold text-yellow-800 mb-2">
                  👑 {action}
                </h3>
                <p className="text-sm text-yellow-700">
                  Select two cards to peek at ({gameStore.peekTargets.length}/2
                  selected)
                </p>
                {gameStore.peekTargets.length > 0 && (
                  <div className="text-xs text-yellow-600 mt-2">
                    Peeked:{' '}
                    {gameStore.peekTargets
                      .map(
                        (target, i) =>
                          `${target.card?.rank} (${target.card?.value})`
                      )
                      .join(', ')}
                  </div>
                )}
                {gameStore.peekTargets.length === 2 && (
                  <div className="flex gap-2 mt-3">
                    <button
                      onClick={() => gameStore.executeQueenSwap()}
                      className="flex-1 bg-green-600 hover:bg-green-700 text-white font-medium py-2 px-3 rounded-lg transition-colors text-sm"
                    >
                      Swap Cards
                    </button>
                    <button
                      onClick={() => gameStore.skipQueenSwap()}
                      className="flex-1 bg-gray-600 hover:bg-gray-700 text-white font-medium py-2 px-3 rounded-lg transition-colors text-sm"
                    >
                      Skip Swap
                    </button>
                  </div>
                )}
              </div>
            </div>
          </div>
        );

      case 'force-draw':
        return (
          <div className="max-w-lg mx-auto px-2">
            <div className="bg-orange-50 border-2 border-orange-300 rounded-xl p-3 shadow-lg">
              <div className="text-center mb-3">
                <h3 className="text-lg font-semibold text-orange-800 mb-2">
                  🎯 {action}
                </h3>
                <p className="text-sm text-orange-700">
                  Click on an opponent to force them to draw a card
                </p>
              </div>
              <div className="text-center text-xs text-orange-600">
                Choose which opponent must draw a penalty card
              </div>
            </div>
          </div>
        );

      case 'declare-action':
        return (
          <div className="max-w-lg mx-auto px-2">
            <div className="bg-indigo-50 border-2 border-indigo-300 rounded-xl p-3 shadow-lg">
              <div className="text-center mb-3">
                <h3 className="text-lg font-semibold text-indigo-800 mb-2">
                  👑 King Declaration
                </h3>
                <p className="text-sm text-indigo-700">
                  Choose which card action to execute
                </p>
              </div>
              <div className="grid grid-cols-2 gap-2">
                {['7', '8', '9', '10', 'J', 'Q', 'A'].map((rank) => (
                  <button
                    key={rank}
                    onClick={() =>
                      gameStore.declareKingAction
                        ? gameStore.declareKingAction(rank as any)
                        : undefined
                    }
                    className="bg-indigo-600 hover:bg-indigo-700 text-white font-medium py-2 px-3 rounded-lg transition-colors text-sm"
                  >
                    {rank}
                  </button>
                ))}
              </div>
            </div>
          </div>
        );

      default:
        return (
          <div className="max-w-lg mx-auto px-2">
            <div className="bg-gray-50 border-2 border-gray-300 rounded-xl p-3 shadow-lg">
              <div className="text-center">
                <h3 className="text-lg font-semibold text-gray-800 mb-2">
                  ⚡ {action}
                </h3>
                <p className="text-sm text-gray-700">
                  Select a target for this action
                </p>
              </div>
            </div>
          </div>
        );
    }
  };

  return <>{renderActionContent()}</>;
});

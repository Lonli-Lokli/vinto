// components/action-types/CardSwap.tsx
'use client';

import { useActionStore, usePlayerStore, useGameStore } from '../di-provider';
import React from 'react';
import { observer } from 'mobx-react-lite';

export const CardSwap = observer(() => {
  const actionStore = useActionStore();
  const playerStore = usePlayerStore();
  const gameStore = useGameStore();
  if (!actionStore.actionContext) return null;
  const { action } = actionStore.actionContext;
  const swapTargets = actionStore.swapTargets;

  const getPlayerName = (playerId: string) => {
    const player = playerStore.getPlayer(playerId);
    return player?.name || 'Unknown';
  };

  return (
    <div className="w-full h-full px-3 py-2">
      <div className="bg-white/95 backdrop-blur-sm border border-gray-300 rounded-lg p-4 shadow-sm h-full flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-xs md:text-sm font-semibold text-gray-800">
            🔄 {action}
          </h3>
          <div className="text-2xs md:text-xs text-gray-500">
            {swapTargets.length}/2 selected
          </div>
        </div>

        {/* Instructions or Selected Cards Info */}
        <div className="flex-1 flex flex-col justify-center mb-2">
          {swapTargets.length === 0 ? (
            <p className="text-xs text-gray-600 text-center">
              Select two cards from different players to swap
            </p>
          ) : swapTargets.length === 1 ? (
            <div className="text-xs text-gray-600 text-center">
              <div className="mb-1">First card selected:</div>
              <div className="text-sm font-medium text-gray-800">
                {getPlayerName(swapTargets[0].playerId)} • Position{' '}
                {swapTargets[0].position + 1}
              </div>
              <p className="text-2xs text-gray-500 mt-1">
                Select second card from a different player
              </p>
            </div>
          ) : (
            <div className="text-xs text-gray-600 text-center">
              <div className="mb-1">Cards will be swapped:</div>
              <div className="text-sm font-medium text-gray-800">
                {swapTargets
                  .map(
                    (target) =>
                      `${getPlayerName(target.playerId)} pos ${
                        target.position + 1
                      }`
                  )
                  .join(' ⟷ ')}
              </div>
            </div>
          )}
        </div>

        {/* Action Buttons */}
        <div className="grid grid-cols-2 gap-2">
          {swapTargets.length > 0 && (
            <button
              onClick={() => actionStore.clearSwapTargets()}
              className="bg-slate-600 hover:bg-slate-700 text-white font-semibold py-2 px-4 rounded shadow-sm transition-colors text-sm"
            >
              🔄 Reset
            </button>
          )}
          <button
            onClick={() => {
              actionStore.clearSwapTargets();
              gameStore.confirmPeekCompletion();
            }}
            className={`bg-slate-600 hover:bg-slate-700 text-white font-semibold py-2 px-4 rounded shadow-sm transition-colors text-sm ${
              swapTargets.length === 0 ? 'col-span-2' : ''
            }`}
          >
            ⏭️ Skip
          </button>
        </div>
      </div>
    </div>
  );
});

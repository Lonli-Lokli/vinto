// components/action-types/AceAction.tsx
'use client';

import { useActionStore, useGameStore, usePlayerStore } from '../di-provider';
import React from 'react';
import { observer } from 'mobx-react-lite';

export const AceAction = observer(() => {
  const actionStore = useActionStore();
  const gameStore = useGameStore();
  const playerStore = usePlayerStore();

  if (!actionStore.actionContext) return null;
  const { action } = actionStore.actionContext;

  const humanPlayer = playerStore.humanPlayer;
  const opponents = playerStore.players.filter(p => p.id !== humanPlayer?.id);

  const handleOpponentClick = (opponentId: string) => {
    // Select the first card position (index 0) as a dummy - the action only cares about the player
    gameStore.selectActionTarget(opponentId, 0);
  };

  return (
    <div className="w-full h-full px-3 py-2">
      <div className="bg-white/95 backdrop-blur-sm border border-gray-300 rounded-lg p-4 shadow-sm h-full flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-xs md:text-sm font-semibold text-gray-800">
            🎯 {action}
          </h3>
        </div>

        {/* Instructions */}
        <div className="flex-1 flex flex-col justify-center mb-4">
          <p className="text-sm text-gray-600 text-center mb-3">
            Select an opponent to force them to draw a penalty card
          </p>

          {/* Opponent buttons */}
          <div className="grid grid-cols-1 gap-2">
            {opponents.map((opponent) => (
              <button
                key={opponent.id}
                onClick={() => handleOpponentClick(opponent.id)}
                className="bg-red-500 hover:bg-red-600 text-white font-semibold py-3 px-4 rounded-lg shadow-sm transition-colors text-base flex items-center justify-center gap-2"
              >
                <span>🎯</span>
                <span>{opponent.name}</span>
              </button>
            ))}
          </div>
        </div>

        {/* Skip Button */}
        <button
          onClick={() => gameStore.confirmPeekCompletion()}
          className="w-full bg-slate-600 hover:bg-slate-700 text-white font-semibold py-2 px-4 rounded shadow-sm transition-colors text-sm"
        >
          ⏭️ Skip
        </button>
      </div>
    </div>
  );
});

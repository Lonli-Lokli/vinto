// components/action-types/AceAction.tsx
'use client';

import { useActionStore, useGameStore, usePlayerStore } from '../di-provider';
import React from 'react';
import { observer } from 'mobx-react-lite';
import { HelpPopover } from '../help-popover';
import { getButtonVariantClasses } from '../../constants/button-colors';

export const AceAction = observer(() => {
  const actionStore = useActionStore();
  const gameStore = useGameStore();
  const playerStore = usePlayerStore();

  if (!actionStore.actionContext) return null;
  const { action } = actionStore.actionContext;

  const humanPlayer = playerStore.humanPlayer;
  const opponents = playerStore.players.filter((p) => p.id !== humanPlayer?.id);

  const handleOpponentClick = (opponentId: string) => {
    // Select the first card position (index 0) as a dummy - the action only cares about the player
    gameStore.selectActionTarget(opponentId, 0);
  };

  return (
    <div className="w-full h-full px-3 py-2">
      <div className="bg-white/98 backdrop-blur-sm supports-[backdrop-filter]:bg-white/95 border border-gray-300 rounded-lg p-4 shadow-sm h-full flex flex-col">
        {/* Header */}
        <div className="flex flex-row items-center justify-between mb-2">
          <h3 className="text-xs md:text-sm font-semibold text-gray-800 leading-tight">
            🎯 {action}
          </h3>
          <HelpPopover title="Ace Action" rank="A" />
        </div>

        {/* Instructions */}
        <div className="flex-1 flex flex-col justify-center mb-4">
          <p className="text-sm text-gray-600 text-center mb-3 leading-normal">
            Select an opponent to force them to draw a penalty card
          </p>

          {/* Opponent buttons */}
          <div className="grid grid-cols-1 gap-2">
            {opponents.map((opponent) => (
              <button
                key={opponent.id}
                onClick={() => handleOpponentClick(opponent.id)}
                className={`${getButtonVariantClasses('secondary')} py-3 px-4 rounded-lg text-base flex flex-row items-center justify-center gap-2 min-h-[44px]`}
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
          className={`w-full ${getButtonVariantClasses('neutral')} py-2 px-4 text-sm min-h-[44px]`}
        >
          ⏭️ Skip
        </button>
      </div>
    </div>
  );
});

// components/action-types/CardSwap.tsx
'use client';

import { useActionStore, usePlayerStore, useGameStore } from '../di-provider';
import React from 'react';
import { observer } from 'mobx-react-lite';
import { HelpPopover } from '../help-popover';
import { ResetButton, SkipButton } from '../buttons';

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
      <div className="bg-surface-primary/95 backdrop-blur-sm border border-primary rounded-lg p-4 shadow-sm h-full flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-xs md:text-sm font-semibold text-primary">
            🔄 {action}
          </h3>
          <div className="flex items-center gap-2">
            <div className="text-2xs md:text-xs text-secondary">
              {swapTargets.length}/2 selected
            </div>
            <HelpPopover title="Swap Cards" rank="J" />
          </div>
        </div>

        {/* Instructions or Selected Cards Info */}
        <div className="flex-1 flex flex-col justify-center mb-2">
          {swapTargets.length === 0 ? (
            <p className="text-xs text-secondary text-center">
              Select any two cards to swap (from any players)
            </p>
          ) : swapTargets.length === 1 ? (
            <div className="text-xs text-secondary text-center">
              <div className="mb-1">First card selected:</div>
              <div className="text-sm font-medium text-primary">
                {getPlayerName(swapTargets[0].playerId)} • Position{' '}
                {swapTargets[0].position + 1}
              </div>
              <p className="text-2xs text-secondary mt-1">
                Select second card to swap
              </p>
            </div>
          ) : (
            <div className="text-xs text-secondary text-center">
              <div className="mb-1">Cards will be swapped:</div>
              <div className="text-sm font-medium text-primary">
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
            <ResetButton
              onClick={() => actionStore.clearSwapTargets()}
              className="py-2 px-4 text-sm"
            />
          )}
          <SkipButton
            onClick={() => {
              actionStore.clearSwapTargets();
              gameStore.confirmPeekCompletion();
            }}
            className={`py-2 px-4 text-sm ${
              swapTargets.length === 0 ? 'col-span-2' : ''
            }`}
          />
        </div>
      </div>
    </div>
  );
});

// components/action-types/QueenAction.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { HelpPopover } from '../help-popover';
import { useActionStore, useGameStore } from '../di-provider';

export const QueenAction = observer(() => {
  const gameStore = useGameStore();
  const actionStore = useActionStore();
  if (!actionStore.actionContext) return null;
  const {
    peekTargets,
    actionContext: { action },
  } = actionStore;
  const { executeQueenSwap, skipQueenSwap } = gameStore;

  const hasBothCards = peekTargets.length === 2;

  return (
    <div className="w-full h-full px-2 py-1.5">
      <div className="bg-white/98 backdrop-blur-sm supports-[backdrop-filter]:bg-white/95 border border-gray-300 rounded-lg p-2 shadow-sm h-full flex flex-col">
        {/* Header */}
        <div className="flex flex-row items-center justify-between mb-1 flex-shrink-0">
          <h3 className="text-xs font-semibold text-gray-800 leading-tight">
            👑 {action}
          </h3>
          <div className="flex flex-row items-center gap-1">
            <div className="text-xs text-gray-500 leading-tight">
              {peekTargets.length}/2 selected
            </div>
            <HelpPopover title="Queen Action" rank="Q" />
          </div>
        </div>

        {/* Instructions - only show when cards not yet selected */}
        <div className="flex-1 flex flex-col justify-center mb-1 min-h-0">
          {!hasBothCards && (
            <div className="text-center space-y-1">
              <p className="text-xs text-gray-800 font-medium leading-tight">
                👁️ Peek at two cards from different players
              </p>
              <p className="text-xs text-gray-600 leading-tight">
                One card may be yours, one must be from another player
              </p>
              <p className="text-xs text-gray-500 leading-tight">
                Then decide whether to swap them
              </p>
              {peekTargets.length === 1 && (
                <p className="text-xs text-emerald-600 font-semibold leading-tight">
                  ✓ First card selected - choose a card from a different player
                </p>
              )}
            </div>
          )}
        </div>

        {/* Action Buttons - only show when both cards selected */}
        {hasBothCards && (
          <div className="grid grid-cols-2 gap-1 flex-shrink-0">
            <button
              onClick={() => executeQueenSwap()}
              className="bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800 text-white font-semibold py-1.5 px-2 rounded shadow-sm transition-colors text-xs min-h-[36px]"
            >
              🔄 Swap Cards
            </button>
            <button
              onClick={() => skipQueenSwap()}
              className="bg-slate-600 hover:bg-slate-700 active:bg-slate-800 text-white font-semibold py-1.5 px-2 rounded shadow-sm transition-colors text-xs min-h-[36px]"
            >
              ⏭️ Skip
            </button>
          </div>
        )}
      </div>
    </div>
  );
});

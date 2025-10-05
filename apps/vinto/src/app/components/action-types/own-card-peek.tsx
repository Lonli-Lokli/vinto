// components/action-types/OwnCardPeek.tsx
'use client';

import { useActionStore, usePlayerStore, useGameStore } from '../di-provider';
import React from 'react';
import { observer } from 'mobx-react-lite';
import { HelpPopover } from '../help-popover';

export const OwnCardPeek = observer(() => {
  const gameStore = useGameStore();
  const actionStore = useActionStore();
  const playerStore = usePlayerStore();
  const { action } = actionStore.actionContext ?? {};

  const humanPlayer = playerStore.humanPlayer;
  const hasRevealedCard =
    humanPlayer && humanPlayer.temporarilyVisibleCards.size > 0;

  return (
    <div className="w-full h-full px-3 py-2">
      <div className="bg-white/95 backdrop-blur-sm border border-gray-300 rounded-lg p-4 shadow-sm h-full flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-xs md:text-sm font-semibold text-gray-800">
            👁️ {action}
          </h3>
          <div className="flex items-center gap-2">
            {hasRevealedCard && (
              <div className="text-2xs md:text-xs text-green-600 font-medium">
                ✓ Card Revealed
              </div>
            )}
            <HelpPopover title="Peek at Own Card" rank="7" />
          </div>
        </div>

        {/* Instructions or Confirmation */}
        <div className="flex-1 flex flex-col justify-center mb-2">
          {!hasRevealedCard ? (
            <p className="text-xs text-gray-600 text-center">
              Click on one of your cards to peek at it
            </p>
          ) : (
            <p className="text-xs text-gray-600 text-center">
              You have peeked at your card
            </p>
          )}
        </div>

        {/* Action Buttons */}
        {hasRevealedCard ? (
          <button
            onClick={() => gameStore.confirmPeekCompletion()}
            className="w-full bg-emerald-600 hover:bg-emerald-700 text-white font-semibold py-2 px-4 rounded shadow-sm transition-colors text-sm"
          >
            Continue
          </button>
        ) : (
          <button
            onClick={() => {
              playerStore.clearTemporaryCardVisibility();
              gameStore.confirmPeekCompletion();
            }}
            className="w-full bg-slate-600 hover:bg-slate-700 text-white font-semibold py-2 px-4 rounded shadow-sm transition-colors text-sm"
          >
            ⏭️ Skip
          </button>
        )}
      </div>
    </div>
  );
});

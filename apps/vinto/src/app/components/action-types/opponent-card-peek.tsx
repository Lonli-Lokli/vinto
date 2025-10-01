// components/action-types/OpponentCardPeek.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { useActionStore, usePlayerStore, useGameStore } from '../di-provider';

export const OpponentCardPeek = observer(() => {
  const gameStore = useGameStore();
  const actionStore = useActionStore();
  const playerStore = usePlayerStore();

  if (!actionStore.actionContext) return null;
  const { action } = actionStore.actionContext;

  const humanPlayer = playerStore.humanPlayer;
  const hasRevealedCard =
    humanPlayer && humanPlayer.temporarilyVisibleCards.size > 0;

  return (
    <div className="w-full max-w-4xl mx-auto px-3 py-2 min-h-[140px]">
      <div className="bg-white/95 backdrop-blur-sm border border-gray-300 rounded-lg p-2 shadow-sm h-full flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-xs md:text-sm font-semibold text-gray-800">
            🔍 {action}
          </h3>
          {hasRevealedCard && (
            <div className="text-2xs md:text-xs text-green-600 font-medium">
              ✓ Card Revealed
            </div>
          )}
        </div>

        {/* Instructions or Confirmation */}
        <div className="flex-1 flex flex-col justify-center mb-2">
          {!hasRevealedCard ? (
            <p className="text-xs text-gray-600 text-center">
              Click on an opponent&apos;s card to peek at it
            </p>
          ) : (
            <p className="text-xs text-gray-600 text-center">
              You have peeked at opponent&apos;s card
            </p>
          )}
        </div>

        {/* Confirmation Button */}
        {hasRevealedCard && (
          <button
            onClick={() => gameStore.confirmPeekCompletion()}
            className="w-full bg-emerald-600 hover:bg-emerald-700 text-white font-semibold py-2 px-4 rounded shadow-sm transition-colors text-sm"
          >
            Continue
          </button>
        )}
      </div>
    </div>
  );
});

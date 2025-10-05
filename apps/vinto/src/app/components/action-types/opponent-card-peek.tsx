// components/action-types/OpponentCardPeek.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { HelpPopover } from '../help-popover';
import { getButtonClasses } from '../../constants/button-colors';
import { useActionStore, usePlayerStore, useGameStore } from '../di-provider';

export const OpponentCardPeek = observer(() => {
  const gameStore = useGameStore();
  const actionStore = useActionStore();
  const playerStore = usePlayerStore();

  if (!actionStore.actionContext) return null;
  const { action } = actionStore.actionContext;

  // Check if any player has temporarily visible cards (the peeked opponent card)
  const hasRevealedCard = playerStore.players.some(
    (p) => p.temporarilyVisibleCards.size > 0
  );

  return (
    <div className="w-full h-full px-3 py-2">
      <div className="bg-white/95 backdrop-blur-sm border border-gray-300 rounded-lg p-4 shadow-sm h-full flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-xs md:text-sm font-semibold text-gray-800">
            🔍 {action}
          </h3>
          <div className="flex items-center gap-2">
            {hasRevealedCard && (
              <div className="text-2xs md:text-xs text-green-600 font-medium">
                ✓ Card Revealed
              </div>
            )}
            <HelpPopover title="Peek at Opponent Card" rank="9" />
          </div>
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

        {/* Action Buttons */}
        {hasRevealedCard ? (
          <button
            onClick={() => gameStore.confirmPeekCompletion()}
            className={`w-full ${getButtonClasses('continue')} py-2 px-4 text-sm`}
          >
            Continue
          </button>
        ) : (
          <button
            onClick={() => {
              // Skip the peek action
              playerStore.clearTemporaryCardVisibility();
              gameStore.confirmPeekCompletion();
            }}
            className={`w-full ${getButtonClasses('skip')} py-2 px-4 text-sm`}
          >
            Skip
          </button>
        )}
      </div>
    </div>
  );
});

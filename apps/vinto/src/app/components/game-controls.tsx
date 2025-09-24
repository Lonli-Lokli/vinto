// components/GameControls.tsx
'use client';

import React from 'react';
import { Card as CardType } from '../shapes';

interface GameControlsProps {
  currentPlayerIsHuman: boolean;
  phase: 'setup' | 'playing' | 'final' | 'scoring';
  isSelectingSwapPosition: boolean;
  waitingForTossIn: boolean;
  drawPileLength: number;
  discardPile: CardType[];
  onDrawCard: () => void;
  onTakeFromDiscard: () => void;
  onCallVinto: () => void;
}

export function GameControls({
  currentPlayerIsHuman,
  phase,
  isSelectingSwapPosition,
  waitingForTossIn,
  drawPileLength,
  discardPile,
  onDrawCard,
  onTakeFromDiscard,
  onCallVinto,
}: GameControlsProps) {
  if (
    !currentPlayerIsHuman ||
    phase !== 'playing' ||
    isSelectingSwapPosition ||
    waitingForTossIn
  ) {
    return null;
  }

  return (
    <div className="mt-3 sm:mt-4 max-w-lg mx-auto px-2">
      <div className="bg-white/90 backdrop-blur-sm border border-gray-200 rounded-xl p-3 sm:p-4 shadow-md">
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-sm sm:text-base font-semibold text-gray-800">Your turn</h3>
          <div className="text-[11px] sm:text-xs text-gray-500">Choose one</div>
        </div>

        <div className="grid grid-cols-2 gap-2 sm:gap-3">
          {/* Draw from Deck */}
          <button
            onClick={onDrawCard}
            disabled={drawPileLength === 0}
            className="flex items-center justify-center gap-2 bg-gradient-to-r from-blue-600 to-blue-700 hover:from-blue-700 hover:to-blue-800 disabled:from-gray-400 disabled:to-gray-500 disabled:cursor-not-allowed text-white font-semibold py-2.5 sm:py-3 px-3 sm:px-4 rounded-lg shadow transition-colors"
            aria-label="Draw from deck"
            title="Draw from deck"
          >
            <span className="text-base sm:text-lg">🎯</span>
            <span className="text-sm sm:text-base">Draw</span>
          </button>

          {/* Take from Discard */}
          <button
            onClick={onTakeFromDiscard}
            disabled={!discardPile.length || !discardPile[0]?.action}
            className="flex items-center justify-center gap-2 bg-gradient-to-r from-green-600 to-green-700 hover:from-green-700 hover:to-green-800 disabled:from-gray-400 disabled:to-gray-500 disabled:cursor-not-allowed text-white font-semibold py-2.5 sm:py-3 px-3 sm:px-4 rounded-lg shadow transition-colors"
            aria-label="Take from discard"
            title="Take from discard"
          >
            <span className="text-base sm:text-lg">♻️</span>
            <span className="text-sm sm:text-base">Take</span>
          </button>
        </div>

        {/* Hint row (condensed) */}
        <div className="mt-2 text-[11px] sm:text-xs text-gray-500 text-center">
          {discardPile[0]?.action
            ? `Top discard: ${discardPile[0].rank} • ${discardPile[0].action}`
            : 'Only action cards (7–K) can be taken'}
        </div>

        <div className="mt-3 sm:mt-4">
          <button
            onClick={onCallVinto}
            className="w-full bg-gradient-to-r from-red-600 to-red-700 hover:from-red-700 hover:to-red-800 text-white font-semibold py-2.5 sm:py-3 px-4 rounded-lg shadow transition-colors"
            aria-label="Call Vinto"
            title="Call Vinto"
          >
            🏆 Call Vinto
          </button>
        </div>
      </div>
    </div>
  );
}

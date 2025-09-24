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
    <div className="mt-4 sm:mt-6 max-w-lg mx-auto px-2">
      <div className="bg-white/95 backdrop-blur-sm border border-gray-200 rounded-2xl p-6 shadow-xl">
        <div className="text-center mb-4">
          <h3 className="text-lg font-semibold text-gray-800 mb-2">
            Your Turn
          </h3>
          <p className="text-sm text-gray-600">Choose one option:</p>
        </div>

        <div className="space-y-3">
          {/* Option A: Draw from Draw Pile */}
          <button
            onClick={onDrawCard}
            disabled={drawPileLength === 0}
            className="w-full bg-gradient-to-r from-blue-600 to-blue-700 hover:from-blue-700 hover:to-blue-800 disabled:from-gray-400 disabled:to-gray-500 disabled:cursor-not-allowed text-white font-semibold py-4 px-6 rounded-xl shadow-lg transition-all transform hover:scale-[1.02] active:scale-[0.98]"
          >
            <div className="text-left">
              <div className="text-lg">🎯 Option A: Draw from Deck</div>
              <div className="text-sm opacity-90">
                Draw card → Choose to play action or swap
              </div>
            </div>
          </button>

          {/* Option B: Take from Discard Pile */}
          <button
            onClick={onTakeFromDiscard}
            disabled={!discardPile.length || !discardPile[0]?.action}
            className="w-full bg-gradient-to-r from-green-600 to-green-700 hover:from-green-700 hover:to-green-800 disabled:from-gray-400 disabled:to-gray-500 disabled:cursor-not-allowed text-white font-semibold py-4 px-6 rounded-xl shadow-lg transition-all transform hover:scale-[1.02] active:scale-[0.98]"
          >
            <div className="text-left">
              <div className="text-lg">♻️ Option B: Take from Discard</div>
              <div className="text-sm opacity-90">
                {discardPile[0]?.action
                  ? `Take ${discardPile[0].rank} → Use its action immediately`
                  : 'Only action cards (7-K) can be taken'}
              </div>
            </div>
          </button>
        </div>

        <div className="mt-4 pt-4 border-t border-gray-200">
          <button
            onClick={onCallVinto}
            className="w-full bg-gradient-to-r from-red-600 to-red-700 hover:from-red-700 hover:to-red-800 disabled:from-gray-400 disabled:to-gray-500 disabled:cursor-not-allowed text-white font-semibold py-3 px-6 rounded-xl shadow-lg transition-all transform hover:scale-[1.02] active:scale-[0.98]"
          >
            🏆 CALL VINTO!
          </button>
        </div>
      </div>
    </div>
  );
}

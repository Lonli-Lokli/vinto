// components/GameControls.tsx
'use client';

import React from 'react';
import { useGameStore } from '../stores/game-store';

export function GameControls() {
  const {
    players,
    currentPlayerIndex,
    phase,
    isSelectingSwapPosition,
    isDeclaringRank,
    waitingForTossIn,
    drawPile,
    discardPile,
    turnCount,
    drawCard,
    takeFromDiscard,
    callVinto,
  } = useGameStore();

  const currentPlayer = players[currentPlayerIndex];

  const handleDrawCard = () => {
    if (currentPlayer && currentPlayer.isHuman) {
      drawCard();
    }
  };
  if (
    !currentPlayer?.isHuman ||
    phase !== 'playing' ||
    isSelectingSwapPosition ||
    isDeclaringRank ||
    waitingForTossIn
  ) {
    return null;
  }

  // Check if this is the first human turn (turn 0 = human's first turn)
  const isFirstHumanTurn = turnCount === 0;

  // Check if discard pile top card can be taken (has action and not played)
  const topDiscardCard = discardPile[0];
  const canTakeFromDiscard = topDiscardCard &&
    topDiscardCard.action &&
    !topDiscardCard.played &&
    !isFirstHumanTurn; // First turn must be draw

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
            onClick={handleDrawCard}
            disabled={drawPile.length === 0}
            className="flex items-center justify-center gap-2 bg-gradient-to-r from-blue-600 to-blue-700 hover:from-blue-700 hover:to-blue-800 disabled:from-gray-400 disabled:to-gray-500 disabled:cursor-not-allowed text-white font-semibold py-2.5 sm:py-3 px-3 sm:px-4 rounded-lg shadow transition-colors"
            aria-label="Draw new card from deck"
            title="Draw a new card from the deck"
          >
            <span className="text-base sm:text-lg">🎯</span>
            <span className="text-sm sm:text-base">Draw New</span>
          </button>

          {/* Take from Discard */}
          <button
            onClick={takeFromDiscard}
            disabled={!canTakeFromDiscard}
            className="flex items-center justify-center gap-2 bg-gradient-to-r from-green-600 to-green-700 hover:from-green-700 hover:to-green-800 disabled:from-gray-400 disabled:to-gray-500 disabled:cursor-not-allowed text-white font-semibold py-2.5 sm:py-3 px-3 sm:px-4 rounded-lg shadow transition-colors"
            aria-label="Take unplayed card from discard pile"
            title={canTakeFromDiscard
              ? `Take ${topDiscardCard?.rank} (${topDiscardCard?.action}) from discard pile`
              : 'Cannot take - card already played or no action cards available'
            }
          >
            <span className="text-base sm:text-lg">♻️</span>
            <span className="text-sm sm:text-base">Play Card</span>
          </button>
        </div>

        {/* Hint row (condensed) */}
        <div className="mt-2 text-[11px] sm:text-xs text-gray-500 text-center">
          {isFirstHumanTurn
            ? 'First turn: must draw from deck'
            : topDiscardCard?.action
            ? topDiscardCard.played
              ? `${topDiscardCard.rank} already played - cannot take`
              : `Top discard: ${topDiscardCard.rank} • ${topDiscardCard.action}`
            : 'Only unplayed action cards (7–K) can be taken'}
        </div>

        <div className="mt-3 sm:mt-4">
          <button
            onClick={callVinto}
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

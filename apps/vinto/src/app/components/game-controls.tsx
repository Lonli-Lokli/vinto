// components/GameControls.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { gameStore } from '../stores/game-store';

export const GameControls = observer(() => {
  const currentPlayer = gameStore.players[gameStore.currentPlayerIndex];

  const handleDrawCard = () => {
    if (currentPlayer && currentPlayer.isHuman) {
      gameStore.drawCard();
    }
  };

  // Show toss-in skip controls when processing toss-in queue for human
  const isHumanTossInAction =
    gameStore.isProcessingTossInQueue &&
    gameStore.actionContext?.playerId ===
      gameStore.players.find((p) => p.isHuman)?.id;

  if (isHumanTossInAction) {
    return (
      <div className="max-w-lg mx-auto px-3 py-2 min-h-[140px]">
        <div className="bg-white/95 backdrop-blur-sm border border-gray-200 rounded-xl p-3 shadow-lg h-full flex flex-col justify-center">
          <div className="flex items-center justify-between mb-2">
            <h3 className="text-sm font-semibold text-gray-800">
              Toss-in Action: {gameStore.tossInQueue[0]?.card.rank} (
              {gameStore.tossInQueue[0]?.card.action})
            </h3>
          </div>

          <div className="text-xs text-gray-600 mb-3 text-center">
            You can execute this action or skip it
          </div>

          <button
            onClick={() => gameStore.skipCurrentTossInAction()}
            className="w-full bg-gradient-to-r from-gray-600 to-gray-700 hover:from-gray-700 hover:to-gray-800 text-white font-semibold py-2 px-4 rounded-lg shadow transition-colors text-sm"
            aria-label="Skip toss-in action"
            title="Skip this toss-in action and continue to next"
          >
            ⏭️ Skip Action
          </button>
        </div>
      </div>
    );
  }

  // Hide controls during special game states
  const shouldHide =
    gameStore.phase !== 'playing' ||
    gameStore.isSelectingSwapPosition ||
    gameStore.isChoosingCardAction ||
    gameStore.isDeclaringRank ||
    gameStore.waitingForTossIn ||
    gameStore.finalTurnTriggered ||
    gameStore.isAwaitingActionTarget ||
    gameStore.isProcessingTossInQueue; // Also hide during toss-in processing

  if (shouldHide) {
    return null;
  }

  // Show vinto-only controls when business logic allows it
  const showVintoOnly = gameStore.canCallVintoAfterHumanTurn;

  // Show full controls only for human player's turn
  const showFullControls = currentPlayer?.isHuman;

  // Hide completely if neither condition is met
  if (!showVintoOnly && !showFullControls) {
    return null;
  }

  // Check if this is the first human turn (turn 0 = human's first turn)
  const isFirstHumanTurn = gameStore.turnCount === 0;

  // Check if discard pile top card can be taken (has action and not played)
  const topDiscardCard = gameStore.discardPile[0];
  const canTakeFromDiscard =
    topDiscardCard &&
    topDiscardCard.action &&
    !topDiscardCard.played &&
    !isFirstHumanTurn; // First turn must be draw

  // Show vinto-only controls during bot delay
  if (showVintoOnly) {
    return (
      <div className="max-w-lg mx-auto px-3 py-2 min-h-[140px]">
        <div className="bg-white/95 backdrop-blur-sm border border-gray-200 rounded-xl p-3 shadow-lg h-full flex flex-col justify-center">
          <div className="flex items-center justify-center mb-2">
            <h3 className="text-sm font-semibold text-gray-800">
              Call Vinto before next player&apos;s turn
            </h3>
          </div>
          <div>
            <button
              onClick={() => gameStore.callVinto()}
              className="w-full bg-gradient-to-r from-red-600 to-red-700 hover:from-red-700 hover:to-red-800 text-white font-semibold py-2 px-4 rounded-lg shadow transition-colors text-sm"
              aria-label="Call Vinto"
              title="Call Vinto - End the game and start final scoring round"
            >
              🏆 Call Vinto
            </button>
          </div>
        </div>
      </div>
    );
  }

  // Show full controls for human player's turn
  return (
    <div className="max-w-lg mx-auto px-3 py-2">
      <div className="bg-white/95 backdrop-blur-sm border border-gray-200 rounded-xl p-3 shadow-lg">
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-sm font-semibold text-gray-800">Your turn</h3>
          <div className="text-[10px] text-gray-500">Choose one</div>
        </div>

        <div className="grid grid-cols-2 gap-2">
          {/* Draw from Deck */}
          <button
            onClick={handleDrawCard}
            disabled={gameStore.drawPile.length === 0}
            className="flex items-center justify-center gap-1.5 bg-gradient-to-r from-blue-600 to-blue-700 hover:from-blue-700 hover:to-blue-800 disabled:from-gray-400 disabled:to-gray-500 disabled:cursor-not-allowed text-white font-semibold py-2 px-3 rounded-lg shadow transition-colors text-sm"
            aria-label="Draw new card from deck"
            title="Draw a new card from the deck"
          >
            <span className="text-sm">🎯</span>
            <span>Draw New</span>
          </button>

          {/* Take from Discard */}
          <button
            onClick={() => gameStore.takeFromDiscard()}
            disabled={!canTakeFromDiscard}
            className="flex items-center justify-center gap-1.5 bg-gradient-to-r from-green-600 to-green-700 hover:from-green-700 hover:to-green-800 disabled:from-gray-400 disabled:to-gray-500 disabled:cursor-not-allowed text-white font-semibold py-2 px-3 rounded-lg shadow transition-colors text-sm"
            aria-label="Take unplayed card from discard pile"
            title={
              canTakeFromDiscard
                ? `Take ${topDiscardCard?.rank} (${topDiscardCard?.action}) from discard pile`
                : 'Cannot take - card already played or no action cards available'
            }
          >
            <span className="text-sm">♻️</span>
            <span>Play Card</span>
          </button>
        </div>

        {/* Hint row (condensed) */}
        <div className="mt-1.5 text-[10px] text-gray-500 text-center">
          {isFirstHumanTurn
            ? 'First turn: must draw from deck'
            : topDiscardCard?.action
            ? topDiscardCard.played
              ? `${topDiscardCard.rank} already played - cannot take`
              : `Top discard: ${topDiscardCard.rank} • ${topDiscardCard.action}`
            : 'Only unplayed action cards (7–K) can be taken'}
        </div>

        <div className="mt-2">
          <button
            onClick={() => gameStore.callVinto()}
            className="w-full bg-gradient-to-r from-red-600 to-red-700 hover:from-red-700 hover:to-red-800 text-white font-semibold py-2 px-4 rounded-lg shadow transition-colors text-sm"
            aria-label="Call Vinto"
            title="Call Vinto"
          >
            🏆 Call Vinto
          </button>
        </div>
      </div>
    </div>
  );
});

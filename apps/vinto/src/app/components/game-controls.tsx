// components/GameControls.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { gameStore } from '../stores/game-store';
import { getPlayerStore } from '../stores/player-store';
import { getGamePhaseStore } from '../stores/game-phase-store';
import { getDeckStore } from '../stores/deck-store';
import { getTossInStore } from '../stores/toss-in-store';

export const GameControls = observer(() => {
  const playerStore = getPlayerStore();
  const { players, currentPlayer } = playerStore;
  const {
    isSelectingSwapPosition,
    isAwaitingActionTarget,
    isTossQueueProcessing,
    phase,
    isChoosingCardAction,
    isDeclaringRank,
    finalTurnTriggered,
  } = getGamePhaseStore();
  const tossInStore = getTossInStore();
  const { waitingForTossIn } = tossInStore;

  const handleDrawCard = () => {
    if (currentPlayer && currentPlayer.isHuman) {
      gameStore.drawCard();
    }
  };

  // Determine what content to show but always use same container
  const getControlContent = () => {
    // Show toss-in skip controls when executing toss-in action for human
    // Check if there's a toss-in action in queue and it's the human's action
    const currentTossInAction = tossInStore.currentTossInAction;
    const hasHumanTossInAction =
      currentTossInAction &&
      currentTossInAction.playerId === players.find((p) => p.isHuman)?.id;

    const isHumanTossInAction =
      hasHumanTossInAction && (isTossQueueProcessing || isAwaitingActionTarget);

    if (isHumanTossInAction) {
      return {
        type: 'toss-in',
        title: `Toss-in Action: ${currentTossInAction.card.rank} (${currentTossInAction.card.action})`,
        subtitle: 'You can execute this action or skip it',
      };
    }

    // Hide controls during special game states
    const shouldHide =
      (phase !== 'playing' && phase !== 'final') ||
      isSelectingSwapPosition ||
      isChoosingCardAction ||
      isDeclaringRank ||
      waitingForTossIn ||
      finalTurnTriggered ||
      isAwaitingActionTarget ||
      isTossQueueProcessing ||
      currentPlayer?.isBot;

    if (shouldHide) {
      return { type: 'hidden' };
    }

    // Show vinto-only controls when business logic allows it
    const showVintoOnly = gameStore.canCallVintoAfterHumanTurn;

    if (showVintoOnly) {
      return {
        type: 'vinto-only',
        title: "Call Vinto before next player's turn",
        subtitle: 'End the game and start final scoring',
      };
    }

    // Show full controls only for human player's turn
    const showFullControls = currentPlayer?.isHuman;

    if (showFullControls) {
      return {
        type: 'full-controls',
        title: 'Your turn',
        subtitle: 'Choose one action',
      };
    }

    return { type: 'hidden' };
  };

  const controlContent = getControlContent();

  // Always return null when hidden to prevent layout jumps
  if (controlContent.type === 'hidden') {
    return null;
  }

  // Single consistent container for all states
  return (
    <div className="w-full max-w-4xl mx-auto px-3 py-2 min-h-[140px]">
      <div className="bg-white/95 backdrop-blur-sm border border-gray-300 rounded-lg p-2 shadow-sm h-full flex flex-col">
        {/* Header - consistent across all states */}
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-xs md:text-sm font-semibold text-gray-800">
            {controlContent.title}
          </h3>
          {controlContent.subtitle && (
            <div className="text-2xs md:text-xs text-gray-500 hidden sm:block">
              {controlContent.subtitle}
            </div>
          )}
        </div>

        {/* Main content area - responsive to content type */}
        <div className="flex-1 flex flex-col justify-center">
          {controlContent.type === 'toss-in' && <TossInControls />}

          {controlContent.type === 'vinto-only' && <VintoOnlyControls />}

          {controlContent.type === 'full-controls' && (
            <FullTurnControls handleDrawCard={handleDrawCard} />
          )}
        </div>
      </div>
    </div>
  );
});

// Sub-components for different control states
const TossInControls = () => {
  const tossInStore = getTossInStore();
  return (
    <div className="space-y-2">
      <div className="text-sm text-gray-600 text-center">
        Execute the action or skip to continue
      </div>
      <button
        onClick={() => tossInStore.skipCurrentAction()}
        className="w-full bg-poker-green-700 hover:bg-poker-green-800 text-white font-semibold py-2 px-4 rounded shadow-sm transition-colors text-sm"
        aria-label="Skip toss-in action"
      >
        ⏭️ Skip Action
      </button>
    </div>
  );
};

const VintoOnlyControls = () => (
  <div className="space-y-2">
    <button
      onClick={() => gameStore.callVinto()}
      className="w-full bg-amber-600 hover:bg-amber-700 text-white font-semibold py-2 px-4 rounded shadow-sm transition-colors text-sm"
      aria-label="Call Vinto"
    >
      🏆 Call Vinto
    </button>
  </div>
);

const FullTurnControls = ({
  handleDrawCard,
}: {
  handleDrawCard: () => void;
}) => {
  const playerStore = getPlayerStore();
  const { discardPile, drawPile } = getDeckStore();
  // Check if this is the first human turn
  const isFirstHumanTurn = playerStore.turnCount === 0;

  // Check if discard pile top card can be taken
  const topDiscardCard = discardPile[0];
  const canTakeFromDiscard =
    topDiscardCard &&
    topDiscardCard.action &&
    !topDiscardCard.played &&
    !isFirstHumanTurn;

  return (
    <div className="space-y-2">
      {/* Mobile: Stack vertically, Desktop: 2-column grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
        {/* Draw from Deck */}
        <button
          onClick={handleDrawCard}
          disabled={drawPile.length === 0}
          className="flex items-center justify-center gap-2 bg-emerald-600 hover:bg-emerald-700 disabled:bg-gray-400 disabled:cursor-not-allowed text-white font-semibold py-1.5 px-3 rounded shadow-sm transition-colors text-sm"
          aria-label="Draw new card from deck"
        >
          <span>🎯</span>
          <span>Draw New</span>
        </button>

        {/* Take from Discard */}
        <button
          onClick={() => gameStore.takeFromDiscard()}
          disabled={!canTakeFromDiscard}
          className="flex items-center justify-center gap-2 bg-poker-green-600 hover:bg-poker-green-700 disabled:bg-gray-400 disabled:cursor-not-allowed text-white font-semibold py-1.5 px-3 rounded shadow-sm transition-colors text-sm"
          aria-label="Take unplayed card from discard pile"
        >
          <span>♻️</span>
          <span>Play Card</span>
        </button>
      </div>

      {/* Hint text - more prominent */}
      <div className="text-xs text-gray-500 text-center px-2">
        {isFirstHumanTurn
          ? 'First turn: must draw from deck'
          : topDiscardCard?.action
          ? topDiscardCard.played
            ? `${topDiscardCard.rank} already played - cannot take`
            : `Top discard: ${topDiscardCard.rank} • ${topDiscardCard.action}`
          : 'Only unplayed action cards (7–K) can be taken'}
      </div>

      {/* Call Vinto - always available during turn */}
      <button
        onClick={() => gameStore.callVinto()}
        className="w-full bg-amber-600 hover:bg-amber-700 text-white font-semibold py-2 px-4 rounded shadow-sm transition-colors text-sm"
        aria-label="Call Vinto"
      >
        🏆 Call Vinto
      </button>
    </div>
  );
};

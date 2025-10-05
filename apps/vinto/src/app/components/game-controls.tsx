// components/GameControls.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { HelpPopover } from './help-popover';
import {
  useGameStore,
  usePlayerStore,
  useGamePhaseStore,
  useDeckStore,
  useTossInStore,
} from './di-provider';

export const GameControls = observer(() => {
  const gameStore = useGameStore();
  const playerStore = usePlayerStore();
  const { currentPlayer } = playerStore;
  const {
    isSelectingSwapPosition,
    isAwaitingActionTarget,
    isTossQueueProcessing,
    phase,
    isChoosingCardAction,
    isDeclaringRank,
    finalTurnTriggered,
  } = useGamePhaseStore();
  const tossInStore = useTossInStore();
  const { waitingForTossIn } = tossInStore;

  const handleDrawCard = () => {
    if (currentPlayer && currentPlayer.isHuman) {
      gameStore.drawCard();
    }
  };

  // Determine what content to show but always use same container
  const getControlContent = () => {
    // Hide controls during special game states
    // Note: isChoosingCardAction is now handled by GamePhaseIndicators (CardDrawnIndicator)
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

  const getHelpContent = () => {
    if (controlContent.type === 'full-controls') {
      return `🎯 Draw New: Draw a new card from the deck

♻️ Play Card: Take an unplayed action card from the discard pile (7-K only, not on first turn)

🏆 Call Vinto: End the game and start final scoring when you think you have the lowest hand`;
    }
    if (controlContent.type === 'vinto-only') {
      return `🏆 Call Vinto: You must call Vinto before the next player's turn to end the game and start final scoring`;
    }
    return '';
  };

  // Single consistent container for all states
  return (
    <div className="w-full h-full px-2 py-1.5">
      <div className="h-full bg-white/98 backdrop-blur-sm supports-[backdrop-filter]:bg-white/95 border border-gray-300 rounded-lg p-2 shadow-sm flex flex-col overflow-hidden">
        {/* Header - consistent across all states */}
        <div className="flex items-center justify-between mb-1 flex-shrink-0">
          <h3 className="text-xs md:text-sm font-semibold text-gray-800 leading-tight">
            {controlContent.title}
          </h3>
          <HelpPopover title="Game Controls" content={getHelpContent()} />
        </div>

        {/* Main content area - responsive to content type */}
        <div className="flex flex-col justify-center flex-1 min-h-0">
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
const VintoOnlyControls = () => {
  const gameStore = useGameStore();
  return (
    <div className="space-y-1">
      <button
        onClick={() => gameStore.callVinto()}
        className="w-full bg-amber-600 hover:bg-amber-700 active:bg-amber-800 text-white font-semibold py-1.5 px-3 rounded shadow-sm transition-colors text-xs min-h-[36px]"
        aria-label="Call Vinto"
      >
        🏆 Call Vinto
      </button>
    </div>
  );
};

const FullTurnControls = ({
  handleDrawCard,
}: {
  handleDrawCard: () => void;
}) => {
  const gameStore = useGameStore();
  const { discardPile, drawPile } = useDeckStore();

  return (
    <div className="space-y-1">
      {/* Mobile: Stack vertically, Desktop: 2-column grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-1">
        {/* Draw from Deck */}
        <button
          onClick={handleDrawCard}
          disabled={drawPile.length === 0}
          className="flex flex-row items-center justify-center gap-1 bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800 disabled:bg-gray-400 disabled:cursor-not-allowed text-white font-semibold py-1.5 px-2 rounded shadow-sm transition-colors text-xs min-h-[36px]"
          aria-label="Draw new card from deck"
        >
          <span>🎯</span>
          <span>Draw New</span>
        </button>

        {/* Take from Discard */}
        <button
          onClick={() => gameStore.takeFromDiscard()}
          disabled={!discardPile[0]?.action || discardPile[0]?.played}
          className="flex flex-row items-center justify-center gap-1 bg-poker-green-600 hover:bg-poker-green-700 active:bg-poker-green-800 disabled:bg-gray-400 disabled:cursor-not-allowed text-white font-semibold py-1.5 px-2 rounded shadow-sm transition-colors text-xs min-h-[36px]"
          aria-label="Take unplayed card from discard pile"
        >
          <span>♻️</span>
          <span>Play Card</span>
        </button>
      </div>

      {/* Call Vinto - always available during turn */}
      <button
        onClick={() => gameStore.callVinto()}
        className="w-full bg-amber-600 hover:bg-amber-700 active:bg-amber-800 text-white font-semibold py-1.5 px-3 rounded shadow-sm transition-colors text-xs min-h-[36px]"
        aria-label="Call Vinto"
      >
        🏆 Call Vinto
      </button>
    </div>
  );
};

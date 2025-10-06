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
import { DrawCardButton, UseActionButton } from './ui/button';

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
    // Note: waitingForTossIn is now handled by GamePhaseIndicators (TossInIndicator)
    const shouldHide =
      (phase !== 'playing' && phase !== 'final') ||
      isSelectingSwapPosition ||
      isChoosingCardAction ||
      isDeclaringRank ||
      finalTurnTriggered ||
      isAwaitingActionTarget ||
      isTossQueueProcessing ||
      waitingForTossIn ||
      currentPlayer?.isBot;

    if (shouldHide) {
      return { type: 'hidden' };
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
    return `🎯 Draw New: Draw a new card from the deck

♻️ Play Card: Take an unplayed action card from the discard pile (7-K only, not on first turn)

Note: Call Vinto option will be available after you complete your turn during the toss-in phase`;
  };

  // Single consistent container for all states
  return (
    <div className="w-full h-full px-2 py-1.5">
      <div className="h-full bg-white/98 backdrop-blur-sm supports-[backdrop-filter]:bg-white/95 border border-gray-300 rounded-lg p-2 shadow-sm flex flex-col">
        {/* Header - consistent across all states */}
        <div className="flex items-center justify-between mb-1 flex-shrink-0">
          <h3 className="text-xs md:text-sm font-semibold text-gray-800 leading-tight">
            {controlContent.title}
          </h3>
          <HelpPopover title="Game Controls" content={getHelpContent()} />
        </div>

        {/* Main content area - responsive to content type */}
        <div className="flex flex-col justify-center flex-1 min-h-0">
          <FullTurnControls handleDrawCard={handleDrawCard} />
        </div>
      </div>
    </div>
  );
});

const FullTurnControls = ({
  handleDrawCard,
}: {
  handleDrawCard: () => void;
}) => {
  const gameStore = useGameStore();
  const { discardPile, drawPile } = useDeckStore();

  const topDiscard = discardPile[0];
  const canTakeDiscard = topDiscard?.action && !topDiscard?.played;
  const deckEmpty = drawPile.length === 0;

  // Get discard button info
  const getDiscardButtonInfo = () => {
    if (!topDiscard) {
      return {
        text: 'Use Discard',
        subtitle: 'Pile empty',
        tooltip: 'No cards in discard pile',
      };
    }
    if (topDiscard.played) {
      return {
        text: `Use ${topDiscard.rank}`,
        subtitle: 'Already used',
        tooltip: `${topDiscard.rank} action has already been played`,
      };
    }
    if (!topDiscard.action) {
      return {
        text: `Use ${topDiscard.rank}`,
        subtitle: 'No action',
        tooltip: `${topDiscard.rank} has no special action`,
      };
    }
    return {
      text: `Use ${topDiscard.rank}`,
      subtitle: null,
      tooltip: `Use ${topDiscard.rank} card action from discard pile`,
    };
  };

  const discardInfo = getDiscardButtonInfo();

  return (
    <div className="space-y-1">
      {/* Mobile: Stack vertically, Desktop: 2-column grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-1">
        {/* Draw from Deck */}
        <DrawCardButton onClick={handleDrawCard} disabled={deckEmpty} />

        {/* Take from Discard */}
        <button
          onClick={() => gameStore.takeFromDiscard()}
          disabled={!canTakeDiscard}
          title={discardInfo.tooltip}
          className={`${
            canTakeDiscard
              ? 'bg-[#2ECC71] hover:bg-[#27AE60] active:bg-[#229954] text-white'
              : 'bg-gray-300 text-gray-600 cursor-not-allowed'
          } font-semibold rounded shadow-sm transition-colors flex flex-col items-center justify-center py-1.5 px-2 text-xs min-h-[36px]`}
        >
          <div className="flex items-center gap-1">
            <span>⚡</span>
            <span>{discardInfo.text}</span>
          </div>
          {!canTakeDiscard && discardInfo.subtitle && (
            <div className="text-[10px] opacity-75 mt-0.5">
              {discardInfo.subtitle}
            </div>
          )}
        </button>
      </div>
    </div>
  );
};

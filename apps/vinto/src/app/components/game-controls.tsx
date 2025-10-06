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
import { DrawCardButton, UseActionButton, CallVintoButton } from './ui/button';

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
      finalTurnTriggered ||
      isAwaitingActionTarget ||
      isTossQueueProcessing ||
      currentPlayer?.isBot;

    if (shouldHide) {
      return { type: 'hidden' };
    }

    // Show vinto-only controls during toss-in phase after human turn
    // or when business logic specifically allows it
    const showVintoOnly =
      waitingForTossIn || gameStore.canCallVintoAfterHumanTurn;

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

Note: Call Vinto option will be available after you complete your turn during the toss-in phase`;
    }
    if (controlContent.type === 'vinto-only') {
      return `🏆 Call Vinto: Call Vinto before the next player's turn to end the game and start final scoring

You can also toss in matching cards or click Continue to proceed to the next player's turn`;
    }
    return '';
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
  const gamePhaseStore = useGamePhaseStore();
  return (
    <div className="space-y-1">
      <CallVintoButton
        onClick={() => gamePhaseStore.openVintoConfirmation()}
        fullWidth
        className="py-1.5 px-3"
      />
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

  const topDiscard = discardPile[0];
  const canTakeDiscard = topDiscard?.action && !topDiscard?.played;
  const deckEmpty = drawPile.length === 0;

  return (
    <div className="space-y-1">
      {/* Mobile: Stack vertically, Desktop: 2-column grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-1">
        {/* Draw from Deck */}
        <DrawCardButton onClick={handleDrawCard} disabled={deckEmpty} />

        {/* Take from Discard */}
        <UseActionButton
          onClick={() => gameStore.takeFromDiscard()}
          disabled={!canTakeDiscard}
        >
          {topDiscard?.rank ? `Use ${topDiscard.rank}` : 'Use Discard'}
        </UseActionButton>
      </div>
    </div>
  );
};

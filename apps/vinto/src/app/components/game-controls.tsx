// components/GameControls.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { gameStore } from '../stores/game-store';
import { getPlayerStore } from '../stores/player-store';
import { getActionStore } from '../stores/action-store';
import { getGamePhaseStore } from '../stores/game-phase-store';
import { getDeckStore } from '../stores/deck-store';
import { getTossInStore } from '../stores/toss-in-store';

export const GameControls = observer(() => {
  const playerStore = getPlayerStore();
  const currentPlayer = playerStore.players[playerStore.currentPlayerIndex];

  const handleDrawCard = () => {
    if (currentPlayer && currentPlayer.isHuman) {
      gameStore.drawCard();
    }
  };

  // Determine what content to show but always use same container
  const getControlContent = () => {
    // Show peek confirmation controls when human has peeked and needs to confirm
    const { players, humanPlayer } = getPlayerStore();
    const {
      isSelectingSwapPosition,
      isAwaitingActionTarget,
      isProcessingTossInQueue,
      phase,
      isChoosingCardAction,
      isDeclaringRank,
      finalTurnTriggered
    } = getGamePhaseStore();
    const { actionContext, tossInQueue } = getActionStore();
    const {waitingForTossIn} = getTossInStore();
    const isPeekConfirmation =
      actionContext?.playerId === humanPlayer?.id &&
      (actionContext?.targetType === 'own-card' ||
        actionContext?.targetType === 'opponent-card') &&
      isAwaitingActionTarget;

    if (isPeekConfirmation) {
      const hasRevealedCard =
        humanPlayer && humanPlayer.temporarilyVisibleCards.size > 0;

      return {
        type: 'peek-confirm',
        title: hasRevealedCard ? 'Card Revealed' : 'Select a card',
        subtitle: '',
      };
    }

    // Show toss-in skip controls when processing toss-in queue for human
    const isHumanTossInAction =
      isProcessingTossInQueue &&
      actionContext?.playerId === players.find((p) => p.isHuman)?.id;

    if (isHumanTossInAction) {
      return {
        type: 'toss-in',
        title: `Toss-in Action: ${tossInQueue[0]?.card.rank} (${tossInQueue[0]?.card.action})`,
        subtitle: 'You can execute this action or skip it',
      };
    }

    // Hide controls during special game states (but allow peek confirmation through)
    const shouldHide =
      (phase !== 'playing' && phase !== 'final') ||
      isSelectingSwapPosition ||
      isChoosingCardAction ||
      isDeclaringRank ||
      waitingForTossIn ||
      finalTurnTriggered ||
      (isAwaitingActionTarget && !isPeekConfirmation) ||
      isProcessingTossInQueue ||
      !currentPlayer?.isHuman;

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
          {controlContent.type === 'peek-confirm' && <PeekConfirmControls />}

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
const PeekConfirmControls = () => {
  const { humanPlayer } = getPlayerStore();
  const hasRevealedCard =
    humanPlayer && humanPlayer.temporarilyVisibleCards.size > 0;
  const actionStore = getActionStore();
  const actionContext = actionStore.actionContext;

  // Determine instruction message based on action type
  const getInstructionMessage = () => {
    if (!actionContext) return 'Select a card to peek';

    if (actionContext.targetType === 'own-card') {
      return 'Peek 1 of your cards';
    } else if (actionContext.targetType === 'opponent-card') {
      return 'Peek 1 opponent card';
    }
    return 'Select a card to peek';
  };

  return (
    <button
      onClick={() => hasRevealedCard && gameStore.confirmPeekCompletion()}
      disabled={!hasRevealedCard}
      className={`w-full font-semibold py-2 px-4 rounded shadow-sm transition-colors text-sm ${
        hasRevealedCard
          ? 'bg-blue-600 hover:bg-blue-700 text-white'
          : 'bg-gray-200 text-gray-600 cursor-not-allowed'
      }`}
      aria-label={
        hasRevealedCard
          ? 'Continue after peeking'
          : 'Waiting for card selection'
      }
    >
      {hasRevealedCard ? 'Continue' : getInstructionMessage()}
    </button>
  );
};

const TossInControls = () => (
  <div className="space-y-2">
    <div className="text-sm text-gray-600 text-center">
      Execute the action or skip to continue
    </div>
    <button
      onClick={() => gameStore.skipCurrentTossInAction()}
      className="w-full bg-slate-600 hover:bg-slate-700 text-white font-semibold py-2 px-4 rounded shadow-sm transition-colors text-sm"
      aria-label="Skip toss-in action"
    >
      ⏭️ Skip Action
    </button>
  </div>
);

const VintoOnlyControls = () => (
  <div className="space-y-2">
    <button
      onClick={() => gameStore.callVinto()}
      className="w-full bg-red-600 hover:bg-red-700 text-white font-semibold py-2 px-4 rounded shadow-sm transition-colors text-sm"
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
          className="flex items-center justify-center gap-2 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed text-white font-semibold py-1.5 px-3 rounded shadow-sm transition-colors text-sm"
          aria-label="Draw new card from deck"
        >
          <span>🎯</span>
          <span>Draw New</span>
        </button>

        {/* Take from Discard */}
        <button
          onClick={() => gameStore.takeFromDiscard()}
          disabled={!canTakeFromDiscard}
          className="flex items-center justify-center gap-2 bg-emerald-600 hover:bg-emerald-700 disabled:bg-gray-400 disabled:cursor-not-allowed text-white font-semibold py-1.5 px-3 rounded shadow-sm transition-colors text-sm"
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
        className="w-full bg-red-600 hover:bg-red-700 text-white font-semibold py-2 px-4 rounded shadow-sm transition-colors text-sm"
        aria-label="Call Vinto"
      >
        🏆 Call Vinto
      </button>
    </div>
  );
};

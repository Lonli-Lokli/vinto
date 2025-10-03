// components/GamePhaseIndicators.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import type { Card, Player } from '../shapes';
import type { ActionStore } from '../stores/action-store';
import {
  useGameStore,
  useGamePhaseStore,
  usePlayerStore,
  useTossInStore,
  useDeckStore,
  useActionStore,
} from './di-provider';

// Setup Phase Component
const SetupPhaseIndicator = observer(({
  setupPeeksRemaining,
  onFinishSetup
}: {
  setupPeeksRemaining: number;
  onFinishSetup: () => void;
}) => (
  <div className="w-full px-3 py-1">
    <div className="bg-white border border-gray-300 rounded-lg p-3 shadow-sm">
      <div className="text-center space-y-2">
        <div className="text-sm font-semibold text-gray-800">
          🔍 Memory Phase
        </div>
        <div className="text-xs text-gray-700">
          Click any 2 of your cards to memorize them. They will be hidden during the game!
        </div>
        <div className="text-xs font-medium text-gray-600">
          Peeks remaining: {setupPeeksRemaining}
        </div>
        <button
          onClick={onFinishSetup}
          disabled={setupPeeksRemaining > 0}
          className={`py-1.5 px-3 rounded text-sm font-semibold text-white transition-colors ${
            setupPeeksRemaining > 0
              ? 'bg-gray-400 cursor-not-allowed'
              : 'bg-blue-500 hover:bg-blue-600 cursor-pointer'
          }`}
        >
          Start Game
        </button>
      </div>
    </div>
  </div>
));

SetupPhaseIndicator.displayName = 'SetupPhaseIndicator';

// Toss-in Period Component
const TossInIndicator = observer(({
  topDiscardRank,
  onContinue
}: {
  topDiscardRank: string;
  onContinue: () => void;
}) => (
  <div className="w-full px-3 py-1">
    <div className="bg-white border border-gray-300 rounded-lg p-2 sm:p-3 shadow-sm">
      <div className="flex flex-col sm:flex-row items-center justify-between gap-2">
        <div className="flex-1 text-center sm:text-left">
          <div className="text-sm font-semibold text-gray-800">
            ⚡ Toss-in Time!
          </div>
          <div className="text-xs text-gray-700">
            {topDiscardRank ? `Toss matching ${topDiscardRank} cards` : 'Toss matching cards'} or continue
          </div>
          <div className="text-xs text-gray-600">
            Wrong guess = penalty card
          </div>
        </div>
        <button
          onClick={onContinue}
          className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded-lg shadow-sm transition-colors text-sm whitespace-nowrap"
        >
          Continue ▶
        </button>
      </div>
    </div>
  </div>
));

TossInIndicator.displayName = 'TossInIndicator';

// Action Execution Indicator Component
const ActionExecutionIndicator = observer(({
  actionContext,
  currentPlayer,
  pendingCard,
  actionStore
}: {
  actionContext: any;
  currentPlayer: Player | null;
  pendingCard: Card | null;
  actionStore: ActionStore;
}) => {
  const actionPlayer = actionContext.playerId === currentPlayer?.id ? 'You' : currentPlayer?.name || 'Player';
  const isHuman = currentPlayer?.isHuman;

  const getActionInfo = () => {
    switch (actionContext.targetType) {
      case 'own-card':
        return {
          icon: '👁️',
          title: `${actionPlayer} ${isHuman ? 'are' : 'is'} peeking at own card`,
          description: isHuman ? 'Click one of your cards to peek at it' : 'Bot is selecting a card...',
        };
      case 'opponent-card':
        return {
          icon: '🔍',
          title: `${actionPlayer} ${isHuman ? 'are' : 'is'} peeking at opponent card`,
          description: isHuman ? "Click an opponent's card to peek at it" : 'Bot is selecting a target...',
        };
      case 'swap-cards':
        return {
          icon: '🔀',
          title: `${actionPlayer} ${isHuman ? 'are' : 'is'} swapping cards`,
          description: isHuman ? 'Click two cards to swap them (any player)' : 'Bot is selecting cards to swap...',
        };
      case 'peek-then-swap':
        return {
          icon: '👑',
          title: `${actionPlayer} ${isHuman ? 'are' : 'is'} using Queen action`,
          description: isHuman
            ? actionStore.peekTargets.length < 2
              ? 'Click two cards to peek at them'
              : 'Choose whether to swap the peeked cards'
            : 'Bot is making a decision...',
        };
      case 'force-draw':
        return {
          icon: '🎯',
          title: `${actionPlayer} ${isHuman ? 'are' : 'is'} forcing a player to draw`,
          description: isHuman ? 'Click an opponent to force them to draw a card' : 'Bot is selecting a target...',
        };
      case 'declare-action':
        return {
          icon: '👑',
          title: `${actionPlayer} ${isHuman ? 'are' : 'is'} declaring King action`,
          description: isHuman ? 'Choose which card action to use (7-10, J, Q, A)' : 'Bot is declaring...',
        };
      default:
        return {
          icon: '🎴',
          title: `${actionPlayer} ${isHuman ? 'are' : 'is'} performing an action`,
          description: actionContext.action || 'Action in progress...',
        };
    }
  };

  const actionInfo = getActionInfo();

  return (
    <div className="w-full px-3 py-1">
      <div className="bg-gradient-to-r from-purple-50 to-blue-50 border border-purple-200 rounded-lg p-2 sm:p-3 shadow-sm">
        <div className="text-center space-y-1">
          <div className="text-sm font-semibold text-purple-800">
            {actionInfo.icon} {actionInfo.title}
          </div>
          <div className="text-xs text-purple-700">
            {actionInfo.description}
          </div>
          {pendingCard && (
            <div className="text-xs font-medium text-purple-600 mt-1">
              Card: {pendingCard.rank} - {pendingCard.action}
            </div>
          )}
        </div>
      </div>
    </div>
  );
});

ActionExecutionIndicator.displayName = 'ActionExecutionIndicator';

// Helper function to get action explanation
const getActionExplanation = (rank: string): string => {
  const explanations: Record<string, string> = {
    '7': 'Peek at one of your own cards',
    '8': 'Peek at an opponent\'s card',
    '9': 'Peek at an opponent\'s card',
    '10': 'Swap any two cards (any players)',
    'J': 'Compare two cards - discard the higher one',
    'Q': 'Peek at two cards, optionally swap them',
    'K': 'Declare another card action (7-A)',
    'A': 'Force an opponent to draw a penalty card',
  };
  return explanations[rank] || 'Special card action';
};

// Card Drawn Indicator Component
const CardDrawnIndicator = observer(({
  pendingCard,
  onUseAction,
  onSwapDiscard,
  onDiscard
}: {
  pendingCard: Card;
  onUseAction: () => void;
  onSwapDiscard: () => void;
  onDiscard: () => void;
}) => {
  const hasAction = !!pendingCard.action;

  return (
    <div className="w-full px-3 py-2">
      <div className="bg-white/95 backdrop-blur-sm border border-gray-300 rounded-lg p-3 shadow-sm">
        {/* Header */}
        <div className="text-center mb-3">
          <div className="text-sm font-semibold text-gray-800 mb-1">
            🎴 Card Drawn: {pendingCard.rank}
          </div>
          {hasAction && (
            <>
              <div className="text-xs font-medium text-emerald-700 mb-1">
                Action: {pendingCard.action}
              </div>
              <div className="text-xs text-gray-600 bg-emerald-50 rounded px-2 py-1 inline-block">
                {getActionExplanation(pendingCard.rank)}
              </div>
            </>
          )}
        </div>

        {/* Action Buttons */}
        <div className="space-y-2">
          {hasAction && (
            <button
              onClick={onUseAction}
              className="w-full flex items-center justify-center gap-2 bg-emerald-600 hover:bg-emerald-700 text-white font-semibold py-2 px-4 rounded shadow-sm transition-colors text-sm"
              title={`Execute ${pendingCard.action}`}
            >
              <span>⚡</span>
              <span>Use Action</span>
            </button>
          )}

          <button
            onClick={onSwapDiscard}
            className="w-full flex items-center justify-center gap-2 bg-blue-600 hover:bg-blue-700 text-white font-semibold py-2 px-4 rounded shadow-sm transition-colors text-sm"
            title="Replace one of your cards with this drawn card"
          >
            <span>🔄</span>
            <span>Swap with Hand Card</span>
          </button>

          {hasAction && (
            <button
              onClick={onDiscard}
              className="w-full flex items-center justify-center gap-2 bg-slate-600 hover:bg-slate-700 text-white font-semibold py-2 px-4 rounded shadow-sm transition-colors text-sm"
              title="Discard without using action"
            >
              <span>🗑️</span>
              <span>Discard (No Action)</span>
            </button>
          )}
        </div>

        {/* Help Text */}
        <div className="text-xs text-gray-500 text-center mt-2 pt-2 border-t border-gray-200">
          {hasAction
            ? 'Use action, swap with your card, or discard without action'
            : 'Swap with one of your cards or discard'}
        </div>
      </div>
    </div>
  );
});

CardDrawnIndicator.displayName = 'CardDrawnIndicator';

// Swap Position Selector Component
const SwapPositionIndicator = observer(({
  onDiscard
}: {
  onDiscard: () => void;
}) => (
  <div className="w-full px-3 py-1">
    <div className="bg-white border border-gray-300 rounded-lg p-2 sm:p-3 shadow-sm">
      <div className="flex flex-col sm:flex-row items-center justify-between gap-2">
        <div className="flex-1 text-center sm:text-left">
          <div className="text-sm font-semibold text-gray-800">
            🔄 Select a card to replace
          </div>
          <div className="text-xs text-gray-700">
            Click on one of your cards to swap it with the drawn card
          </div>
        </div>
        <button
          onClick={onDiscard}
          className="px-4 py-2 bg-slate-600 hover:bg-slate-700 text-white font-semibold rounded shadow-sm transition-colors text-sm whitespace-nowrap"
        >
          Discard Instead
        </button>
      </div>
    </div>
  </div>
));

SwapPositionIndicator.displayName = 'SwapPositionIndicator';

// Main Component
export const GamePhaseIndicators = observer(() => {
  const gameStore = useGameStore();
  const { phase, isSelectingSwapPosition, isAwaitingActionTarget, isChoosingCardAction } = useGamePhaseStore();
  const { setupPeeksRemaining, currentPlayer } = usePlayerStore();
  const tossInStore = useTossInStore();
  const { waitingForTossIn } = tossInStore;
  const { discardPile } = useDeckStore();
  const actionStore = useActionStore();
  const { pendingCard, actionContext } = actionStore;

  // Setup Phase
  if (phase === 'setup' && gameStore.sessionActive) {
    return (
      <SetupPhaseIndicator
        setupPeeksRemaining={setupPeeksRemaining}
        onFinishSetup={() => gameStore.finishSetup()}
      />
    );
  }

  // Toss-in Period
  if (waitingForTossIn) {
    const topDiscardRank = discardPile.length > 0 ? discardPile[discardPile.length - 1].rank : '';
    return (
      <TossInIndicator
        topDiscardRank={topDiscardRank}
        onContinue={() => gameStore.finishTossInPeriod()}
      />
    );
  }

  // Action Execution
  if (isAwaitingActionTarget && actionContext) {
    return (
      <ActionExecutionIndicator
        actionContext={actionContext}
        currentPlayer={currentPlayer}
        pendingCard={pendingCard}
        actionStore={actionStore}
      />
    );
  }

  // Card Drawn - Choosing Action
  if (isChoosingCardAction && pendingCard) {
    return (
      <CardDrawnIndicator
        pendingCard={pendingCard}
        onUseAction={() => gameStore.choosePlayCard()}
        onSwapDiscard={() => gameStore.chooseSwap()}
        onDiscard={() => gameStore.discardCard()}
      />
    );
  }

  // Card Selection for Swap
  if (isSelectingSwapPosition) {
    return (
      <SwapPositionIndicator
        onDiscard={() => gameStore.discardCard()}
      />
    );
  }

  return null;
});

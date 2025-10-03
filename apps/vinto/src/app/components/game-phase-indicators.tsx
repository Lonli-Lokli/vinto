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

// Card Drawn Indicator Component
const CardDrawnIndicator = observer(({
  pendingCard,
  onUseAction,
  onSwapDiscard
}: {
  pendingCard: Card;
  onUseAction: () => void;
  onSwapDiscard: () => void;
}) => (
  <div className="w-full px-3 py-1">
    <div className="bg-gradient-to-r from-green-50 to-emerald-50 border border-green-200 rounded-lg p-2 sm:p-3 shadow-sm">
      <div className="flex flex-col sm:flex-row items-center justify-between gap-2">
        <div className="flex-1 text-center sm:text-left">
          <div className="text-sm font-semibold text-green-800">
            🎴 Card Drawn: {pendingCard.rank}
          </div>
          <div className="text-xs text-green-700">
            Action: {pendingCard.action}
          </div>
          <div className="text-xs text-green-600">
            Choose to use the action or swap/discard the card
          </div>
        </div>
        <div className="flex gap-2">
          <button
            onClick={onUseAction}
            className="px-3 py-1.5 bg-green-600 hover:bg-green-700 text-white font-semibold rounded shadow-sm transition-colors text-xs whitespace-nowrap"
          >
            Use Action
          </button>
          <button
            onClick={onSwapDiscard}
            className="px-3 py-1.5 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded shadow-sm transition-colors text-xs whitespace-nowrap"
          >
            Swap/Discard
          </button>
        </div>
      </div>
    </div>
  </div>
));

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
  if (isChoosingCardAction && pendingCard?.action) {
    return (
      <CardDrawnIndicator
        pendingCard={pendingCard}
        onUseAction={() => gameStore.choosePlayCard()}
        onSwapDiscard={() => gameStore.chooseSwap()}
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

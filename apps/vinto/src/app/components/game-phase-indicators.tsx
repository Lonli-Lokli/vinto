// components/GamePhaseIndicators.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { HelpPopover } from './help-popover';
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
import { Card as CardComponent } from './card';

// Setup Phase Component
const SetupPhaseIndicator = observer(
  ({
    setupPeeksRemaining,
    onFinishSetup,
  }: {
    setupPeeksRemaining: number;
    onFinishSetup: () => void;
  }) => (
    <div className="w-full h-full px-3 py-2">
      <div className="h-full bg-white border border-gray-300 rounded-lg p-3 shadow-sm flex flex-col justify-center">
        <div className="text-center space-y-2">
          <div className="text-sm font-semibold text-gray-800 leading-tight">
            🔍 Memory Phase
          </div>
          <div className="text-xs text-gray-700 leading-normal">
            Click any 2 of your cards to memorize them. They will be hidden
            during the game!
          </div>
          <div className="text-xs font-medium text-gray-600 leading-normal">
            Peeks remaining: {setupPeeksRemaining}
          </div>
          <button
            onClick={onFinishSetup}
            disabled={setupPeeksRemaining > 0}
            className={`py-1.5 px-3 rounded text-sm font-semibold text-white transition-colors min-h-[44px] ${
              setupPeeksRemaining > 0
                ? 'bg-gray-400 cursor-not-allowed'
                : 'bg-blue-500 hover:bg-blue-600 active:bg-blue-700 cursor-pointer'
            }`}
          >
            Start Game
          </button>
        </div>
      </div>
    </div>
  )
);

SetupPhaseIndicator.displayName = 'SetupPhaseIndicator';

// Toss-in Period Component
const TossInIndicator = observer(
  ({
    topDiscardRank,
    onContinue,
    currentPlayer,
    isCurrentPlayerWaiting,
  }: {
    topDiscardRank: string;
    onContinue: () => void;
    currentPlayer: Player | null;
    isCurrentPlayerWaiting: boolean;
  }) => (
    <div className="w-full h-full px-3 py-2">
      <div className="h-full bg-white border border-gray-300 rounded-lg p-3 shadow-sm flex flex-row items-center">
        <div className="flex flex-col sm:flex-row items-center justify-between gap-2 w-full">
          <div className="flex-1 text-center sm:text-left">
            <div className="flex flex-row items-center gap-2 justify-center sm:justify-start">
              <div className="text-sm font-semibold text-gray-800 leading-tight">
                ⚡ Toss-in Time!
              </div>
              {isCurrentPlayerWaiting && currentPlayer && (
                <div className="text-xs text-gray-600 flex flex-row items-center gap-1 leading-normal">
                  <span className="animate-spin">⏳</span>
                  <span>{currentPlayer.name}&apos;s turn</span>
                </div>
              )}
            </div>
            <div className="text-xs text-gray-700 leading-normal">
              {topDiscardRank
                ? `Toss matching ${topDiscardRank} cards`
                : 'Toss matching cards'}{' '}
              or continue
            </div>
            <div className="text-xs text-gray-600 leading-normal">
              Wrong guess = penalty card
            </div>
          </div>
          <button
            onClick={onContinue}
            className="px-4 py-2 bg-blue-600 hover:bg-blue-700 active:bg-blue-800 text-white font-semibold rounded shadow-sm transition-colors text-sm whitespace-nowrap min-h-[44px]"
          >
            Continue ▶
          </button>
        </div>
      </div>
    </div>
  )
);

TossInIndicator.displayName = 'TossInIndicator';

// Action Execution Indicator Component
const ActionExecutionIndicator = observer(
  ({
    actionContext,
    currentPlayer,
    pendingCard,
    actionStore,
  }: {
    actionContext: any;
    currentPlayer: Player | null;
    pendingCard: Card | null;
    actionStore: ActionStore;
  }) => {
    const actionPlayer =
      actionContext.playerId === currentPlayer?.id
        ? 'You'
        : currentPlayer?.name || 'Player';
    const isHuman = currentPlayer?.isHuman;

    const getActionInfo = () => {
      switch (actionContext.targetType) {
        case 'own-card':
          return {
            icon: '👁️',
            title: `${actionPlayer} ${
              isHuman ? 'are' : 'is'
            } peeking at own card`,
            description: isHuman
              ? 'Click one of your cards to peek at it'
              : 'Bot is selecting a card...',
          };
        case 'opponent-card':
          return {
            icon: '🔍',
            title: `${actionPlayer} ${
              isHuman ? 'are' : 'is'
            } peeking at opponent card`,
            description: isHuman
              ? "Click an opponent's card to peek at it"
              : 'Bot is selecting a target...',
          };
        case 'swap-cards':
          return {
            icon: '🔀',
            title: `${actionPlayer} ${isHuman ? 'are' : 'is'} swapping cards`,
            description: isHuman
              ? 'Click two cards to swap them (any player)'
              : 'Bot is selecting cards to swap...',
          };
        case 'peek-then-swap':
          return {
            icon: '👑',
            title: `${actionPlayer} ${
              isHuman ? 'are' : 'is'
            } using Queen action`,
            description: isHuman
              ? actionStore.peekTargets.length < 2
                ? 'Click two cards to peek at them'
                : 'Choose whether to swap the peeked cards'
              : 'Bot is making a decision...',
          };
        case 'force-draw':
          return {
            icon: '🎯',
            title: `${actionPlayer} ${
              isHuman ? 'are' : 'is'
            } forcing a player to draw`,
            description: isHuman
              ? 'Click an opponent to force them to draw a card'
              : 'Bot is selecting a target...',
          };
        case 'declare-action':
          return {
            icon: '👑',
            title: `${actionPlayer} ${
              isHuman ? 'are' : 'is'
            } declaring King action`,
            description: isHuman
              ? 'Choose which card action to use (7-10, J, Q, A)'
              : 'Bot is declaring...',
          };
        default:
          return {
            icon: '🎴',
            title: `${actionPlayer} ${
              isHuman ? 'are' : 'is'
            } performing an action`,
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
  }
);

ActionExecutionIndicator.displayName = 'ActionExecutionIndicator';

// Helper function to get action explanation
const getActionExplanation = (rank: string): string => {
  const { getCardLongDescription } = require('../constants/game-setup');
  return getCardLongDescription(rank) || 'Special card action';
};

// Card Drawn Indicator Component
const CardDrawnIndicator = observer(
  ({
    pendingCard,
    onUseAction,
    onSwapDiscard,
    onDiscard,
  }: {
    pendingCard: Card;
    onUseAction: () => void;
    onSwapDiscard: () => void;
    onDiscard: () => void;
  }) => {
    const hasAction = !!pendingCard.action;

    const getHelpContent = () => {
      let content = `⚡ Use Action: Execute the card's special ability immediately

🔄 Swap: Replace one of your cards with this drawn card

🗑️ Discard: Discard this card${hasAction ? ' without using its action' : ''}`;

      if (hasAction) {
        content += `\n\n${pendingCard.rank}: ${getActionExplanation(
          pendingCard.rank
        )}`;
      }
      return content;
    };

    return (
      <div className="w-full h-full px-3 py-2">
        <div className="h-full bg-white/98 backdrop-blur-sm supports-[backdrop-filter]:bg-white/95 border border-gray-300 rounded-lg shadow-sm flex flex-row">
          <div className="h-full flex flex-row gap-3 w-full p-2.5">
            {/* Card image - first column - fixed width for Safari compatibility */}
            <div className="flex-shrink-0" style={{ width: '80px', height: '100%' }}>
              <CardComponent card={pendingCard} revealed={true} size="auto" />
            </div>

            {/* Content - second column */}
            <div className="flex-1 min-w-0 flex flex-col">
              {/* Header with title, rank and help */}
              <div className="flex flex-row items-start justify-between mb-2">
                <div className="flex-1">
                  <div className="flex flex-row items-baseline gap-2">
                    <span className="text-sm font-semibold text-gray-800 leading-tight">
                      Card Drawn:
                    </span>
                    <span className="text-base font-bold text-gray-900 leading-tight">
                      {pendingCard.rank}
                    </span>
                  </div>
                  {hasAction ? (
                    <div className="text-xs text-emerald-700 mt-0.5 leading-normal">
                      {getActionExplanation(pendingCard.rank)}
                    </div>
                  ) : (
                    <div className="text-xs text-gray-500 mt-0.5 leading-normal">
                      No action available
                    </div>
                  )}
                </div>

                <HelpPopover title="Card Actions" content={getHelpContent()} />
              </div>

              {/* Action Buttons - 2 in first row, 1 in second */}
              <div className="space-y-1.5 mt-auto">
                <div className="grid grid-cols-2 gap-1.5">
                  {hasAction && (
                    <button
                      onClick={onUseAction}
                      className="flex flex-row items-center justify-center gap-1.5 bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800 text-white font-semibold py-2 px-3 rounded shadow-sm transition-colors text-sm min-h-[44px]"
                    >
                      <span>⚡</span>
                      <span>Use Action</span>
                    </button>
                  )}

                  <button
                    onClick={onSwapDiscard}
                    className={`flex flex-row items-center justify-center gap-1.5 bg-blue-600 hover:bg-blue-700 active:bg-blue-800 text-white font-semibold py-2 px-3 rounded shadow-sm transition-colors text-sm min-h-[44px] ${
                      !hasAction ? 'col-span-2' : ''
                    }`}
                  >
                    <span>🔄</span>
                    <span>Swap</span>
                  </button>
                </div>

                <button
                  onClick={onDiscard}
                  className="w-full flex flex-row items-center justify-center gap-1.5 bg-slate-600 hover:bg-slate-700 active:bg-slate-800 text-white font-semibold py-2 px-3 rounded shadow-sm transition-colors text-sm min-h-[44px]"
                >
                  <span>🗑️</span>
                  <span>Discard</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }
);

CardDrawnIndicator.displayName = 'CardDrawnIndicator';

// Swap Position Selector Component
const SwapPositionIndicator = observer(
  ({ onDiscard }: { onDiscard: () => void }) => (
    <div className="w-full px-3 py-1">
      <div className="bg-white border border-gray-300 rounded-lg p-2 sm:p-3 shadow-sm">
        <div className="flex flex-col sm:flex-row items-center justify-between gap-2">
          <div className="flex-1 text-center sm:text-left">
            <div className="text-sm font-semibold text-gray-800 leading-tight">
              🔄 Select a card to replace
            </div>
            <div className="text-xs text-gray-700 leading-normal">
              Click on one of your cards to swap it with the drawn card
            </div>
          </div>
          <button
            onClick={onDiscard}
            className="px-4 py-2 bg-slate-600 hover:bg-slate-700 active:bg-slate-800 text-white font-semibold rounded shadow-sm transition-colors text-sm whitespace-nowrap min-h-[44px]"
          >
            Discard Instead
          </button>
        </div>
      </div>
    </div>
  )
);

SwapPositionIndicator.displayName = 'SwapPositionIndicator';

// Main Component
export const GamePhaseIndicators = observer(() => {
  const gameStore = useGameStore();
  const {
    phase,
    isSelectingSwapPosition,
    isAwaitingActionTarget,
    isChoosingCardAction,
  } = useGamePhaseStore();
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
    const topDiscardRank =
      discardPile.length > 0 ? discardPile[discardPile.length - 1].rank : '';
    return (
      <TossInIndicator
        topDiscardRank={topDiscardRank}
        onContinue={() => gameStore.finishTossInPeriod()}
        currentPlayer={currentPlayer}
        isCurrentPlayerWaiting={gameStore.isCurrentPlayerWaiting}
      />
    );
  }

  // Action Execution - only show for bot players
  // Human players get detailed instructions in the ActionTargetSelector (bottom area)
  if (
    isAwaitingActionTarget &&
    actionContext &&
    currentPlayer &&
    !currentPlayer.isHuman
  ) {
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
    return <SwapPositionIndicator onDiscard={() => gameStore.discardCard()} />;
  }

  return null;
});

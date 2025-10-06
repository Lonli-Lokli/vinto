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
import {
  UseActionButton,
  SwapButton,
  DiscardButton,
  StartGameButton,
  ContinueButton,
  DiscardInsteadButton,
} from './ui/button';
import {
  getActionExplanation,
  getCardName,
  getCardValue,
} from '../utils/card-helper';

// Setup Phase Component
const SetupPhaseIndicator = observer(
  ({
    setupPeeksRemaining,
    onFinishSetup,
  }: {
    setupPeeksRemaining: number;
    onFinishSetup: () => void;
  }) => (
    <div className="w-full h-full px-2 py-1.5">
      <div className="h-full bg-white border border-gray-300 rounded-lg p-2 shadow-sm flex flex-col justify-center">
        <div className="text-center space-y-1">
          <div className="text-xs font-semibold text-gray-800 leading-tight">
            🔍 Memory Phase
          </div>
          <div className="text-xs text-gray-700 leading-tight">
            Click any 2 of your cards to memorize them. They will be hidden
            during the game!
          </div>
          <div className="text-xs font-medium text-gray-600 leading-tight">
            Peeks remaining: {setupPeeksRemaining}
          </div>
          <div className="flex justify-center">
            <StartGameButton
              onClick={onFinishSetup}
              disabled={setupPeeksRemaining > 0}
            />
          </div>
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
    <div className="w-full h-full px-2 py-1.5">
      <div className="h-full bg-white border border-gray-300 rounded-lg p-2 shadow-sm flex flex-row items-center">
        <div className="flex flex-col sm:flex-row items-center justify-between gap-2 w-full min-w-0">
          <div className="flex-1 text-center sm:text-left min-w-0">
            <div className="flex flex-row items-center gap-1 justify-center sm:justify-start">
              <div className="text-xs font-semibold text-gray-800 leading-tight">
                ⚡ Toss-in Time!
              </div>
              {isCurrentPlayerWaiting && currentPlayer && (
                <div className="text-xs text-gray-600 flex flex-row items-center gap-1 leading-tight">
                  <span className="animate-spin">⏳</span>
                  <span className="line-clamp-1">
                    {currentPlayer.name}&apos;s turn
                  </span>
                </div>
              )}
            </div>
            <div className="text-xs text-gray-700 leading-tight">
              {topDiscardRank
                ? `Toss matching ${topDiscardRank} cards`
                : 'Toss matching cards'}{' '}
              or continue
            </div>
            <div className="text-xs text-gray-600 leading-tight">
              Wrong guess = penalty card
            </div>
          </div>
          <ContinueButton onClick={onContinue} />
        </div>
      </div>
    </div>
  )
);

TossInIndicator.displayName = 'TossInIndicator';

// Utility function to get action information
const getActionInfo = (
  actionContext: any,
  actionPlayer: string,
  isHuman: boolean,
  peekTargetsLength: number
) => {
  switch (actionContext.targetType) {
    case 'own-card':
      return {
        icon: '👁️',
        title: `${actionPlayer} ${isHuman ? 'are' : 'is'} peeking at own card`,
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
        title: `${actionPlayer} ${isHuman ? 'are' : 'is'} using Queen action`,
        description: isHuman
          ? peekTargetsLength < 2
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
        title: `${actionPlayer} ${isHuman ? 'are' : 'is'} performing an action`,
        description: actionContext.action || 'Action in progress...',
      };
  }
};

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
    const isHuman = currentPlayer?.isHuman ?? false;

    const actionInfo = getActionInfo(
      actionContext,
      actionPlayer,
      isHuman,
      actionStore.peekTargets.length
    );

    return (
      <div className="w-full px-2 py-1">
        <div className="bg-gradient-to-r from-purple-50 to-blue-50 border border-purple-200 rounded-lg p-2 shadow-sm">
          <div className="text-center">
            <div className="text-xs font-semibold text-purple-800 leading-tight">
              {actionInfo.icon} {actionInfo.title}
            </div>
            <div className="text-xs text-purple-700 leading-tight mt-0.5">
              {actionInfo.description}
            </div>
          </div>
        </div>
      </div>
    );
  }
);

ActionExecutionIndicator.displayName = 'ActionExecutionIndicator';

// Card Drawn Header Component
const CardDrawnHeader = ({
  pendingCard,
  hasAction,
  getHelpContent,
}: {
  pendingCard: Card;
  hasAction: boolean;
  getHelpContent: () => string;
}) => (
  <div className="flex flex-row items-center justify-between mb-1.5 flex-shrink-0">
    <div className="flex-1 min-w-0">
      {/* Compact single line with all info */}
      <div className="flex flex-row items-baseline gap-1 flex-wrap">
        <span className="text-sm font-bold text-gray-900 leading-tight">
          {getCardName(pendingCard.rank)}
        </span>
        <span className="text-xs text-gray-600 leading-tight">
          {getCardValue(pendingCard.rank)}{' '}
          {Math.abs(getCardValue(pendingCard.rank)) === 1
            ? ' point'
            : ' points'}
        </span>
        {hasAction && (
          <span className="text-xs text-emerald-700 leading-tight">
            • {getActionExplanation(pendingCard.rank)}
          </span>
        )}
      </div>
    </div>

    <HelpPopover title="Card Actions" content={getHelpContent()} />
  </div>
);

// Card Action Buttons Component
const CardActionButtons = ({
  hasAction,
  onUseAction,
  onSwapDiscard,
  onDiscard,
}: {
  hasAction: boolean;
  onUseAction: () => void;
  onSwapDiscard: () => void;
  onDiscard: () => void;
}) => (
  <div className="space-y-1 mt-auto flex-shrink-0">
    {/* Row 1: Use and Swap (or just Swap and Discard if no action) */}
    <div className="grid grid-cols-2 gap-1">
      {hasAction ? (
        <>
          <UseActionButton onClick={onUseAction} />
          <SwapButton onClick={onSwapDiscard} />
        </>
      ) : (
        <>
          <SwapButton onClick={onSwapDiscard} />
          <DiscardButton onClick={onDiscard} />
        </>
      )}
    </div>

    {/* Row 2: Discard (only when Use is available) */}
    {hasAction && <DiscardButton onClick={onDiscard} fullWidth />}
  </div>
);

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
      <div className="w-full h-full px-2 py-1.5">
        <div className="h-full bg-white/98 backdrop-blur-sm supports-[backdrop-filter]:bg-white/95 border border-gray-300 rounded-lg shadow-sm flex flex-row">
          <div className="h-full flex flex-row gap-2 w-full p-2">
            {/* Card image - takes full height, preserves aspect ratio */}
            <div className="flex-shrink-0 h-full flex items-stretch">
              <div className="h-full" style={{ aspectRatio: '2.5 / 3.5' }}>
                <CardComponent card={pendingCard} revealed={true} size="auto" />
              </div>
            </div>

            {/* Content - second column */}
            <div className="flex-1 min-w-0 flex flex-col">
              <CardDrawnHeader
                pendingCard={pendingCard}
                hasAction={hasAction}
                getHelpContent={getHelpContent}
              />

              <CardActionButtons
                hasAction={hasAction}
                onUseAction={onUseAction}
                onSwapDiscard={onSwapDiscard}
                onDiscard={onDiscard}
              />
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
    <div className="w-full px-2 py-1">
      <div className="bg-white border border-gray-300 rounded-lg p-2 shadow-sm flex flex-row items-center gap-2">
        <div className="flex-1 text-center sm:text-left min-w-0">
          <div className="text-xs font-semibold text-gray-800 leading-tight">
            🔄 Click your card to swap
          </div>
        </div>
        <DiscardInsteadButton onClick={onDiscard} />
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

  // Card Drawn - Choosing Action (only for human players)
  if (isChoosingCardAction && pendingCard && currentPlayer?.isHuman) {
    return (
      <CardDrawnIndicator
        pendingCard={pendingCard}
        onUseAction={() => gameStore.choosePlayCard()}
        onSwapDiscard={() => gameStore.chooseSwap()}
        onDiscard={() => gameStore.discardCard()}
      />
    );
  }

  // Card Selection for Swap (only for human players)
  if (isSelectingSwapPosition && currentPlayer?.isHuman) {
    return <SwapPositionIndicator onDiscard={() => gameStore.discardCard()} />;
  }

  return null;
});

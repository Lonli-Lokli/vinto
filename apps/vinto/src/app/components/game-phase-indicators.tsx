// components/GamePhaseIndicators.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import {
  Eye,
  Search,
  Repeat,
  Crown,
  Target,
  Sparkles,
  Zap,
  Hourglass,
  CircleArrowRight,
} from 'lucide-react';
import { HelpPopover } from './presentational';
import { useUIStore } from './di-provider';
import { Card as CardComponent } from './presentational';
import {
  UseActionButton,
  SwapButton,
  DiscardButton,
  StartGameButton,
  ContinueButton,
  DiscardInsteadButton,
  CallVintoButton,
} from './buttons';
import {
  getCardName,
  getCardValue,
  getCardLongDescription as getActionExplanation,
  Rank,
  Card,
} from '@/shared';
import { ReactJoin } from '../utils/react-join';
import { useGameClient } from '@/client';
import { GameActions } from '@/engine';

// Main Component
export const GamePhaseIndicators = observer(() => {
  const gameClient = useGameClient();
  const uiStore = useUIStore();

  // Read all state from GameClient
  const phase = gameClient.state.phase;
  const subPhase = gameClient.state.subPhase;
  const humanPlayer = gameClient.state.players.find((p) => p.isHuman);
  const currentPlayer = gameClient.currentPlayer;
  const sessionActive = gameClient.state.phase !== 'final';
  const pendingCard = gameClient.state.pendingAction?.card;

  // Map subPhases to UI boolean flags
  const isSelectingSwapPosition = uiStore.isSelectingSwapPosition;
  const isChoosingCardAction = subPhase === 'choosing';
  const waitingForTossIn = subPhase === 'toss_queue_active';

  // Setup peeks remaining (count cards not in knownCardPositions)
  const setupPeeksRemaining = humanPlayer
    ? 2 - humanPlayer.knownCardPositions.length
    : 0;

  // Setup Phase
  if (phase === 'setup' && sessionActive) {
    return (
      <SetupPhaseIndicator
        setupPeeksRemaining={setupPeeksRemaining}
        onFinishSetup={() => {
          if (!humanPlayer) return;
          gameClient.dispatch(GameActions.finishSetup(humanPlayer.id));
        }}
      />
    );
  }

  // Toss-in Period
  // Note: Vinto caller should never see toss-in indicator, only waiting indicator
  if (waitingForTossIn && !humanPlayer?.isVintoCaller) {
    const topDiscardRank = gameClient.topDiscardCard?.rank;
    const isCurrentPlayerWaiting = gameClient.state.subPhase === 'ai_thinking';
    return (
      <TossInIndicator
        topDiscardRank={topDiscardRank}
        onContinue={() => {
          if (!humanPlayer) return;
          // Engine will check if all humans are ready and auto-advance turn
          gameClient.dispatch(GameActions.playerTossInFinished(humanPlayer.id));
        }}
        currentPlayer={currentPlayer}
        isCurrentPlayerWaiting={isCurrentPlayerWaiting}
      />
    );
  }

  // Note: Bot activity during awaiting_action is now shown in WaitingIndicator
  // Human players get detailed instructions in the ActionTargetSelector (bottom area)

  // Card Selection for Swap (only for human players)
  if (isSelectingSwapPosition && currentPlayer?.isHuman) {
    // If position is selected, show RankDeclaration
    if (uiStore.selectedSwapPosition !== null) {
      return null; // RankDeclaration component will render
    }

    // Otherwise show swap position selector
    return (
      <SwapPositionIndicator
        onDiscard={() => {
          if (!humanPlayer) return;
          uiStore.cancelSwapSelection();
          gameClient.dispatch(GameActions.discardCard(humanPlayer.id));
        }}
      />
    );
  }

  // Card Drawn - Choosing Action (only for human players)
  if (isChoosingCardAction && pendingCard && currentPlayer?.isHuman) {
    return (
      <CardDrawnIndicator
        pendingCard={pendingCard}
        onUseAction={() => {
          if (!humanPlayer || !pendingCard) return;
          gameClient.dispatch(
            GameActions.playCardAction(humanPlayer.id, pendingCard)
          );
        }}
        onSwapDiscard={() => {
          console.log('[CardDrawnIndicator] Swap button clicked');
          // Start swap selection (UI-only state)
          uiStore.startSelectingSwapPosition();
          console.log(
            '[CardDrawnIndicator] isSelectingSwapPosition set to:',
            uiStore.isSelectingSwapPosition
          );
        }}
        onDiscard={() => {
          if (!humanPlayer) return;
          gameClient.dispatch(GameActions.discardCard(humanPlayer.id));
        }}
      />
    );
  }

  return null;
});

// Setup Phase Component
const SetupPhaseIndicator = observer(
  ({
    setupPeeksRemaining,
    onFinishSetup,
  }: {
    setupPeeksRemaining: number;
    onFinishSetup: () => void;
  }) => (
    <div className="w-full h-full">
      <div className="h-full bg-surface-primary border border-primary rounded-lg p-2 shadow-theme-sm flex flex-col justify-center">
        <div className="text-center space-y-1">
          <div className="text-xs font-semibold text-primary leading-tight flex items-center justify-center gap-1">
            <Eye size={14} />
            <span>Memory Phase</span>
          </div>
          <div className="text-xs text-secondary leading-tight">
            Click any 2 of your cards to memorize them. They will be hidden
            during the game!
          </div>
          <div className="text-xs font-medium text-tertiary leading-tight">
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
    topDiscardRank?: Rank;
    onContinue: () => void;
    currentPlayer: { name: string; isHuman: boolean } | null;
    isCurrentPlayerWaiting: boolean;
  }) => {
    const gameClient = useGameClient();
    const uiStore = useUIStore();

    // Get bot actions from the PREVIOUS player (who just finished their turn)
    // Since we're in toss-in phase, the previous player's actions are what we want to show
    const currentTurn = gameClient.state.turnCount;
    const recentBotActions = gameClient.state.recentActions
      .filter((action) => {
        // Only show actions from current turn
        if (action.turnNumber !== currentTurn) return false;
        // Only show actions from the player who just took their turn (previous player)
        // This is the player whose turn triggered this toss-in phase
        const previousPlayer =
          gameClient.state.players[gameClient.state.currentPlayerIndex];
        if (!previousPlayer) return false;
        // Only show if it's the previous player and they're a bot
        return action.playerId === previousPlayer.id && !previousPlayer.isHuman;
      })
      .map((action) => action.description);

    const getHelpContent = () => {
      return `⚡ Toss-in Phase: After a card is discarded, all players can toss in matching cards from their hand.

🎯 How it works:
• Click matching cards to toss them in
• Wrong card = penalty card draw
• Click Continue when done

🏆 Call Vinto:
A special action to end the game if you think you have the lowest score. Use carefully - if you don't have the lowest score, you get penalty points!

⏭️ Continue:
Skip toss-in and proceed to next player's turn`;
    };

    return (
      <div className="w-full h-full">
        <div className="backdrop-theme border border-primary rounded-lg p-2 shadow-theme-sm h-full flex flex-col">
          {/* Header */}
          <div className="flex flex-row items-center justify-between mb-1.5 flex-shrink-0">
            <div className="flex-1 min-w-0">
              <h3 className="text-xs font-semibold text-primary leading-tight flex items-center gap-1">
                <Zap size={14} />
                <span>Toss-in Time!</span>
                {isCurrentPlayerWaiting && currentPlayer && (
                  <span className="text-secondary ml-1 flex items-center gap-1">
                    <Hourglass size={12} className="animate-spin" />
                    {currentPlayer.name}
                  </span>
                )}
              </h3>
              <div className="text-xs text-secondary leading-tight">
                Toss matching{' '}
                {topDiscardRank ? (
                  <span className="text-sm font-bold text-primary leading-tight">
                    {getCardName(topDiscardRank)}
                  </span>
                ) : (
                  'cards'
                )}{' '}
                • Wrong = penalty
              </div>
            </div>
            <HelpPopover title="Toss-in Phase" content={getHelpContent()} />
          </div>

          {/* Bot Actions Section */}
          {recentBotActions.length > 0 && (
            <div className="mb-2 p-2 bg-info-light rounded border border-info flex-shrink-0">
              <div className="font-semibold text-secondary mb-1 text-xs">
                Recent Actions:
              </div>
              <div className="flex flex-wrap gap-1 items-center">
                <ReactJoin separator={<CircleArrowRight size={14} />}>
                  {recentBotActions.map((action, idx) => (
                    <span key={idx} className="text-secondary text-xs">
                      {action}
                    </span>
                  ))}
                </ReactJoin>
              </div>
            </div>
          )}

          {/* Main Actions */}

          <div className="flex-1 flex flex-col justify-center min-h-0 space-y-1.5">
            {/* Continue Button */}
            <ContinueButton onClick={onContinue} fullWidth />

            {currentPlayer && currentPlayer.isHuman && (
              <>
                {/* Divider */}
                <div className="flex items-center gap-2">
                  <div className="flex-1 border-t border-primary"></div>
                  <span className="text-xs text-secondary font-medium">or</span>
                  <div className="flex-1 border-t border-primary"></div>
                </div>

                {/* Call Vinto - Special Action */}
                <div className="space-y-1">
                  <CallVintoButton
                    onClick={() => uiStore.setShowVintoConfirmation(true)}
                    fullWidth
                    className="py-1.5 px-2"
                  />
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    );
  }
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
        icon: <Eye size={14} />,
        title: `${actionPlayer} ${isHuman ? 'are' : 'is'} peeking at own card`,
        description: isHuman
          ? 'Click one of your cards to peek at it'
          : 'Bot is selecting a card...',
      };
    case 'opponent-card':
      return {
        icon: <Search size={14} />,
        title: `${actionPlayer} ${
          isHuman ? 'are' : 'is'
        } peeking at opponent card`,
        description: isHuman
          ? "Click an opponent's card to peek at it"
          : 'Bot is selecting a target...',
      };
    case 'swap-cards':
      return {
        icon: <Repeat size={14} />,
        title: `${actionPlayer} ${isHuman ? 'are' : 'is'} swapping cards`,
        description: isHuman
          ? 'Click two cards to swap them (any player)'
          : 'Bot is selecting cards to swap...',
      };
    case 'peek-then-swap':
      return {
        icon: <Crown size={14} />,
        title: `${actionPlayer} ${isHuman ? 'are' : 'is'} using Queen action`,
        description: isHuman
          ? peekTargetsLength < 2
            ? 'Click two cards to peek at them'
            : 'Choose whether to swap the peeked cards'
          : 'Bot is making a decision...',
      };
    case 'force-draw':
      return {
        icon: <Target size={14} />,
        title: `${actionPlayer} ${
          isHuman ? 'are' : 'is'
        } forcing a player to draw`,
        description: isHuman
          ? 'Click an opponent to force them to draw a card'
          : 'Bot is selecting a target...',
      };
    case 'declare-action':
      return {
        icon: <Crown size={14} />,
        title: `${actionPlayer} ${
          isHuman ? 'are' : 'is'
        } declaring King action`,
        description: isHuman
          ? 'Choose which card action to use (7-10, J, Q, A)'
          : 'Bot is declaring...',
      };
    default:
      return {
        icon: <Sparkles size={14} />,
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
  }: {
    actionContext: any;
    currentPlayer: { id: string; name: string; isHuman: boolean } | null;
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
      actionContext?.targets?.length || 0
    );

    return (
      <div className="w-full px-2 py-1">
        <div className="bg-gradient-to-r from-purple-50 to-blue-50 border border-purple-200 rounded-lg p-2 shadow-sm">
          <div className="text-center">
            <div className="text-xs font-semibold text-purple-800 leading-tight flex items-center justify-center gap-1">
              {actionInfo.icon}
              <span>{actionInfo.title}</span>
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
        <span className="text-sm font-bold text-primary leading-tight">
          {getCardName(pendingCard.rank)}
        </span>
        <span className="text-xs text-secondary leading-tight">
          {getCardValue(pendingCard.rank)}{' '}
          {Math.abs(getCardValue(pendingCard.rank)) === 1
            ? ' point'
            : ' points'}
        </span>
        {hasAction && (
          <span className="text-xs text-success leading-tight">
            • {getActionExplanation(pendingCard.rank)}
          </span>
        )}
      </div>
    </div>

    <HelpPopover title="Card Actions" content={getHelpContent()} />
  </div>
);

// Card Action Buttons Component (for choosing phase - includes Swap option)
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

// Card Drawn Indicator Component (for choosing phase)
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
    const hasAction = !!pendingCard.actionText;

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
      <div className="w-full h-full">
        <div className="h-full bg-surface-primary/98 backdrop-blur-sm supports-[backdrop-filter]:bg-surface-primary/95 border border-primary rounded-lg shadow-sm flex flex-row">
          <div className="h-full flex flex-row gap-2 w-full p-2">
            {/* Card image - takes full height, preserves aspect ratio */}
            <div className="flex-shrink-0 h-full flex items-stretch">
              <div className="h-full" style={{ aspectRatio: '2.5 / 3.5' }}>
                <CardComponent
                  card={pendingCard}
                  revealed={true}
                  size="auto"
                  selectionState="default"
                />
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
  ({ onDiscard }: { onDiscard: () => void }) => {
    const getHelpContent = () => {
      return `🔄 Swap Card: Replace one of your cards with the drawn card

🎯 How it works:
• Click any of your cards to replace it
• The replaced card goes to the discard pile
• The drawn card takes its place in your hand

🗑️ Discard Instead:
• Discard the drawn card without swapping
• Keep all your current cards`;
    };

    return (
      <div className="w-full h-full">
        <div className="h-full bg-surface-primary border border-primary rounded-lg p-2 shadow-sm flex flex-col">
          {/* Header with help button */}
          <div className="flex flex-row items-center justify-between mb-1 flex-shrink-0">
            <div className="flex-1 min-w-0">
              <div className="text-xs font-semibold text-primary leading-tight flex items-center gap-1">
                <Repeat size={14} />
                <span>Click your card to swap</span>
              </div>
            </div>
            <HelpPopover title="Swap Card" content={getHelpContent()} />
          </div>

          {/* Action button */}
          <div className="flex items-center justify-center flex-1 min-h-0">
            <DiscardInsteadButton onClick={onDiscard} />
          </div>
        </div>
      </div>
    );
  }
);

SwapPositionIndicator.displayName = 'SwapPositionIndicator';

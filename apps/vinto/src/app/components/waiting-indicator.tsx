'use client';
import { observer } from 'mobx-react-lite';
import { useCardAnimationStore, useUIStore } from './di-provider';
import { useGameClient } from '@vinto/local-client';
import { PureCSSParallaxStars } from './presentational/parallax-stars';
import { NeverError, PendingAction, PlayerState, TargetType } from '@vinto/shapes';
import { Eye, Search, Repeat, Crown, Target, Sparkles } from 'lucide-react';

export const WaitingIndicator = observer(function WaitingIndicator() {
  const gameClient = useGameClient();
  const uiStore = useUIStore();
  const animationStore = useCardAnimationStore();
  const hasBlockingAnimations = animationStore.hasBlockingAnimations;

  // Get current player
  const currentPlayer = gameClient.currentPlayer;
  const isCurrentPlayerWaiting =
    !currentPlayer.isHuman && gameClient.visualState.phase === 'playing';

  // Check subPhases
  const isChoosingCardAction = gameClient.state.subPhase === 'choosing';
  const isSelectingSwapPosition = uiStore.isSelectingSwapPosition;
  const isAwaitingActionTarget =
    gameClient.state.subPhase === 'awaiting_action';
  const waitingForTossIn =
    gameClient.visualState.subPhase === 'toss_queue_active' ||
    gameClient.visualState.subPhase === 'toss_queue_processing';
  const aiThinking = gameClient.state.subPhase === 'ai_thinking';


  // Check if processing toss-in actions
  const isProcessingTossInAction =
    gameClient.visualState.activeTossIn &&
    gameClient.visualState.activeTossIn.queuedActions.length > 0;

  // Get human player to check if they're the Vinto caller
  const humanPlayer = gameClient.visualState.players.find((p) => p.isHuman);
  const isVintoCaller = humanPlayer?.isVintoCaller ?? false;

  const showEmptyAnimation = hasBlockingAnimations && !isCurrentPlayerWaiting;
  if (showEmptyAnimation) {
    return <PureCSSParallaxStars />;
  }
  // Don't show if toss-in is active (shown inline in TossInIndicator instead)
  // Exception: Show for Vinto caller during toss-in (they don't participate)
  // Also don't show when processing toss-in actions (action UI is shown instead)
  if (
    !isCurrentPlayerWaiting ||
    (waitingForTossIn && !isVintoCaller) ||
    isProcessingTossInAction
  )
    return null;

  // Determine what the bot is doing

  return (
    <div className="w-full h-full">
      <div className="h-full bg-surface-primary/95 backdrop-blur-sm border border-primary rounded-lg p-4 shadow-sm flex flex-col justify-center">
        <div className="text-center">
          <h3 className="text-sm md:text-base font-semibold text-primary mb-2">
            {currentPlayer?.name}&apos;s turn
          </h3>
          <BotActivity
            currentPlayer={currentPlayer}
            aiThinking={aiThinking}
            isChoosingCardAction={isChoosingCardAction}
            isSelectingSwapPosition={isSelectingSwapPosition}
            isAwaitingActionTarget={isAwaitingActionTarget}
            pendingAction={gameClient.state.pendingAction}
          />
        </div>
      </div>
    </div>
  );
});

const BotActivity = ({
  currentPlayer,
  aiThinking,
  isChoosingCardAction,
  isSelectingSwapPosition,
  isAwaitingActionTarget,
  pendingAction,
}: {
  currentPlayer: PlayerState | undefined;
  aiThinking: boolean;
  isChoosingCardAction: boolean;
  isSelectingSwapPosition: boolean;
  isAwaitingActionTarget: boolean;
  pendingAction: PendingAction | null;
}) => {
  if (!currentPlayer) return <span>Thinking...</span>;

  if (aiThinking) {
    return (
      <div className="flex items-center justify-center gap-2 text-xs md:text-sm text-secondary">
        <div className="animate-spin">⏳</div>
        <span>Deciding next move...</span>
      </div>
    );
  }

  if (isChoosingCardAction) {
    return (
      <div className="flex items-center justify-center gap-2 text-xs md:text-sm text-secondary">
        <div className="animate-spin">⏳</div>
        <span>Deciding whether to swap or play the card...</span>
      </div>
    );
  }

  if (isSelectingSwapPosition) {
    return (
      <div className="flex items-center justify-center gap-2 text-xs md:text-sm text-secondary">
        <div className="animate-spin">⏳</div>
        <span>Choosing which card to swap...</span>
      </div>
    );
  }

  if (isAwaitingActionTarget) {
    if (pendingAction?.card) {
      const actionInfo = getActionInfo(
        pendingAction.targetType,
        currentPlayer.name,
        currentPlayer.isHuman,
        pendingAction.targets?.length || 0
      );

      return (
        <div className="flex items-center justify-center gap-2 text-xs md:text-sm text-secondary">
          <div className="animate-spin">⏳</div>
          <span>{actionInfo.description}</span>
        </div>
      );
    }
    return (
      <div className="flex items-center justify-center gap-2 text-xs md:text-sm text-secondary">
        <div className="animate-spin">⏳</div>
        <span>Selecting target...</span>
      </div>
    );
  }

  return (
    <div className="flex items-center justify-center gap-2 text-xs md:text-sm text-secondary">
      <div className="animate-spin">⏳</div>
      <span>Making a move...</span>
    </div>
  );
};


const getActionInfo = (
  targetType: TargetType | undefined,
  actionPlayer: string,
  isHuman: boolean,
  peekTargetsLength: number,
) => {
  switch (targetType) {
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
    case undefined:
      return {
        icon: <Sparkles size={14} />,
        title: `${actionPlayer} ${isHuman ? 'are' : 'is'} taking an action`,
        description: isHuman
          ? 'Select an action to perform'
          : 'Bot is selecting an action...',
      }
    default:
      throw new NeverError(targetType);
      
  }
};
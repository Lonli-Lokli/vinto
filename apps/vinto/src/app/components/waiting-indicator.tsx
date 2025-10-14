'use client';
import { observer } from 'mobx-react-lite';
import { useUIStore } from './di-provider';
import { useGameClient } from '@/client';

export const WaitingIndicator = observer(function WaitingIndicator() {
  const gameClient = useGameClient();
  const uiStore = useUIStore();

  // Get current player
  const currentPlayer = gameClient.currentPlayer;
  const isCurrentPlayerWaiting =
    !currentPlayer.isHuman && gameClient.state.phase === 'playing';

  // Check subPhases
  const isChoosingCardAction = gameClient.state.subPhase === 'choosing';
  const isSelectingSwapPosition = uiStore.isSelectingSwapPosition;
  const isAwaitingActionTarget =
    gameClient.state.subPhase === 'awaiting_action';
  const isDeclaringRank = gameClient.state.subPhase === 'declaring_rank';
  const waitingForTossIn =
    gameClient.state.subPhase === 'toss_queue_active' ||
    gameClient.state.subPhase === 'toss_queue_processing';
  const aiThinking = gameClient.state.subPhase === 'ai_thinking';

  // Get action context (if any)
  const actionContext = gameClient.state.pendingAction
    ? {
        action: gameClient.state.pendingAction.card.rank,
        targetType: gameClient.state.pendingAction.targets
          ? 'awaiting-target'
          : undefined,
      }
    : null;

  // Don't show if toss-in is active (shown inline in TossInIndicator instead)
  if (!isCurrentPlayerWaiting || waitingForTossIn) return null;

  // Determine what the bot is doing
  const getBotActivity = () => {
    if (!currentPlayer) return 'Thinking...';

    if (isChoosingCardAction) {
      return 'Deciding whether to swap or play the card...';
    }

    if (isSelectingSwapPosition) {
      return 'Choosing which card to swap...';
    }

    if (isDeclaringRank) {
      return 'Declaring rank for the King action...';
    }

    if (isAwaitingActionTarget && actionContext) {
      return `Playing ${actionContext.action} - making selection...`;
    }

    if (aiThinking) {
      return 'Thinking...';
    }

    return 'Making a move...';
  };

  return (
    <div className="w-full h-full py-1">
      <div className="h-full bg-surface-primary/95 backdrop-blur-sm border border-primary rounded-lg p-4 shadow-sm flex flex-col justify-center">
        <div className="text-center">
          <h3 className="text-sm md:text-base font-semibold text-primary mb-2">
            {currentPlayer?.name}&apos;s turn
          </h3>
          <div className="flex items-center justify-center gap-2 text-xs md:text-sm text-secondary">
            <div className="animate-spin">⏳</div>
            <span>{getBotActivity()}</span>
          </div>
        </div>
      </div>
    </div>
  );
});

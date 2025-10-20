'use client';
import { observer } from 'mobx-react-lite';
import { useUIStore } from './di-provider';
import { useGameClient } from '@vinto/local-client';

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

  // Check if processing toss-in actions
  const isProcessingTossInAction =
    gameClient.state.activeTossIn &&
    gameClient.state.activeTossIn.queuedActions.length > 0;

  // Get human player to check if they're the Vinto caller
  const humanPlayer = gameClient.state.players.find((p) => p.isHuman);
  const isVintoCaller = humanPlayer?.isVintoCaller ?? false;

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
  const getBotActivity = () => {
    if (!currentPlayer) return 'Thinking...';

    if (aiThinking) {
      return 'Deciding next move...';
    }

    if (isChoosingCardAction) {
      return 'Deciding whether to swap or play the card...';
    }

    if (isSelectingSwapPosition) {
      return 'Choosing which card to swap...';
    }

    if (isDeclaringRank) {
      return 'Declaring rank for the King action...';
    }

    if (isAwaitingActionTarget) {
      const pendingAction = gameClient.state.pendingAction;
      if (pendingAction?.card) {
        const cardName = pendingAction.card.rank;
        const targetType = pendingAction.targetType;

        if (targetType === 'own-card') {
          return `Playing ${cardName} - selecting own card to peek...`;
        } else if (targetType === 'opponent-card') {
          return `Playing ${cardName} - selecting opponent card to peek...`;
        } else if (targetType === 'swap-cards') {
          return `Playing ${cardName} - selecting cards to swap...`;
        } else if (targetType === 'peek-then-swap') {
          return `Playing ${cardName} - peeking and deciding...`;
        } else if (targetType === 'force-draw') {
          return `Playing ${cardName} - selecting target player...`;
        } else if (targetType === 'declare-action') {
          return `Playing ${cardName} - declaring action...`;
        }
        return `Playing ${cardName} - selecting target...`;
      }
      return 'Selecting target...';
    }

    return 'Making a move...';
  };

  return (
    <div className="w-full h-full">
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

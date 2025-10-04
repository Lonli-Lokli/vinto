'use client';
import { observer } from 'mobx-react-lite';
import { useGameStore, usePlayerStore, useGamePhaseStore, useActionStore, useTossInStore } from './di-provider';

export const WaitingIndicator = observer(function WaitingIndicator() {
  const { isCurrentPlayerWaiting, aiThinking } = useGameStore();
  const { currentPlayer } = usePlayerStore();
  const { isChoosingCardAction, isSelectingSwapPosition, isAwaitingActionTarget, isDeclaringRank } = useGamePhaseStore();
  const { actionContext } = useActionStore();
  const { waitingForTossIn } = useTossInStore();

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
      switch (actionContext.targetType) {
        case 'own-card':
          return `Playing ${actionContext.action} - peeking at own card...`;
        case 'opponent-card':
          return `Playing ${actionContext.action} - peeking at opponent's card...`;
        case 'swap-cards':
          return `Playing ${actionContext.action} - selecting cards to swap...`;
        case 'peek-then-swap':
          return `Playing ${actionContext.action} - peeking and swapping...`;
        case 'force-draw':
          return `Playing ${actionContext.action} - choosing target...`;
        default:
          return `Playing ${actionContext.action}...`;
      }
    }

    if (aiThinking) {
      return 'Thinking...';
    }

    return 'Making a move...';
  };

  return (
    <div className="w-full h-full px-3 py-2">
      <div className="h-full bg-white/95 backdrop-blur-sm border border-gray-300 rounded-lg p-4 shadow-sm flex flex-col justify-center">
        <div className="text-center">
          <h3 className="text-sm md:text-base font-semibold text-gray-800 mb-2">
            {currentPlayer?.name}&apos;s turn
          </h3>
          <div className="flex items-center justify-center gap-2 text-xs md:text-sm text-gray-600">
            <div className="animate-spin">⏳</div>
            <span>{getBotActivity()}</span>
          </div>
        </div>
      </div>
    </div>
  );
});

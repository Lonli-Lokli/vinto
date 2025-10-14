// components/action-types/OpponentCardPeek.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { useUIStore } from '../di-provider';
import { ContinueButton, SkipButton } from '../buttons';
import { useGameClient } from '@/client';
import { GameActions } from '@/engine';
import { HelpPopover } from '../presentational';

export const OpponentCardPeek = observer(() => {
  const uiStore = useUIStore();
  const gameClient = useGameClient();
  const humanPlayer = gameClient.state.players.find((p) => p.isHuman);

  if (!gameClient.state.pendingAction) return null;
  const action = gameClient.state.pendingAction.card.rank;

  // Check if any player has temporarily visible cards (the peeked opponent card)
  const hasRevealedCard = gameClient.state.players.some(
    (p) => uiStore.getTemporarilyVisibleCards(p.id).size > 0
  );

  return (
    <div className="w-full h-full px-3 py-2">
      <div className="bg-surface-primary/95 backdrop-blur-sm border border-primary rounded-lg p-4 shadow-sm h-full flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-xs md:text-sm font-semibold text-primary">
            🔍 {action}
          </h3>
          <div className="flex items-center gap-2">
            {hasRevealedCard && (
              <div className="text-2xs md:text-xs text-success font-medium">
                ✓ Card Revealed
              </div>
            )}
            <HelpPopover title="Peek at Opponent Card" rank="9" />
          </div>
        </div>

        {/* Instructions or Confirmation */}
        <div className="flex-1 flex flex-col justify-center mb-2">
          {!hasRevealedCard ? (
            <p className="text-xs text-secondary text-center">
              Click on an opponent&apos;s card to peek at it
            </p>
          ) : (
            <p className="text-xs text-secondary text-center">
              You have peeked at opponent&apos;s card
            </p>
          )}
        </div>

        {/* Action Buttons */}
        {hasRevealedCard ? (
          <ContinueButton
            onClick={() => {
              if (!humanPlayer) return;
              gameClient.dispatch(GameActions.confirmPeek(humanPlayer.id));
            }}
            className="w-full py-2 px-4 text-sm"
          />
        ) : (
          <SkipButton
            onClick={() => {
              if (!humanPlayer) return;
              // Skip the peek action
              gameClient.dispatch(GameActions.confirmPeek(humanPlayer.id));
            }}
            className="w-full py-2 px-4 text-sm"
          >
            Skip
          </SkipButton>
        )}
      </div>
    </div>
  );
});

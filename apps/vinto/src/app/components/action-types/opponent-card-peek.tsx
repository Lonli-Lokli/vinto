// components/action-types/OpponentCardPeek.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { useUIStore } from '../di-provider';
import { ContinueButton, SkipButton } from '../buttons';
import { useGameClient } from '@vinto/local-client';
import { GameActions } from '@vinto/engine';
import { HelpPopover } from '../presentational';
import { getCardShortDescription, getCardName } from '@vinto/shapes';

export const OpponentCardPeek = observer(() => {
  const uiStore = useUIStore();
  const gameClient = useGameClient();
  const humanPlayer = gameClient.visualState.players.find((p) => p.isHuman);

  if (!gameClient.visualState.pendingAction) return null;
  const action = gameClient.visualState.pendingAction.card.rank;

  // Check if any player has temporarily visible cards (the peeked opponent card)
  const hasRevealedCard = gameClient.visualState.players.some(
    (p) => uiStore.getTemporarilyVisibleCards(p.id).size > 0
  );

  // Check if this is a toss-in action
  const isTossInAction =
    gameClient.visualState.activeTossIn &&
    gameClient.visualState.activeTossIn.queuedActions.length > 0;

  return (
    <div className="w-full h-full">
      <div className="bg-surface-primary/95 backdrop-blur-sm border border-primary rounded-lg p-4 shadow-sm h-full flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between mb-2">
          <div className="flex flex-col">
            <h3 className="text-xs md:text-sm font-semibold text-primary flex items-center">
              🔍 {getCardName(action)}
              {isTossInAction && (
                <span className="ml-2 text-[10px] text-accent-primary font-medium">
                  ⚡ Toss-in
                </span>
              )}
            </h3>
            <span className="text-[10px] text-secondary mt-0.5 ml-5">{getCardShortDescription(action)}</span>
          </div>
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

// components/action-types/OwnCardPeek.tsx
'use client';

import { useUIStore } from '../di-provider';
import React from 'react';
import { observer } from 'mobx-react-lite';
import { ContinueButton, SkipButton } from '../buttons';
import { useGameClient } from '@/client';
import { GameActions } from '@/engine';
import { HelpPopover } from '../presentational';

export const OwnCardPeek = observer(() => {
  const uiStore = useUIStore();
  const gameClient = useGameClient();
  const action = gameClient.state.pendingAction?.card.rank;
  const humanPlayerState = gameClient.state.players.find((p) => p.isHuman);

  const hasRevealedCard =
    humanPlayerState &&
    uiStore.getTemporarilyVisibleCards(humanPlayerState.id).size > 0;

  return (
    <div className="w-full h-full px-3 py-2">
      <div className="bg-surface-primary/95 backdrop-blur-sm border border-primary rounded-lg p-4 shadow-sm h-full flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-xs md:text-sm font-semibold text-primary">
            👁️ {action}
          </h3>
          <div className="flex items-center gap-2">
            {hasRevealedCard && (
              <div className="text-2xs md:text-xs text-success font-medium">
                ✓ Card Revealed
              </div>
            )}
            <HelpPopover title="Peek at Own Card" rank="7" />
          </div>
        </div>

        {/* Instructions or Confirmation */}
        <div className="flex-1 flex flex-col justify-center mb-2">
          {!hasRevealedCard ? (
            <p className="text-xs text-secondary text-center">
              Click on one of your cards to peek at it
            </p>
          ) : (
            <p className="text-xs text-secondary text-center">
              You have peeked at your card
            </p>
          )}
        </div>

        {/* Action Buttons */}
        {hasRevealedCard ? (
          <ContinueButton
            onClick={() => {
              if (!humanPlayerState) return;
              gameClient.dispatch(GameActions.confirmPeek(humanPlayerState.id));
            }}
            className="w-full py-2 px-4 text-sm"
          />
        ) : (
          <SkipButton
            onClick={() => {
              if (!humanPlayerState) return;
              gameClient.dispatch(GameActions.confirmPeek(humanPlayerState.id));
            }}
            className="w-full py-2 px-4 text-sm"
          />
        )}
      </div>
    </div>
  );
});

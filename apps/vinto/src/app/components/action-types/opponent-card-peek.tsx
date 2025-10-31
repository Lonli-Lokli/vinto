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
import { Card } from '../presentational/card';

export const OpponentCardPeek = observer(() => {
  const uiStore = useUIStore();
  const gameClient = useGameClient();
  const humanPlayer = gameClient.visualState.players.find((p) => p.isHuman);

  if (!gameClient.visualState.pendingAction) return null;
  const action = gameClient.visualState.pendingAction.card.rank;

  // Find the opponent whose card is revealed
  const opponent = gameClient.visualState.players.find(
    (p) => uiStore.getTemporarilyVisibleCards(p.id).size > 0
  );
  const cardId = opponent
    ? Array.from(uiStore.getTemporarilyVisibleCards(opponent.id))[0]
    : undefined;
  // Get the card rank if possible
  const cardRank =
    opponent && typeof cardId === 'number' && opponent.cards[cardId]
      ? opponent.cards[cardId].rank
      : undefined;

  // Check if this is a toss-in action
  const isTossInAction =
    gameClient.visualState.activeTossIn &&
    gameClient.visualState.activeTossIn.queuedActions.length > 0;

  return (
    <>
      {/* Header */}
      <div className="flex items-center justify-between mb-1 flex-shrink-0">
        <div className="flex flex-col">
          <h3 className="text-xs font-semibold text-primary flex items-center leading-tight">
            🔍 {getCardName(action)}
            {isTossInAction && (
              <span className="ml-2 text-2xs text-accent-primary font-medium">
                ⚡ Toss-in
              </span>
            )}
          </h3>
          <span className="text-2xs text-secondary mt-0.5 ml-5 leading-tight">
            {getCardShortDescription(action)}
          </span>
        </div>
        <div className="flex items-center gap-1">
          {opponent && (
            <div className="text-2xs text-success font-medium">
              ✓ Revealed
            </div>
          )}
          <HelpPopover title="Peek at Opponent Card" rank="9" />
        </div>
      </div>

      {/* Instructions or Confirmation */}
      <div className="flex items-center justify-center gap-3 mb-1 flex-1 min-h-0">
        {!opponent ? (
          <p className="text-xs text-secondary text-center">
            Click on an opponent&apos;s card to peek at it
          </p>
        ) : (
          <>
            {cardRank && (
              <Card
                rank={cardRank}
                revealed={true}
                isPeeked={true}
                size="md"
                selectionState="default"
              />
            )}
            <p className="text-xs text-secondary">
              You have peeked at {opponent.name}&apos;s card
            </p>
          </>
        )}
      </div>

      {/* Action Buttons */}
      {opponent ? (
        <ContinueButton
          onClick={() => {
            if (!humanPlayer) return;
            gameClient.dispatch(GameActions.confirmPeek(humanPlayer.id));
          }}
          className="w-full"
        />
      ) : (
        <SkipButton
          onClick={() => {
            if (!humanPlayer) return;
            // Skip the peek action
            gameClient.dispatch(GameActions.confirmPeek(humanPlayer.id));
          }}
          className="w-full"
        >
          Skip
        </SkipButton>
      )}
    </>
  );
});

// components/action-types/OwnCardPeek.tsx
'use client';

import { useUIStore } from '../di-provider';
import React from 'react';
import { observer } from 'mobx-react-lite';
import { ContinueButton, SkipButton } from '../buttons';
import { useGameClient } from '@vinto/local-client';
import { GameActions } from '@vinto/engine';
import { HelpPopover } from '../presentational';
import { getCardShortDescription, getCardName } from '@vinto/shapes';
import { Card } from '../presentational/card';

export const OwnCardPeek = observer(() => {
  const uiStore = useUIStore();
  const gameClient = useGameClient();
  if (!gameClient.visualState.pendingAction) return null;

  const action = gameClient.visualState.pendingAction.card.rank;
  const humanPlayerState = gameClient.visualState.players.find(
    (p) => p.isHuman
  );

  // Find the peeked card index for the human player
  const cardId = humanPlayerState
    ? Array.from(uiStore.getTemporarilyVisibleCards(humanPlayerState.id))[0]
    : undefined;
  const cardRank =
    humanPlayerState && typeof cardId === 'number' && humanPlayerState.cards[cardId]
      ? humanPlayerState.cards[cardId].rank
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
            👁️ {getCardName(action)}
            {isTossInAction && (
              <span className="ml-2 text-2xs text-accent-primary font-medium">
                ⚡ Toss-in
              </span>
            )}
          </h3>
          <span className="text-2xs text-secondary mt-0.5 ml-5 leading-tight">{getCardShortDescription(action)}</span>
        </div>
        <div className="flex items-center gap-1">
          {cardRank && (
            <div className="text-2xs text-success font-medium">
              ✓ Revealed
            </div>
          )}
          <HelpPopover title="Peek at Own Card" rank="7" />
        </div>
      </div>

      {/* Instructions or Confirmation */}
      <div className="flex items-center justify-center gap-3 mb-1 flex-1 min-h-0">
        {!cardRank ? (
          <p className="text-xs text-secondary text-center">
            Click on one of your cards to peek at it
          </p>
        ) : (
          <>
            <Card
              rank={cardRank}
              revealed={true}
              isPeeked={true}
              size="md"
              selectionState="default"
            />
            <p className="text-xs text-secondary">
              You have peeked at your card
            </p>
          </>
        )}
      </div>

      {/* Action Buttons */}
      {cardRank ? (
        <ContinueButton
          onClick={() => {
            if (!humanPlayerState) return;
            gameClient.dispatch(GameActions.confirmPeek(humanPlayerState.id));
          }}
          className="w-full"
        />
      ) : (
        <SkipButton
          onClick={() => {
            if (!humanPlayerState) return;
            gameClient.dispatch(GameActions.confirmPeek(humanPlayerState.id));
          }}
          className="w-full"
        />
      )}
    </>
  );
});

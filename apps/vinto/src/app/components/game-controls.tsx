// components/game-controls-new.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { HelpPopover } from './presentational';
import { DrawCardButton, PlayDiscardButton } from './buttons';
import { useDispatch, useGameClient } from '@/client';
import { GameActions } from '@/engine';
import { getCardName } from '@/shared';

export const GameControls = observer(() => {
  const gameClient = useGameClient();
  const dispatch = useDispatch();

  const currentPlayer = gameClient.currentPlayer;
  const isMyTurn = gameClient.isCurrentPlayerHuman;
  const subPhase = gameClient.state.subPhase;
  const phase = gameClient.state.phase;

  // Consolidated game state checks
  const shouldHideControls =
    (phase !== 'playing' && phase !== 'final') ||
    subPhase === 'choosing' || // Selecting swap position
    subPhase === 'selecting' || // Choosing card action
    subPhase === 'declaring_rank' || // Declaring king rank
    subPhase === 'awaiting_action' || // Awaiting action target
    subPhase === 'toss_queue_processing' || // Processing toss-in
    subPhase === 'toss_queue_active' || // Waiting for toss-in
    gameClient.state.finalTurnTriggered ||
    !isMyTurn;

  // Action handlers using dispatch
  const handleDrawCard = () => {
    if (isMyTurn) {
      dispatch(GameActions.drawCard(currentPlayer.id));
    }
  };

  const handlePlayDiscard = () => {
    if (isMyTurn) {
      dispatch(GameActions.playDiscard(currentPlayer.id));
    }
  };

  if (shouldHideControls) {
    return null;
  }

  const getHelpContent = () => {
    return `🎯 Draw New: Draw a new card from the deck

♻️ Play Card: Take an unplayed action card from the discard pile (7-K only, not on first turn)

Note: Call Vinto option will be available after you complete your turn during the toss-in phase`;
  };

  return (
    <div className="w-full h-full py-1">
      <div className="h-full bg-surface-primary/98 backdrop-blur-sm supports-[backdrop-filter]:bg-surface-primary/95 border border-primary rounded-lg p-2 shadow-sm flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between mb-1 flex-shrink-0">
          <h3 className="text-xs md:text-sm font-semibold text-primary leading-tight">
            Your turn
          </h3>
          <HelpPopover title="Game Controls" content={getHelpContent()} />
        </div>

        {/* Main controls */}
        <div className="flex flex-col justify-center flex-1 min-h-0">
          <FullTurnControls
            handleDrawCard={handleDrawCard}
            handleTakeDiscard={handlePlayDiscard}
          />
        </div>
      </div>
    </div>
  );
});

const FullTurnControls = observer(
  ({
    handleDrawCard,
    handleTakeDiscard,
  }: {
    handleDrawCard: () => void;
    handleTakeDiscard: () => void;
  }) => {
    const gameClient = useGameClient();

    // Simple computed values from GameClient
    const deckEmpty = gameClient.drawPileCount === 0;
    const topDiscard = gameClient.topDiscardCard;
    const canTakeDiscard = topDiscard?.actionText && !topDiscard?.played;

    // Get discard button info
    const getDiscardButtonInfo = () => {
      if (!topDiscard) {
        return {
          text: 'Use Discard',
          subtitle: 'Pile empty',
          tooltip: 'No cards in discard pile',
        };
      }
      if (topDiscard.played) {
        return {
          text: `Use ${getCardName(topDiscard.rank)}`,
          subtitle: 'Already used',
          tooltip: `${topDiscard.rank} action has already been played`,
        };
      }
      if (!topDiscard.actionText) {
        return {
          text: `Use ${getCardName(topDiscard.rank)}`,
          subtitle: 'No action',
          tooltip: `${topDiscard.rank} has no special action`,
        };
      }
      return {
        text: `Use ${getCardName(topDiscard.rank)}`,
        subtitle: topDiscard.actionText,
        tooltip: `Use ${topDiscard.rank} card action from discard pile`,
      };
    };

    const discardInfo = getDiscardButtonInfo();

    return (
      <div className="space-y-1">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-1">
          {/* Draw from Deck */}
          <DrawCardButton onClick={handleDrawCard} disabled={deckEmpty} />

          {/* Play from Discard */}
          <PlayDiscardButton
            onClick={handleTakeDiscard}
            disabled={!canTakeDiscard}
            title={discardInfo.tooltip}
            text={discardInfo.text}
            subtitle={discardInfo.subtitle}
          />
        </div>
      </div>
    );
  }
);

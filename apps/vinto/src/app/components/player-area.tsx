// components/PlayerArea.tsx - Refactored with logic separation
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { PlayerAvatar, PlayerCards } from './presentational';
import { useCardAnimationStore, useUIStore } from './di-provider';
import { PlayerState } from '@vinto/shapes';
import { useGameClient } from '@vinto/local-client';
import type { PlayerPosition } from './logic/player-area-logic';
import {
  getCardSizeForPlayer,
  shouldAvatarComeFirst,
} from './logic/player-area-logic';

interface PlayerAreaProps {
  player: PlayerState;
  position: PlayerPosition;
  isCurrentPlayer: boolean;
  isThinking: boolean;
  onCardClick?: (index: number) => void;
  gamePhase: 'setup' | 'playing' | 'final' | 'scoring';
  isSelectingSwapPosition?: boolean;
  swapPosition?: number | null;
  isSelectingActionTarget?: boolean;
}

export const PlayerArea = observer(function PlayerArea({
  player,
  position,
  isCurrentPlayer,
  onCardClick,
  gamePhase,
  isSelectingSwapPosition = false,
  swapPosition = null,
  isSelectingActionTarget = false,
}: PlayerAreaProps) {
  const gameClient = useGameClient();
  const uiStore = useUIStore();
  const animationStore = useCardAnimationStore();
  const landingCards = new Set(
    animationStore
      .getPlayerAnimations(player.id)
      .filter((a) => a.rank && a.type !== 'highlight')
      .map((a) => a.to.position)
  );

  const humanPlayer = gameClient.visualState.players.find((p) => p.isHuman);
  const coalitionLeader = gameClient.coalitionLeader;

  const temporarilyVisibleCards = uiStore.getTemporarilyVisibleCards(player.id);
  const highlightedCards = uiStore.getHighlightedCards(player.id);

  const isCoalitionLeader = coalitionLeader?.id === player.id;
  const isCoalitionMember = player.coalitionWith.length > 0;

  const cardSize = getCardSizeForPlayer(player.cards.length, player.isHuman);
  const avatarFirst = shouldAvatarComeFirst(position);

  // Get action targets for cards that have been selected (Q, K actions, etc.)
  const actionTargets = gameClient.visualState.pendingAction?.targets || [];

  // Get failed toss-in cards for this player
  const failedTossInCards = new Set<number>();
  player.cards.forEach((_, index) => {
    if (uiStore.hasFailedTossInFeedback(player.id, index)) {
      failedTossInCards.add(index);
    }
  });

  const avatarComponent = (
    <div data-player-id={player.id}>
      <PlayerAvatar
        playerName={player.name}
        isCurrentPlayer={isCurrentPlayer}
        isCoalitionMember={isCoalitionMember}
        isCoalitionLeader={isCoalitionLeader}
      />
    </div>
  );

  // Disable card clicks during blocking animations (but allow during highlights)
  const hasBlockingAnimations = animationStore.hasBlockingAnimations;
  const effectiveOnCardClick = hasBlockingAnimations ? undefined : onCardClick;

  const cardsComponent = (
    <PlayerCards
      player={player}
      position={position}
      cardSize={cardSize}
      gamePhase={gamePhase}
      onCardClick={effectiveOnCardClick}
      isSelectingSwapPosition={isSelectingSwapPosition}
      swapPosition={swapPosition}
      isSelectingActionTarget={isSelectingActionTarget}
      temporarilyVisibleCards={temporarilyVisibleCards}
      highlightedCards={highlightedCards}
      coalitionLeaderId={coalitionLeader?.id || null}
      humanPlayerId={humanPlayer?.id || null}
      actionTargets={actionTargets}
      failedTossInCards={failedTossInCards}
      landingCards={landingCards}
    />
  );

  return (
    <div
      className={`flex items-center ${
        position === 'bottom' || position === 'top'
          ? 'flex-row gap-2 md:gap-4'
          : 'flex-col gap-1 md:gap-2'
      }`}
    >
      {avatarFirst ? (
        <>
          {avatarComponent}
          {cardsComponent}
        </>
      ) : (
        <>
          {cardsComponent}
          {avatarComponent}
        </>
      )}
    </div>
  );
});

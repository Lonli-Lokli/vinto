// components/PlayerArea.tsx - Refactored with logic separation
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { PlayerAvatar, PlayerCards } from './presentational';
import { useUIStore } from './di-provider';
import { PlayerState } from '@/shared';
import { useGameClient } from '@/client';
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
  isDeclaringRank?: boolean;
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
  isDeclaringRank = false,
  swapPosition = null,
  isSelectingActionTarget = false,
}: PlayerAreaProps) {
  const gameClient = useGameClient();
  const uiStore = useUIStore();
  const humanPlayer = gameClient.state.players.find((p) => p.isHuman);
  const coalitionLeader = gameClient.coalitionLeader;

  const temporarilyVisibleCards = uiStore.getTemporarilyVisibleCards(player.id);
  const highlightedCards = uiStore.getHighlightedCards(player.id);

  const isCoalitionLeader = coalitionLeader?.id === player.id;
  const isCoalitionMember = player.coalitionWith.length > 0;

  const cardSize = getCardSizeForPlayer(player.cards.length, player.isHuman);
  const avatarFirst = shouldAvatarComeFirst(position);

  // Get action targets for cards that have been selected (Q, K actions, etc.)
  const actionTargets = gameClient.state.pendingAction?.targets || [];

  const avatarComponent = (
    <PlayerAvatar
      playerName={player.name}
      isCurrentPlayer={isCurrentPlayer}
      isCoalitionMember={isCoalitionMember}
      isCoalitionLeader={isCoalitionLeader}
    />
  );

  const cardsComponent = (
    <PlayerCards
      player={player}
      position={position}
      cardSize={cardSize}
      isCurrentPlayer={isCurrentPlayer}
      gamePhase={gamePhase}
      onCardClick={onCardClick}
      isSelectingSwapPosition={isSelectingSwapPosition}
      isDeclaringRank={isDeclaringRank}
      swapPosition={swapPosition}
      isSelectingActionTarget={isSelectingActionTarget}
      temporarilyVisibleCards={temporarilyVisibleCards}
      highlightedCards={highlightedCards}
      coalitionLeaderId={coalitionLeader?.id || null}
      humanPlayerId={humanPlayer?.id || null}
      actionTargets={actionTargets}
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

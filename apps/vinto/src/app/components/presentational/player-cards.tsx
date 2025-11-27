// components/presentational/player-cards.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import type { PlayerState, GamePhase } from '@vinto/shapes';
import type { PlayerPosition } from '../logic/player-area-logic';
import { isHorizontalPosition } from '../logic/player-area-logic';
import { CardSize } from '../helpers';
import { HorizontalPlayerCards } from './horizontal-player-cards';
import { VerticalPlayerCards } from './vertical-player-cards';

interface PlayerCardsProps {
  player: PlayerState;
  position: PlayerPosition;
  cardSize: CardSize;
  gamePhase: GamePhase;
  onCardClick?: (index: number) => void;
  isSelectingSwapPosition: boolean;
  swapPosition: number | null;
  isSelectingActionTarget: boolean;
  temporarilyVisibleCards: Map<number, Array<string>>; // cardIndex -> array of playerIds who can see it
  highlightedCards: Set<number>;
  coalitionLeaderId: string | null;
  observingPlayer: PlayerState | undefined;
  actionTargets?: Array<{ playerId: string; position: number }>;
  failedTossInCards?: Set<number>; // Card positions with failed toss-in feedback
  landingCards?: Set<number>;
  vintoCallerId?: string | null;
}

export const PlayerCards: React.FC<PlayerCardsProps> = observer(
  ({
    player,
    position,
    cardSize,
    gamePhase,
    onCardClick,
    isSelectingSwapPosition,
    swapPosition,
    isSelectingActionTarget,
    temporarilyVisibleCards,
    highlightedCards,
    coalitionLeaderId,
    observingPlayer,
    actionTargets = [],
    failedTossInCards = new Set<number>(),
    landingCards = new Set<number>(),
    vintoCallerId = null,
  }) => {
    // Use specialized components based on position
    const CardComponent = isHorizontalPosition(position)
      ? HorizontalPlayerCards
      : VerticalPlayerCards;

    return (
      <CardComponent
        player={player}
        position={position}
        cardSize={cardSize}
        gamePhase={gamePhase}
        onCardClick={onCardClick}
        isSelectingSwapPosition={isSelectingSwapPosition}
        swapPosition={swapPosition}
        isSelectingActionTarget={isSelectingActionTarget}
        temporarilyVisibleCards={temporarilyVisibleCards}
        highlightedCards={highlightedCards}
        coalitionLeaderId={coalitionLeaderId}
        observingPlayer={observingPlayer}
        actionTargets={actionTargets}
        failedTossInCards={failedTossInCards}
        landingCards={landingCards}
        vintoCallerId={vintoCallerId}
      />
    );
  }
);

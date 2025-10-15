// components/presentational/player-cards.tsx
'use client';

import React from 'react';
import { Card } from './card';
import type { PlayerState, GamePhase } from '@/shared';
import type { PlayerPosition, CardSize } from '../logic/player-area-logic';
import {
  canSeePlayerCard,
  isCardSelectable,
  shouldHighlightCard,
  getCardContainerClasses,
  isSidePlayer,
} from '../logic/player-area-logic';

interface PlayerCardsProps {
  player: PlayerState;
  position: PlayerPosition;
  cardSize: CardSize;
  isCurrentPlayer: boolean;
  gamePhase: GamePhase;
  onCardClick?: (index: number) => void;
  isSelectingSwapPosition: boolean;
  isDeclaringRank: boolean;
  swapPosition: number | null;
  isSelectingActionTarget: boolean;
  temporarilyVisibleCards: Set<number>;
  highlightedCards: Set<number>;
  coalitionLeaderId: string | null;
  humanPlayerId: string | null;
}

export const PlayerCards: React.FC<PlayerCardsProps> = ({
  player,
  position,
  cardSize,
  isCurrentPlayer,
  gamePhase,
  onCardClick,
  isSelectingSwapPosition,
  isDeclaringRank,
  swapPosition,
  isSelectingActionTarget,
  temporarilyVisibleCards,
  highlightedCards,
  coalitionLeaderId,
  humanPlayerId,
}) => {
  const containerClasses = getCardContainerClasses(position);
  const currentPlayerClasses = isCurrentPlayer
    ? 'p-0.5 md:p-1 rounded md:rounded-lg border border-success md:border-2 bg-success-light/20'
    : '';
  const dimmedClasses =
    isSelectingActionTarget && !onCardClick ? 'area-dimmed' : '';
  const currentPlayerAnimation = isCurrentPlayer
    ? { animation: 'gentle-pulse 2s infinite' }
    : undefined;

  return (
    <div
      className={`${containerClasses} ${currentPlayerClasses} ${dimmedClasses}`}
      style={currentPlayerAnimation}
    >
      {player.cards.map((card, index) => {
        const cardIsSelectable = isCardSelectable({
          hasOnCardClick: !!onCardClick,
          isSelectingSwapPosition,
          isDeclaringRank,
          swapPosition,
          cardIndex: index,
          isSelectingActionTarget,
        });

        const cardHighlighted = shouldHighlightCard({
          isSelectingSwapPosition,
          isDeclaringRank,
          swapPosition,
          cardIndex: index,
          isSelectingActionTarget,
        });

        const canSeeCard = canSeePlayerCard({
          cardIndex: index,
          player,
          gamePhase,
          temporarilyVisibleCards,
          coalitionLeaderId,
          humanPlayerId,
        });

        // Hide cards during swap selection for human player
        const shouldHideCard = player.isHuman && isSelectingSwapPosition;

        return (
          <Card
            key={`${card.id}-${index}`}
            card={card}
            revealed={canSeeCard && !shouldHideCard}
            position={index + 1}
            size={cardSize}
            selectable={cardIsSelectable}
            notSelectable={isSelectingActionTarget && !onCardClick}
            highlighted={cardHighlighted}
            botPeeking={highlightedCards.has(index)}
            onClick={() => onCardClick?.(index)}
            rotated={isSidePlayer(position)}
            playerId={player.id}
            cardIndex={index}
          />
        );
      })}
    </div>
  );
};

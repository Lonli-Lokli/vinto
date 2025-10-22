// components/presentational/player-cards.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { Card } from './card';
import type { PlayerState, GamePhase } from '@vinto/shapes';
import type { PlayerPosition, CardSize } from '../logic/player-area-logic';
import {
  canSeePlayerCard,
  isCardSelectable,
  shouldHighlightCard,
  getCardContainerClasses,
  isSidePlayer,
} from '../logic/player-area-logic';
import { useCardAnimationStore } from '../di-provider';

interface PlayerCardsProps {
  player: PlayerState;
  position: PlayerPosition;
  cardSize: CardSize;
  gamePhase: GamePhase;
  onCardClick?: (index: number) => void;
  isSelectingSwapPosition: boolean;
  swapPosition: number | null;
  isSelectingActionTarget: boolean;
  temporarilyVisibleCards: Set<number>;
  highlightedCards: Set<number>;
  coalitionLeaderId: string | null;
  humanPlayerId: string | null;
  actionTargets?: Array<{ playerId: string; position: number }>;
  failedTossInCards?: Set<number>; // Card positions with failed toss-in feedback
  landingCards?: Set<number>;
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
    humanPlayerId,
    actionTargets = [],
    failedTossInCards = new Set(),
    landingCards = new Set(),
  }) => {
    const containerClasses = getCardContainerClasses(position);
    const dimmedClasses =
      isSelectingActionTarget && !onCardClick ? 'area-dimmed' : '';
    const currentPlayerAnimation = undefined; // Removed container animation - individual cards animate
    const animationStore = useCardAnimationStore();

    return (
      <div
        className={`${containerClasses} ${dimmedClasses}`}
        style={currentPlayerAnimation}
      >
        {player.cards.map((card, index) => {
          const cardIsSelectable = isCardSelectable({
            hasOnCardClick: !!onCardClick,
            isSelectingSwapPosition,
            swapPosition,
            cardIndex: index,
            isSelectingActionTarget,
          });

          const cardHighlighted = shouldHighlightCard({
            isSelectingSwapPosition,
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

          // Determine selection state based on context
          const getSelectionState = ():
            | 'default'
            | 'selectable'
            | 'not-selectable' => {
            if (isSelectingActionTarget && !onCardClick) {
              return 'not-selectable';
            }
            if (cardIsSelectable) {
              return 'selectable';
            }
            return 'default';
          };

          // Determine selection variant (swap vs action)
          const selectionVariant = isSelectingSwapPosition ? 'swap' : 'action';

          // Check if this card is selected as an action target
          const isActionTargetSelected = actionTargets.some(
            (target) =>
              target.playerId === player.id && target.position === index
          );

          // Check if this card has failed toss-in feedback
          const hasFailedTossInFeedback = failedTossInCards.has(index);

          // Check if this card is involved in any animation (arriving or leaving)
          // Hide both to prevent double-rendering with ghost element
          const isAnimating = animationStore.isCardAnimating(player.id, index);

          return (
            <Card
              key={`${card.id}-${index}`}
              card={card}
              revealed={canSeeCard && !shouldHideCard}
              size={cardSize}
              selectionState={getSelectionState()}
              selectionVariant={selectionVariant}
              highlighted={cardHighlighted}
              botPeeking={highlightedCards.has(index)}
              onClick={() => onCardClick?.(index)}
              rotated={isSidePlayer(position)}
              playerId={player.id}
              cardIndex={index}
              actionTargetSelected={isActionTargetSelected}
              failedTossInFeedback={hasFailedTossInFeedback}
              hidden={landingCards.has(index) || isAnimating}
            />
          );
        })}
      </div>
    );
  }
);

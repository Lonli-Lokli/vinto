// components/presentational/player-cards.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import type { PlayerState, GamePhase } from '@vinto/shapes';
import type { PlayerPosition } from '../logic/player-area-logic';
import {
  canSeePlayerCard,
  isCardSelectable,
  shouldHighlightCard,
  getCardContainerClasses,
  isSidePlayer,
} from '../logic/player-area-logic';
import { useCardAnimationStore } from '../di-provider';
import { ClickableCard } from './clickable-card';
import { CardSize } from '../helpers';

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
        data-player-cards={player.id}
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
            targetPlayer: player,
            gamePhase,
            temporarilyVisibleCards,
            coalitionLeaderId,
            observingPlayer,
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

          // Check if this card is being peeked (temporarily visible)
          const isPeeked = temporarilyVisibleCards.has(index);

          return (
            <ClickableCard
              key={`${card.id}-${index}`}
              rank={card.rank}
              revealed={canSeeCard && !shouldHideCard}
              size={cardSize}
              selectionState={getSelectionState()}
              selectionVariant={selectionVariant}
              highlighted={cardHighlighted}
              botPeeking={highlightedCards.has(index)}
              isPeeked={isPeeked}
              onClick={() => onCardClick?.(index)}
              rotated={isSidePlayer(position)}
              playerId={player.id}
              cardIndex={index}
              actionTargetSelected={isActionTargetSelected}
              intent={hasFailedTossInFeedback ? 'failure' : undefined}
              hidden={landingCards.has(index) || isAnimating}
            />
          );
        })}
      </div>
    );
  }
);

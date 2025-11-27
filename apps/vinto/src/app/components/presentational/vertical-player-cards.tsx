// components/presentational/vertical-player-cards.tsx
// For left/right players - vertical card layout with wrapping for rotated cards
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import type { PlayerState, GamePhase } from '@vinto/shapes';
import type { PlayerPosition } from '../logic/player-area-logic';
import {
  canSeePlayerCard,
  isCardSelectable,
  shouldHighlightCard,
} from '../logic/player-area-logic';
import { useCardAnimationStore } from '../di-provider';
import { ClickableCard } from './clickable-card';
import { CardSize } from '../helpers';

interface VerticalPlayerCardsProps {
  player: PlayerState;
  position: PlayerPosition;
  cardSize: CardSize;
  gamePhase: GamePhase;
  onCardClick?: (index: number) => void;
  isSelectingSwapPosition: boolean;
  swapPosition: number | null;
  isSelectingActionTarget: boolean;
  temporarilyVisibleCards: Map<number, Array<string>>;
  highlightedCards: Set<number>;
  coalitionLeaderId: string | null;
  observingPlayer: PlayerState | undefined;
  actionTargets?: Array<{ playerId: string; position: number }>;
  failedTossInCards?: Set<number>;
  landingCards?: Set<number>;
  vintoCallerId?: string | null;
}

export const VerticalPlayerCards: React.FC<VerticalPlayerCardsProps> = observer(
  ({
    player,
    position: _position,
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
    vintoCallerId = null,
  }) => {
    const animationStore = useCardAnimationStore();
    const dimmedClasses =
      isSelectingActionTarget && !onCardClick ? 'area-dimmed' : '';

    // For vertical layouts (left/right), use CSS Grid with auto-flow: column
    // This allows cards to stack vertically in columns, wrapping to new columns when needed
    // Cards are rotated 90°, so we need to consider:
    // - row-gap controls spacing along the column (vertical before rotation = horizontal after)
    // - column-gap controls spacing between columns (horizontal before rotation = vertical after)
    const cardCount = player.cards.length;
    
    // Determine max rows based on card count - aim for 2-3 columns max
    const maxRows = cardCount <= 6 ? cardCount : Math.ceil(cardCount / 2);
    
    return (
      <div
        className={`grid auto-cols-max ${dimmedClasses}`}
        style={{
          gridAutoFlow: 'column',
          gridTemplateRows: `repeat(${maxRows}, auto)`,
          maxHeight: '100%',
          rowGap: '0rem', // becomes horizontal gap after rotation
          columnGap: '1rem', // becomes vertical gap between columns after rotation
        }}
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

          const shouldHideCard = player.isHuman && isSelectingSwapPosition;

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

          const selectionVariant = isSelectingSwapPosition ? 'swap' : 'action';

          const isActionTargetSelected = actionTargets.some(
            (target) =>
              target.playerId === player.id && target.position === index
          );

          const hasFailedTossInFeedback = failedTossInCards.has(index);
          const isAnimating = animationStore.isCardAnimating(player.id, index);
          const isPeeked = temporarilyVisibleCards.has(index);

          // Determine if this card is known by bots (for final round UI)
          // Show bot knowledge indicator when:
          // 1. Human is the Vinto caller (observing player is Vinto caller)
          // 2. We're in final phase
          // 3. This is a bot player's card (coalition member)
          // 4. This card is in the bot's own knownCardPositions OR any other bot has knowledge of it
          let isBotKnown = false;
          if (
            gamePhase === 'final' &&
            vintoCallerId === observingPlayer?.id &&
            player.isBot
          ) {
            // Check if the bot knows this card (own knowledge)
            if (player.knownCardPositions.includes(index)) {
              isBotKnown = true;
            }
          }

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
              rotated={true}
              playerId={player.id}
              cardIndex={index}
              actionTargetSelected={isActionTargetSelected}
              intent={hasFailedTossInFeedback ? 'failure' : undefined}
              hidden={landingCards.has(index) || isAnimating}
              isBotKnown={isBotKnown}
            />
          );
        })}
      </div>
    );
  }
);

VerticalPlayerCards.displayName = 'VerticalPlayerCards';

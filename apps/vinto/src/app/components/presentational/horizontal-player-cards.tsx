// components/presentational/horizontal-player-cards.tsx
// For top/bottom players - horizontal card layout with wrapping
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

interface HorizontalPlayerCardsProps {
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

export const HorizontalPlayerCards: React.FC<HorizontalPlayerCardsProps> = observer(
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

    return (
      <div
        className={`flex flex-wrap gap-1 justify-center max-w-full ${dimmedClasses}`}
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
              rotated={false}
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

HorizontalPlayerCards.displayName = 'HorizontalPlayerCards';

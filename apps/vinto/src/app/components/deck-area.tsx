// components/DeckArea.tsx
'use client';

import React from 'react';
import { Card as CardType } from '../shapes';
import { DrawPile } from './draw-pile';
import { DiscardPile } from './discard-pile';
import { DrawnCard } from './drawn-card';

interface DeckAreaProps {
  discardPile: CardType[];
  pendingCard: CardType | null;
  isChoosingCardAction: boolean;
  isSelectingSwapPosition: boolean;
  isDeclaringRank: boolean;
  canDrawCard: boolean;
  onDrawCard: () => void;
  isMobile?: boolean;
}

export const DeckArea: React.FC<DeckAreaProps> = ({
  discardPile,
  pendingCard,
  isChoosingCardAction,
  isSelectingSwapPosition,
  isDeclaringRank,
  canDrawCard,
  onDrawCard,
  isMobile = false,
}) => {
  const cardSize = 'lg';
  const gap = isMobile ? 'gap-2' : 'gap-12';

  // Show drawn card whenever there's a pending card
  const isDrawnCardVisible = !!pendingCard;

  // Show interactive elements only when actively choosing
  const showInteraction =
    !!pendingCard &&
    (isChoosingCardAction || isSelectingSwapPosition || isDeclaringRank);

  return (
    <div className="flex flex-col items-center gap-2">
      {/* Draw and Discard Piles - Side by Side */}
      <div className={`flex ${gap} items-center`}>
        <DrawPile
          clickable={canDrawCard}
          onClick={onDrawCard}
          size={cardSize}
          isMobile={isMobile}
        />
        <DiscardPile
          cards={discardPile}
          size={cardSize}
          isMobile={isMobile}
        />
      </div>

      {/* Drawn Card - Below both piles - Always reserves space */}
      <DrawnCard
        card={pendingCard ?? undefined}
        isVisible={isDrawnCardVisible}
        showAction={showInteraction && isChoosingCardAction}
        size={cardSize}
        isMobile={isMobile}
      />
    </div>
  );
};

// components/DeckArea.tsx
'use client';

import React from 'react';
import { Card as CardType, Pile } from '@vinto/shapes';
import { DiscardPile } from './discard-pile';
import { DrawPile } from './draw-pile';
import { DrawnCard } from '../drawn-card';

interface DeckAreaProps {
  discardPile: Pile;
  pendingCard: CardType | null;
  canDrawCard: boolean;
  onDrawCard: () => void;
  isMobile?: boolean;
  isSelectingActionTarget?: boolean;
}

export const DeckArea: React.FC<DeckAreaProps> = ({
  discardPile,
  pendingCard,
  canDrawCard,
  onDrawCard,
  isMobile = false,
  isSelectingActionTarget = false,
}) => {
  const cardSize = 'lg';
  const gap = isMobile ? 'gap-2' : 'gap-12';

  // Show drawn card whenever there's a pending card
  const isDrawnCardVisible = !!pendingCard;

  // Dim deck area when selecting action targets
  const shouldDimDeckArea = isSelectingActionTarget;

  return (
    <div
      className={`flex flex-col items-center gap-2 pointer-events-auto ${
        shouldDimDeckArea ? 'area-dimmed' : ''
      }`}
    >
      {/* Draw and Discard Piles - Side by Side */}
      <div className={`flex ${gap} items-center`}>
        <DrawPile
          clickable={canDrawCard}
          onClick={onDrawCard}
          size={cardSize}
          isMobile={isMobile}
        />
        <DiscardPile pile={discardPile} size={cardSize} isMobile={isMobile} />
      </div>

      {/* Drawn Card - Below both piles - Always reserves space */}
      <DrawnCard
        card={pendingCard ?? undefined}
        isVisible={isDrawnCardVisible}
        size={cardSize}
        isMobile={isMobile}
      />
    </div>
  );
};

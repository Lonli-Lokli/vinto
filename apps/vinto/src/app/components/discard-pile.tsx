// components/discard-pile.tsx
'use client';

import React from 'react';
import { Card } from './card';
import { Card as CardType } from '../shapes';

interface DiscardPileProps {
  cards: CardType[];
  size?: 'sm' | 'md' | 'lg' | 'xl';
  isMobile?: boolean;
}

export const DiscardPile: React.FC<DiscardPileProps> = ({
  cards,
  size = 'lg',
  isMobile = false,
}) => {
  const textSize = isMobile ? 'text-2xs' : 'text-xs';
  const labelMargin = isMobile ? 'mt-1' : 'mt-2';
  const labelPadding = isMobile ? 'px-2 py-0.5' : 'px-2 py-1';

  return (
    <div className="text-center" data-discard-pile="true">
      <Card card={cards[0]} revealed={cards.length > 0} size={size} />
      <div
        className={`${labelMargin} ${textSize} text-white font-medium bg-black/30 rounded ${labelPadding}`}
      >
        DISCARD
      </div>
    </div>
  );
};

// components/discard-pile.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { Card } from './card';
import { Pile, Card as CardType } from '@/shared';
import { useCardAnimationStore, useUIStore } from '../di-provider';

interface DiscardPileProps {
  pile: Pile;
  size?: 'sm' | 'md' | 'lg' | 'xl';
  isMobile?: boolean;
}

export const DiscardPile: React.FC<DiscardPileProps> = observer(
  ({ pile, size = 'lg', isMobile = false }) => {
    const animationStore = useCardAnimationStore();
    const uiStore = useUIStore();
    const textSize = isMobile ? 'text-2xs' : 'text-xs';
    const labelMargin = isMobile ? 'mt-1' : 'mt-2';
    const labelPadding = isMobile ? 'px-2 py-0.5' : 'px-2 py-1';

    // While a card is being animated to the discard pile, show the previous card
    // This prevents the visual bug where the new card appears before animation completes
    const animatingCard = animationStore.cardAnimatingToDiscard;

    // If animating, we need to show what was on top before (second card)
    // Otherwise show the current top card
    let cardToShow: CardType | undefined;
    let shouldReveal: boolean;

    if (animatingCard) {
      // During animation, show the second-to-top card (what was top before)
      cardToShow = pile.length >= 2 ? pile.peekAt(1) : undefined;
      shouldReveal = cardToShow !== undefined;
    } else {
      // Normal case: show top card
      cardToShow = pile.peekTop();
      shouldReveal = !pile.isEmpty();
    }

    // Get declaration feedback (green for correct, red for incorrect)
    const declarationFeedback = uiStore.getDiscardPileDeclarationFeedback();

    return (
      <div
        className="flex flex-col items-center justify-center"
        data-discard-pile="true"
      >
        <Card
          card={cardToShow}
          revealed={shouldReveal}
          size={size}
          selectionState="default"
          declarationFeedback={declarationFeedback}
        />
        <div
          className={`${labelMargin} ${textSize} text-white font-medium bg-overlay rounded ${labelPadding}`}
        >
          DISCARD
        </div>
      </div>
    );
  }
);

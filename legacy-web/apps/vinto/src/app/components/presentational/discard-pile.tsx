// components/discard-pile.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { Card } from './card';
import { Pile, Card as CardType } from '@vinto/shapes';
import { useUIStore } from '../di-provider';
import { getPileSettings } from '../helpers';

interface DiscardPileProps {
  pile: Pile;
  size: 'sm' | 'md' | 'lg' | 'xl';
  isMobile: boolean;
}

export const DiscardPile: React.FC<DiscardPileProps> = observer(
  ({ pile, size = 'lg', isMobile = false }) => {
    const uiStore = useUIStore();

    const { textSize, labelMargin, labelPadding, textClasses } = getPileSettings(isMobile);

    // While a card is being animated to the discard pile, show the previous card
    // This prevents the visual bug where the new card appears before animation completes

    const cardToShow: CardType | undefined = pile.peekTop();
    const shouldReveal = !pile.isEmpty();

    // Get declaration feedback (green for correct, red for incorrect)
    const declarationFeedback = uiStore.getDiscardPileDeclarationFeedback();
    const intent =
      declarationFeedback === null
        ? undefined
        : declarationFeedback
        ? 'success'
        : 'failure';

    return (
      <div className="flex flex-col items-center justify-center">
        <div className="flex" data-discard-pile="true">
          <Card
            rank={cardToShow?.rank}
            revealed={shouldReveal}
            size={size}
            selectionState="default"
            intent={intent}
            disableFlipAnimation={true}
          />
        </div>
        <div
          className={`${labelMargin} ${textSize} ${textClasses} ${labelPadding}`}
        >
          DISCARD
        </div>
      </div>
    );
  }
);

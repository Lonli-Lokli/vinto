// components/drawn-card.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { Card } from './presentational';
import { Card as CardType } from '@vinto/shapes';
import { useCardAnimationStore, useUIStore } from './di-provider';

interface DrawnCardProps {
  card?: CardType;
  isVisible: boolean;
  size?: 'sm' | 'md' | 'lg' | 'xl';
  isMobile?: boolean;
}

export const DrawnCard: React.FC<DrawnCardProps> = observer(
  ({ card, isVisible, size = 'lg', isMobile = false }) => {
    const animationStore = useCardAnimationStore();
    const uiStore = useUIStore();
    const textSize = isMobile ? 'text-2xs' : 'text-xs';
    const labelMargin = isMobile ? 'mt-1' : 'mt-2';
    const labelPadding = isMobile ? 'px-2 py-0.5' : 'px-2 py-1';

    // Check if the drawn card is being animated TO or FROM
    // Hide if animating to drawn area (card arriving) or animating from drawn area (card leaving)
    const isDrawnCardAnimating = animationStore.isDrawnCardAnimating();

    // Completely hide during animation, show after
    const shouldShowCard = isVisible && !isDrawnCardAnimating;

    // Get declaration feedback for drawn card (correct declarations)
    const declarationFeedback = uiStore.getDrawnCardDeclarationFeedback();
    const intent =
      declarationFeedback === null
        ? undefined
        : declarationFeedback
        ? 'success'
        : 'failure';

    return (
      <div
        className={`flex flex-col items-center justify-center ${
          !shouldShowCard ? 'invisible' : ''
        }`}
      >
        <Card
          data-pending-card="true"
          card={card}
          revealed={!!card}
          size={size}
          highlighted={!!card}
          isPending={true}
          selectionState="default"
          intent={intent}
        />
        <div
          className={`${labelMargin} ${textSize} text-white font-medium bg-warning/80 rounded ${labelPadding}`}
        >
          DRAWN
        </div>
      </div>
    );
  }
);

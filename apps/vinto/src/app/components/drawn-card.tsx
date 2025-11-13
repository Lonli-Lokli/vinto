// components/drawn-card.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { Card } from './presentational';
import { Rank } from '@vinto/shapes';
import { useCardAnimationStore, useUIStore } from './di-provider';
import { getPileSettings } from './helpers';

interface DrawnCardProps {
  rank?: Rank;
  isVisible: boolean;
  size?: 'sm' | 'md' | 'lg' | 'xl';
  isMobile?: boolean;
}

export const DrawnCard: React.FC<DrawnCardProps> = observer(
  ({ rank, isVisible, size = 'lg', isMobile = false }) => {
    const animationStore = useCardAnimationStore();
    const uiStore = useUIStore();
    const { textSize, labelMargin, labelPadding, textClasses } =
      getPileSettings(isMobile);

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
        className="flex flex-col items-center justify-center"
        data-discard-pile="true"
      >
        <div className={`flex ${!shouldShowCard ? 'invisible' : ''}`}>
          <Card
            data-pending-card="true"
            rank={rank}
            revealed={!!rank}
            size={size}
            highlighted={!!rank}
            isPending={true}
            selectionState="default"
            intent={intent}
            disableFlipAnimation={true}
          />
        </div>
        <div
          className={`${labelMargin} ${textSize} ${textClasses} ${labelPadding} ${
            !shouldShowCard ? 'invisible' : ''
          }`}
        >
          DRAWN
        </div>
      </div>
    );
  }
);

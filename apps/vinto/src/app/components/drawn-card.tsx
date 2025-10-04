// components/drawn-card.tsx
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { Card } from './card';
import { Card as CardType } from '../shapes';
import { useCardAnimationStore } from './di-provider';

interface DrawnCardProps {
  card?: CardType;
  isVisible: boolean;
  size?: 'sm' | 'md' | 'lg' | 'xl';
  isMobile?: boolean;
}

export const DrawnCard: React.FC<DrawnCardProps> = observer(
  ({ card, isVisible, size = 'lg', isMobile = false }) => {
    const animationStore = useCardAnimationStore();
    const textSize = isMobile ? 'text-2xs' : 'text-xs';
    const labelMargin = isMobile ? 'mt-1' : 'mt-2';
    const labelPadding = isMobile ? 'px-2 py-0.5' : 'px-2 py-1';

    // Check if there's an active draw animation targeting the drawn area
    const hasActiveDrawAnimation = Array.from(
      animationStore.activeAnimations.values()
    ).some((anim) => anim.type === 'draw' && !anim.completed);

    // Completely hide during animation, show after
    const shouldShowCard = isVisible && !hasActiveDrawAnimation;

    return (
      <div
        className={`text-center ${!shouldShowCard ? 'invisible' : ''}`}
        data-pending-card="true"
      >
        <div className="relative">
          <Card
            card={card}
            revealed={!!card}
            size={size}
            highlighted={!!card}
            isPending={true}
          />
          {card && (
            <div
              className={`absolute ${
                isMobile
                  ? '-top-1.5 -left-1.5 w-4 h-4'
                  : '-top-2 -left-2 w-5 h-5'
              } bg-amber-500 text-white rounded-full ${
                isMobile ? 'text-2xs' : 'text-xs'
              } font-bold flex items-center justify-center animate-pulse`}
            >
              !
            </div>
          )}
        </div>
        <div
          className={`${labelMargin} ${textSize} text-white font-medium bg-amber-600/80 rounded ${labelPadding}`}
        >
          DRAWN
        </div>
      </div>
    );
  }
);

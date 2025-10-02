// components/drawn-card.tsx
'use client';

import React from 'react';
import { Card } from './card';
import { Card as CardType } from '../shapes';

interface DrawnCardProps {
  card?: CardType;
  isVisible: boolean;
  showAction?: boolean;
  size?: 'sm' | 'md' | 'lg' | 'xl';
  isMobile?: boolean;
}

export const DrawnCard: React.FC<DrawnCardProps> = ({
  card,
  isVisible,
  showAction = false,
  size = 'lg',
  isMobile = false,
}) => {
  const textSize = isMobile ? 'text-2xs' : 'text-xs';
  const labelMargin = isMobile ? 'mt-1' : 'mt-2';
  const labelPadding = isMobile ? 'px-2 py-0.5' : 'px-2 py-1';

  return (
    <div
      className={`text-center ${!isVisible ? 'invisible' : ''}`}
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
              isMobile ? '-top-1.5 -left-1.5 w-4 h-4' : '-top-2 -left-2 w-5 h-5'
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
      {showAction && card?.action && (
        <div
          className={`mt-1 text-2xs text-white bg-blue-600/80 rounded ${
            isMobile ? 'px-1 py-0.5' : 'px-2 py-0.5'
          }`}
        >
          {card.action}
        </div>
      )}
    </div>
  );
};

// components/draw-pile.tsx
'use client';

import React from 'react';
import { DrawCard } from './draw-card';

interface DrawPileProps {
  clickable: boolean;
  onClick: () => void;
  size?: 'sm' | 'md' | 'lg' | 'xl';
  isMobile?: boolean;
}

export const DrawPile: React.FC<DrawPileProps> = ({
  clickable,
  onClick,
  size = 'lg',
  isMobile = false,
}) => {
  const textSize = isMobile ? 'text-2xs' : 'text-xs';
  const labelMargin = isMobile ? 'mt-1' : 'mt-2';
  const labelPadding = isMobile ? 'px-2 py-0.5' : 'px-2 py-1';

  return (
    <div className="text-center" data-deck-pile="true">
      <DrawCard size={size} clickable={clickable} onClick={onClick} />
      <div
        className={`${labelMargin} ${textSize} text-white font-medium bg-surface-tertiary/30 rounded ${labelPadding}`}
      >
        DRAW
      </div>
    </div>
  );
};

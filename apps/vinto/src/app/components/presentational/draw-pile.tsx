// components/draw-pile.tsx
'use client';

import React from 'react';
import { DrawCard } from './draw-card';
import { getPileSettings } from '../helpers';

interface DrawPileProps {
  clickable: boolean;
  onClick: () => void;
  size: 'sm' | 'md' | 'lg' | 'xl';
  isMobile: boolean;
}

export const DrawPile: React.FC<DrawPileProps> = ({
  clickable,
  onClick,
  size = 'lg',
  isMobile = false,
}) => {
  const { textSize, labelMargin, labelPadding, textClasses } = getPileSettings(isMobile);

  return (
    <div className="flex flex-col items-center justify-center">
      <div className="flex" data-deck-pile="true">
        <DrawCard size={size} clickable={clickable} onClick={onClick} />
      </div>
      <div
        className={`${labelMargin} ${textSize} ${textClasses} ${labelPadding}`}
      >
        DRAW
      </div>
    </div>
  );
};

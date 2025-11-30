// components/presentational/draw-card.tsx
'use client';

import React from 'react';
import { Card } from './card';

interface DrawCardProps {
  size?: 'sm' | 'md' | 'lg' | 'xl';
  clickable: boolean;
  onClick: () => void;
}

/**
 * DrawCard - Decorator component that wraps Card with clickable behavior
 * Adds hover effects when clickable, without the pulsing animation of 'selectable' state
 * This allows the draw pile to use the full Card component with animations, rank display, etc.
 */
export const DrawCard: React.FC<DrawCardProps> = ({
  size = 'lg',
  clickable,
  onClick,
}) => {
  return (
    <div
      className={
        (clickable
          ? 'cursor-pointer hover:scale-105 active:scale-95 hover:shadow-lg transition-all duration-150'
          : '') + ' flex flex-col items-center justify-center'
      }
      onClick={clickable ? onClick : undefined}
      data-testid="draw-pile"
    >
      <Card size={size} selectionState="default" />
    </div>
  );
};

// components/Card.tsx
'use client';

import React, { FC } from 'react';
import { Card as CardType, NeverError, Rank } from '@/shared';
import {
  Image_2,
  Image_3,
  Image_4,
  Image_5,
  Image_6,
  Image_7,
  Image_8,
  Image_9,
  Image_10,
  Image_J,
  Image_Q,
  Image_K,
  Image_A,
  Image_Joker,
  Image_Cover,
} from './image';

type CardSize = 'sm' | 'md' | 'lg' | 'xl' | 'auto';
const CARD_SIZES: Record<CardSize, string> = {
  sm: 'w-6 h-9 text-2xs',
  md: 'w-8 h-12 text-2xs',
  lg: 'w-10 h-14 text-xs',
  xl: 'w-12 h-16 text-sm',
  auto: 'w-full h-full text-xs',
};

interface CardProps {
  card?: CardType;
  revealed?: boolean;
  position?: number;
  size?: CardSize;
  clickable?: boolean;
  highlighted?: boolean;
  botPeeking?: boolean;
  onClick?: () => void;
  rotated?: boolean;
  // For animation tracking
  playerId?: string;
  cardIndex?: number; // The actual array index (0-based)
  isPending?: boolean;
  // New selection states
  selectable?: boolean; // Card can be selected (shows pulsing border)
  notSelectable?: boolean; // Card cannot be selected (dimmed)
}

export function Card({
  card,
  revealed = false,
  position = 0,
  size = 'md',
  clickable = false,
  highlighted = false,
  botPeeking = false,
  onClick,
  rotated = false,
  playerId,
  cardIndex,
  isPending = false,
  selectable = false,
  notSelectable = false,
}: CardProps) {
  // Build data attributes for animation tracking
  const dataAttributes: Record<string, string> = {};
  if (playerId && cardIndex !== undefined && cardIndex >= 0) {
    dataAttributes['data-player-id'] = playerId;
    dataAttributes['data-card-position'] = cardIndex.toString();
  }
  if (isPending) {
    dataAttributes['data-pending-card'] = 'true';
  }

  // Determine visual state classes
  const getCardStateClasses = () => {
    if (notSelectable) return 'card-not-selectable';
    if (selectable) return 'card-selectable animate-card-select-pulse';
    if (clickable)
      return 'cursor-pointer hover:scale-102 active:scale-95 hover:shadow-md';
    return '';
  };

  return (
    <div
      className={`
        ${size === 'auto' ? 'w-full h-full' : CARD_SIZES[size]}
        relative flex
        transition-all duration-150 select-none
        ${rotated ? 'transform-gpu' : ''}
        ${getCardStateClasses()}
      `}
      style={
        highlighted && !selectable
          ? {
              transform: rotated ? 'rotate(90deg)' : undefined,
              animation: 'ring-pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite',
            }
          : rotated
          ? { transform: 'rotate(90deg)' }
          : undefined
      }
      onClick={
        (clickable || selectable) && !notSelectable ? onClick : undefined
      }
      {...dataAttributes}
    >
      {revealed && card ? (
        <RankComponent rank={card.rank} />
      ) : (
        <CardBackComponent botPeeking={botPeeking} />
      )}

      {position > 0 && (
        <div className="absolute -top-2 -right-2 w-4 h-4 bg-secondary text-on-primary rounded-full text-2xs font-bold flex items-center justify-center">
          {position}
        </div>
      )}
    </div>
  );
}

const CardBackComponent: FC<{ botPeeking?: boolean }> = ({
  botPeeking = false,
}) => {
  // Container has border and background, image fills it completely
  const containerClassName = `h-full w-auto rounded border shadow-theme-sm overflow-hidden ${
    botPeeking
      ? 'border-warning bg-card-revealed-gradient'
      : 'border-primary bg-card-gradient'
  }`;

  const imageClassName = 'h-full w-full object-cover';

  return (
    <div className={containerClassName}>
      <Image_Cover className={imageClassName} />
    </div>
  );
};

const RankComponent: FC<{
  rank: Rank;
}> = ({ rank }) => {
  // Container has border and background, image fills it completely
  const containerClassName =
    'h-full w-auto rounded border border-primary bg-surface-primary shadow-sm overflow-hidden';
  const imageClassName = 'h-full w-full object-contain';

  const renderImage = () => {
    switch (rank) {
      case '2':
        return <Image_2 className={imageClassName} />;
      case '3':
        return <Image_3 className={imageClassName} />;
      case '4':
        return <Image_4 className={imageClassName} />;
      case '5':
        return <Image_5 className={imageClassName} />;
      case '6':
        return <Image_6 className={imageClassName} />;
      case '7':
        return <Image_7 className={imageClassName} />;
      case '8':
        return <Image_8 className={imageClassName} />;
      case '9':
        return <Image_9 className={imageClassName} />;
      case '10':
        return <Image_10 className={imageClassName} />;
      case 'J':
        return <Image_J className={imageClassName} />;
      case 'Q':
        return <Image_Q className={imageClassName} />;
      case 'K':
        return <Image_K className={imageClassName} />;
      case 'A':
        return <Image_A className={imageClassName} />;
      case 'Joker':
        return <Image_Joker className={imageClassName} />;
      default:
        throw new NeverError(rank);
    }
  };

  return <div className={containerClassName}>{renderImage()}</div>;
};

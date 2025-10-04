// components/Card.tsx
'use client';

import React, { FC } from 'react';
import { Card as CardType, NeverError, Rank } from '../shapes';
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

  return (
    <div
      className={`
        ${size === 'auto' ? 'w-full h-full' : CARD_SIZES[size]}
        relative flex
        transition-all duration-150 select-none
        ${rotated ? 'transform-gpu' : ''}
        ${
          clickable
            ? 'cursor-pointer hover:scale-102 active:scale-95 hover:shadow-md'
            : ''
        }
      `}
      style={
        highlighted
          ? {
              transform: rotated ? 'rotate(90deg)' : undefined,
              animation: 'ring-pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite',
            }
          : rotated
          ? { transform: 'rotate(90deg)' }
          : undefined
      }
      onClick={clickable ? onClick : undefined}
      {...dataAttributes}
    >
      {revealed && card ? (
        <RankComponent rank={card.rank} size={size} />
      ) : (
        <CardBackComponent size={size} botPeeking={botPeeking} />
      )}

      {position > 0 && (
        <div className="absolute -top-2 -right-2 w-4 h-4 bg-slate-600 text-white rounded-full text-2xs font-bold flex items-center justify-center">
          {position}
        </div>
      )}

      {card?.action && revealed && (
        <div className="absolute -top-1 -left-1 w-2 h-2 bg-blue-500 rounded-full shadow-sm" />
      )}
    </div>
  );
}

const CardBackComponent: FC<{ size: CardSize; botPeeking?: boolean }> = ({ size, botPeeking = false }) => {
  // Always use full height and let width adjust to maintain aspect ratio
  const className = `h-full w-auto object-contain rounded border shadow-sm ${
    botPeeking
      ? 'border-amber-500 bg-gradient-to-br from-amber-600 to-amber-700'
      : 'border-poker-green-600 bg-gradient-to-br from-poker-green-700 to-poker-green-800'
  }`;
  
  return <Image_Cover className={className} />;
};

const RankComponent: FC<{
  rank: Rank;
  size: CardSize;
}> = ({ rank, size }) => {
  // Always use full height and let width adjust to maintain aspect ratio
  const className = 'h-full w-auto object-contain rounded border border-gray-300 bg-white shadow-sm';

  switch (rank) {
    case '2':
      return <Image_2 className={className} />;
    case '3':
      return <Image_3 className={className} />;
    case '4':
      return <Image_4 className={className} />;
    case '5':
      return <Image_5 className={className} />;
    case '6':
      return <Image_6 className={className} />;
    case '7':
      return <Image_7 className={className} />;
    case '8':
      return <Image_8 className={className} />;
    case '9':
      return <Image_9 className={className} />;
    case '10':
      return <Image_10 className={className} />;
    case 'J':
      return <Image_J className={className} />;
    case 'Q':
      return <Image_Q className={className} />;
    case 'K':
      return <Image_K className={className} />;
    case 'A':
      return <Image_A className={className} />;
    case 'Joker':
      return <Image_Joker className={className} />;
    default:
      throw new NeverError(rank);
  }
};

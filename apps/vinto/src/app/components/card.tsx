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
} from './image';

interface CardProps {
  card?: CardType;
  revealed?: boolean;
  position?: number;
  size?: 'sm' | 'md' | 'lg' | 'xl';
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
  const sizeClasses = {
    sm: 'w-6 h-9 text-2xs',
    md: 'w-8 h-12 text-2xs',
    lg: 'w-10 h-14 text-xs',
    xl: 'w-12 h-16 text-sm',
  } as const;

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
        ${sizeClasses[size]}
        relative rounded border
        flex flex-col items-center justify-center
        transition-all duration-150 select-none
        ${rotated ? 'transform-gpu' : ''}
        ${
          revealed && card
            ? 'bg-white border-gray-300 shadow-sm'
            : botPeeking
            ? 'bg-gradient-to-br from-amber-600 to-amber-700 border-amber-500 text-white shadow-lg'
            : 'bg-gradient-to-br from-poker-green-700 to-poker-green-800 border-poker-green-600 text-white'
        }
        ${
          clickable
            ? 'cursor-pointer hover:scale-102 active:scale-95 hover:shadow-md'
            : ''
        }
        ${highlighted ? 'ring-2 ring-yellow-400 animate-pulse' : ''}
      `}
      style={rotated ? { transform: 'rotate(90deg)' } : undefined}
      onClick={clickable ? onClick : undefined}
      {...dataAttributes}
    >
      {revealed && card ? (
        <RankComponent rank={card.rank} size={size} />
      ) : (
        <span
          className={`font-bold ${
            size === 'sm'
              ? 'text-sm'
              : size === 'lg'
              ? 'text-2xl'
              : size === 'xl'
              ? 'text-3xl'
              : 'text-lg'
          }`}
        >
          ?
        </span>
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

const RankComponent: FC<{ rank: Rank; size: 'sm' | 'md' | 'lg' | 'xl' }> = ({ rank, size }) => {
  // Size classes that fit within card containers with proper aspect ratio
  const sizeClass = {
    sm: 'w-5 h-7',    // fits in w-6 h-9
    md: 'w-7 h-10',   // fits in w-8 h-12
    lg: 'w-9 h-12',   // fits in w-10 h-14
    xl: 'w-11 h-14',  // fits in w-12 h-16
  }[size];

  const className = `${sizeClass} object-contain`;

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

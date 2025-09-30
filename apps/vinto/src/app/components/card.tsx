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
  onClick?: () => void;
  rotated?: boolean;
}

export function Card({
  card,
  revealed = false,
  position = 0,
  size = 'md',
  clickable = false,
  highlighted = false,
  onClick,
  rotated = false,
}: CardProps) {
  const sizeClasses = {
    sm: 'w-7 h-10 text-2xs',
    md: 'w-10 h-14 text-2xs',
    lg: 'w-12 h-17 text-xs',
    xl: 'w-14 h-20 text-sm',
  } as const;

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
    sm: 'w-6 h-8',    // fits in w-7 h-10
    md: 'w-8 h-12',   // fits in w-10 h-14
    lg: 'w-10 h-14',  // fits in w-12 h-17
    xl: 'w-12 h-17',  // fits in w-14 h-20
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

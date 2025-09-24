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
  size?: 'sm' | 'md' | 'lg';
  clickable?: boolean;
  highlighted?: boolean;
  onClick?: () => void;
}

export function Card({
  card,
  revealed = false,
  position = 0,
  size = 'md',
  clickable = false,
  highlighted = false,
  onClick,
}: CardProps) {
  const sizeClasses = {
    sm: 'w-8 h-12 text-xs',
    md: 'w-12 h-18 text-sm',
    lg: 'w-16 h-24 text-base',
  } as const;

  return (
    <div
      className={`
        ${sizeClasses[size]}
        relative rounded-lg border-2
        flex flex-col items-center justify-center
        transition-all duration-200 select-none
        ${revealed && card
          ? 'bg-neutral-50 border-gray-300 shadow-md'
          : 'bg-gradient-to-br from-blue-600 to-blue-700 border-blue-500 text-white'}
        ${clickable ? 'cursor-pointer hover:scale-105 active:scale-95 hover:shadow-lg' : ''}
        ${highlighted ? 'ring-2 ring-yellow-400 animate-pulse' : ''}
      `}
      onClick={clickable ? onClick : undefined}
    >
      {revealed && card ? (
        <>
          <RankComponent rank={card.rank} />
          <span className="mt-0.5 text-[10px] text-gray-600 font-medium">{card.rank}</span>
        </>
      ) : (
        <span className={`font-bold ${size === 'sm' ? 'text-sm' : size === 'lg' ? 'text-2xl' : 'text-lg'}`}>
          ?
        </span>
      )}

      {position > 0 && (
        <div className="absolute -top-2 -right-2 w-5 h-5 bg-yellow-400 text-black rounded-full text-xs font-bold flex items-center justify-center">
          {position}
        </div>
      )}

      {card?.action && revealed && (
        <div className="absolute -top-1 -left-1 w-3 h-3 bg-orange-400 rounded-full shadow-sm" />
      )}
    </div>
  );
}

const RankComponent: FC<{rank: Rank}> = ({ rank }) => {
  switch (rank) {
    case '2':
      return <Image_2 />;
    case '3':
      return <Image_3 />;
    case '4':
      return <Image_4 />;
    case '5':
      return <Image_5 />;
    case '6':
      return <Image_6 />;
    case '7':
      return <Image_7 />;
    case '8':
      return <Image_8 />;
    case '9':
      return <Image_9 />;
    case '10':
      return <Image_10 />;
    case 'J':
      return <Image_J />;
    case 'Q':
      return <Image_Q />;
    case 'K':
      return <Image_K />;
    case 'A':
      return <Image_A />;
    case 'Joker':
      return <Image_Joker />;
    default:
      throw new NeverError(rank);
  }
}
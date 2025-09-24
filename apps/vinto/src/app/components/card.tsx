// components/Card.tsx
'use client';

import React from 'react';
import { Card as CardType } from '../shapes';
import { getSuitColor } from '../lib/game-helpers';

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
  onClick 
}: CardProps) {
  const sizeClasses = {
    sm: 'w-8 h-12 text-xs',
    md: 'w-12 h-18 text-sm', 
    lg: 'w-16 h-24 text-base'
  };

  return (
    <div
      className={`
        ${sizeClasses[size]}
        relative rounded-lg border-2 
        flex flex-col items-center justify-center
        transition-all duration-200 select-none
        ${revealed && card
          ? 'bg-white border-gray-300 shadow-md'
          : 'bg-gradient-to-br from-blue-600 to-blue-700 border-blue-500 text-white'
        }
        ${clickable ? 'cursor-pointer hover:scale-105 active:scale-95 hover:shadow-lg' : ''}
        ${highlighted ? 'ring-2 ring-yellow-400 animate-pulse' : ''}
      `}
      onClick={clickable ? onClick : undefined}
    >
      {revealed && card ? (
        <>
          <span className={`font-bold ${getSuitColor(card.suit)}`}>
            {card.rank}
          </span>
          {card.suit && (
            <span className={`${size === 'sm' ? 'text-sm' : 'text-lg'} ${getSuitColor(card.suit)}`}>
              {card.suit}
            </span>
          )}
          <span className="text-xs text-gray-600 font-medium">
            ({card.value})
          </span>
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
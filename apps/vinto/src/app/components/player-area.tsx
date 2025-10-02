// components/PlayerArea.tsx - FIXED VERSION
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { Player } from '../shapes';
import { Card } from './card';
import { Avatar } from './avatar';

interface PlayerAreaProps {
  player: Player;
  isCurrentPlayer: boolean;
  isThinking: boolean;
  onCardClick?: (index: number) => void;
  gamePhase: 'setup' | 'playing' | 'final' | 'scoring';
  finalScores?: { [playerId: string]: number };
  isSelectingSwapPosition?: boolean;
  isDeclaringRank?: boolean;
  swapPosition?: number | null;
  isSelectingActionTarget?: boolean;
}

export const PlayerArea = observer(function PlayerArea({
  player,
  isCurrentPlayer,
  isThinking,
  onCardClick,
  gamePhase,
  finalScores,
  isSelectingSwapPosition = false,
  isDeclaringRank = false,
  swapPosition = null,
  isSelectingActionTarget = false,
}: PlayerAreaProps) {
  // Determine if we can see this player's cards based on official Vinto rules
  const canSeeCards = (cardIndex: number): boolean => {
    // During setup phase, human can see their own cards for memorization
    if (
      gamePhase === 'setup' &&
      player.isHuman &&
      (player.knownCardPositions.has(cardIndex) ||
        player.temporarilyVisibleCards.has(cardIndex))
    ) {
      return true;
    }

    // During gameplay, show temporarily visible cards (from actions like peek)
    // This works for both human and bot cards when they're being peeked
    if (
      (gamePhase === 'playing' || gamePhase === 'final') &&
      player.temporarilyVisibleCards.has(cardIndex)
    ) {
      return true;
    }

    // During scoring phase, ALL cards are revealed
    if (gamePhase === 'scoring') {
      return true;
    }

    return false;
  };



  // Dynamic card size based on number of cards
  const getCardSize = (): 'sm' | 'md' | 'lg' => {
    const cardCount = player.cards.length;
    if (cardCount > 7) return 'sm';
    if (cardCount > 5) return 'md';
    return player.isHuman ? 'lg' : 'md';
  };

  const cardContainerClasses = {
    bottom: 'flex flex-wrap gap-1 justify-center max-w-full',
    top: 'flex flex-wrap gap-1 justify-center max-w-full',
    left: 'flex flex-col flex-wrap gap-1 items-center max-h-full',
    right: 'flex flex-col flex-wrap gap-1 items-center max-h-full',
  };



  return (
    <div
      className={`flex items-center ${
        player.position === 'bottom' || player.position === 'top'
          ? 'flex-row gap-2 md:gap-4'
          : 'flex-col gap-1 md:gap-2'
      }`}
    >
      {/* Avatar comes BEFORE cards for bottom/left */}
      {(player.position === 'bottom' || player.position === 'left') && (
        <div className="flex flex-col items-center justify-center gap-1 md:gap-2">
          {/* Mobile: Combined avatar + name in rounded box */}
          <div
            className={`
              md:hidden
              flex items-center gap-2
              bg-white/95 backdrop-blur-sm
              px-2 py-1
              rounded-full
              shadow-lg border-2
              ${isCurrentPlayer ? 'border-orange-900' : 'border-gray-300'}
            `}
            style={isCurrentPlayer ? {
              animation: 'border-flash 1s ease-in-out infinite',
            } : undefined}
          >
            <div className="w-8 h-8">
              <Avatar player={player} size="sm" />
            </div>
            <div
              className={`
                text-xs
                font-extrabold
                ${isCurrentPlayer ? 'text-emerald-600' : 'text-gray-900'}
              `}
            >
              {player.name}
            </div>
          </div>

          {/* Desktop: Separate avatar and name */}
          <div className="hidden md:flex md:flex-col md:items-center md:justify-center md:gap-2">
            <div
              className={`
                w-32 h-32
                ${isCurrentPlayer ? 'scale-110' : 'scale-100'}
                transition-all duration-300
                drop-shadow-2xl
              `}
              style={{
                filter: isCurrentPlayer ? 'drop-shadow(0 0 10px rgba(16, 185, 129, 0.5))' : undefined
              }}
            >
              <Avatar player={player} size="lg" />
            </div>

            <div
              className={`
                text-lg
                font-extrabold
                ${isCurrentPlayer ? 'text-emerald-600' : 'text-gray-900'}
                bg-white/95 backdrop-blur-sm
                px-4 py-2
                rounded-full
                shadow-lg border-2
                ${isCurrentPlayer ? 'border-orange-900' : 'border-gray-300'}
              `}
              style={isCurrentPlayer ? {
                animation: 'border-flash 1s ease-in-out infinite',
              } : undefined}
            >
              {player.name}
            </div>
          </div>
        </div>
      )}

      {/* Cards container with responsive styling and wrapping */}
      <div
        className={`${cardContainerClasses[player.position]} ${
          isCurrentPlayer
            ? 'p-0.5 md:p-1 rounded md:rounded-lg border border-emerald-400 md:border-2 bg-emerald-400/10'
            : ''
        }`}
        style={
          isCurrentPlayer
            ? {
                animation: 'gentle-pulse 2s infinite',
              }
            : undefined
        }
      >
        {player.cards.map((card, index) => {
          const isSidePlayer = player.position === 'left' || player.position === 'right';
          return (
            <Card
              key={`${card.id}-${index}`}
              card={card}
              revealed={
                canSeeCards(index) && !(player.isHuman && isSelectingSwapPosition)
              }
              position={player.isHuman ? index + 1 : 0}
              size={getCardSize()}
              clickable={!!onCardClick}
              highlighted={
                (isSelectingSwapPosition && !isDeclaringRank) ||
                (isDeclaringRank && swapPosition === index) ||
                isSelectingActionTarget
              }
              botPeeking={player.highlightedCards.has(index)}
              onClick={() => onCardClick?.(index)}
              rotated={isSidePlayer}
              playerId={player.id}
              cardIndex={index}
            />
          );
        })}
      </div>

      {/* Avatar comes AFTER cards for top/right */}
      {(player.position === 'top' || player.position === 'right') && (
        <div className="flex flex-col items-center justify-center gap-1 md:gap-2">
          {/* Mobile: Combined avatar + name in rounded box */}
          <div
            className={`
              md:hidden
              flex items-center gap-2
              bg-white/95 backdrop-blur-sm
              px-2 py-1
              rounded-full
              shadow-lg border-2
              ${isCurrentPlayer ? 'border-orange-900' : 'border-gray-300'}
            `}
            style={isCurrentPlayer ? {
              animation: 'border-flash 1s ease-in-out infinite',
            } : undefined}
          >
            <div className="w-8 h-8">
              <Avatar player={player} size="sm" />
            </div>
            <div
              className={`
                text-xs
                font-extrabold
                ${isCurrentPlayer ? 'text-emerald-600' : 'text-gray-900'}
              `}
            >
              {player.name}
            </div>
          </div>

          {/* Desktop: Separate avatar and name */}
          <div className="hidden md:flex md:flex-col md:items-center md:justify-center md:gap-2">
            <div
              className={`
                w-32 h-32
                ${isCurrentPlayer ? 'scale-110' : 'scale-100'}
                transition-all duration-300
                drop-shadow-2xl
              `}
              style={{
                filter: isCurrentPlayer ? 'drop-shadow(0 0 10px rgba(16, 185, 129, 0.5))' : undefined
              }}
            >
              <Avatar player={player} size="lg" />
            </div>

            <div
              className={`
                text-lg
                font-extrabold
                ${isCurrentPlayer ? 'text-emerald-600' : 'text-gray-900'}
                bg-white/95 backdrop-blur-sm
                px-4 py-2
                rounded-full
                shadow-lg border-2
                ${isCurrentPlayer ? 'border-orange-900' : 'border-gray-300'}
              `}
              style={isCurrentPlayer ? {
                animation: 'border-flash 1s ease-in-out infinite',
              } : undefined}
            >
              {player.name}
            </div>
          </div>
        </div>
      )}
    </div>
  );
});

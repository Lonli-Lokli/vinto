// components/PlayerArea.tsx - FIXED VERSION
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { Player, Card as CardType } from '../shapes';
import { Card } from './card';

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

  // Extracted desktop info card for clarity and reuse
  const DesktopPlayerInfoCard: React.FC<{
    player: Player;
    isCurrentPlayer: boolean;
    isThinking: boolean;
    displayScore: string | null;
  }> = ({ player, isCurrentPlayer, isThinking, displayScore }) => (
    <div
      className={`
          bg-white/90 backdrop-blur-sm rounded-lg p-2 border-2
          ${
            isCurrentPlayer
              ? 'border-emerald-500 shadow-lg shadow-emerald-500/20 ring-2 ring-emerald-300'
              : 'border-gray-200'
          }
          transition-all duration-300
        `}
    >
      <div className="text-center">
        <div className="text-base mb-1">{player.avatar}</div>
        <div
          className={`text-xs font-medium ${
            isCurrentPlayer ? 'text-emerald-600' : 'text-gray-700'
          }`}
        >
          {player.name}
        </div>
        {displayScore && (
          <div className="text-xs text-gray-500 mt-1">{displayScore} pts</div>
        )}
        {isCurrentPlayer && (
          <div className="mt-1">
            {isThinking ? (
              <div className="animate-spin text-emerald-500 text-sm">⏳</div>
            ) : (
              <div className="text-emerald-500 animate-pulse text-sm">⭐</div>
            )}
          </div>
        )}
      </div>
    </div>
  );

  const getDisplayScore = (): string | null => {
    // During scoring phase, show real scores
    if (gamePhase === 'scoring' && finalScores) {
      return finalScores[player.id].toString();
    }
    return null;
  };

  const positionClasses = {
    bottom: 'flex-col items-center',
    top: 'flex-col-reverse items-center',
    left: 'flex-row-reverse items-center',
    right: 'flex-row items-center',
  };

  const cardContainerClasses = {
    bottom: 'flex gap-1',
    top: 'flex gap-1',
    left: 'flex flex-col gap-1',
    right: 'flex flex-col gap-1',
  };

  // Mobile info card was removed to save space and avoid visual clutter

  // Mobile: group info card and cards together, add spacing and show player name near border
  const MobilePlayerGroup: React.FC = () => {
    // helper to render a single card with orientation tweaks for mobile
    const renderMobileCard = (card: CardType | undefined, index: number) => {
      const isSidePlayer = player.position === 'left' || player.position === 'right';

      return (
        <Card
          key={`${card?.id ?? 'card'}-${index}`}
          card={card}
          revealed={
            canSeeCards(index) && !(player.isHuman && isSelectingSwapPosition)
          }
          position={player.isHuman ? index + 1 : 0}
          size="lg"
          clickable={!!onCardClick}
          highlighted={
            isSelectingSwapPosition ||
            (isDeclaringRank && swapPosition === index) ||
            isSelectingActionTarget
          }
          onClick={() => onCardClick?.(index)}
          rotated={isSidePlayer}
        />
      );
    };
    const nameLabel = (
      <div
        className={`md:hidden absolute z-10 flex items-center gap-1 text-2xs font-semibold leading-none whitespace-nowrap px-1.5 py-0.5 rounded-md bg-poker-green-900/60 text-white shadow-sm border border-white/20 ${
          player.position === 'left'
            ? '-rotate-90 left-0 top-1/2 -translate-y-1/2 -translate-x-2'
            : player.position === 'right'
            ? 'rotate-90 right-0 top-1/2 -translate-y-1/2 translate-x-2'
            : player.position === 'top'
            ? 'top-0 left-1/2 -translate-x-1/2 -translate-y-2'
            : player.position === 'bottom'
            ? 'bottom-0 left-1/2 -translate-x-1/2 translate-y-2'
            : ''
        }`}
        style={{ pointerEvents: 'none' }}
      >
        <span className="text-xs leading-none">{player.avatar}</span>
        <span className={isCurrentPlayer ? 'text-emerald-200' : 'text-white'}>
          {player.name}
        </span>
      </div>
    );

    // Add extra gap for mobile
    const isSide = player.position === 'left' || player.position === 'right';
    const wrapper = isSide
      ? 'md:hidden relative flex flex-row items-center w-full gap-3 my-1'
      : 'md:hidden relative flex flex-col items-center w-full gap-1';

    const mobileCardGap = isSide ? 'flex flex-col gap-2' : 'flex gap-2';

    return (
      <div className={wrapper}>
        {nameLabel}
        <div className={mobileCardGap}>
          {player.cards.map((card, index) => renderMobileCard(card, index))}
        </div>
      </div>
    );
  };

  return (
    <div
      className={`flex gap-2 ${positionClasses[player.position]} ${
        isCurrentPlayer
          ? 'p-1 rounded-lg border-2 border-emerald-400 bg-emerald-400/10'
          : ''
      }`}
    >
      {/* Mobile: Do NOT show player name & avatar */}

      {/* Desktop: Player Info Card in the middle */}
      <div className="hidden md:block">
        <DesktopPlayerInfoCard
          player={player}
          isCurrentPlayer={isCurrentPlayer}
          isThinking={isThinking}
          displayScore={getDisplayScore()}
        />
      </div>

      {/* Mobile: Cards and Info Card grouped by position */}
      <MobilePlayerGroup />

      {/* Desktop: Cards */}
      <div
        className={`hidden md:flex ${cardContainerClasses[player.position]}`}
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
              size="lg"
              clickable={!!onCardClick}
              highlighted={
                isSelectingSwapPosition ||
                (isDeclaringRank && swapPosition === index) ||
                isSelectingActionTarget
              }
              onClick={() => onCardClick?.(index)}
              rotated={isSidePlayer}
            />
          );
        })}
      </div>
    </div>
  );
});

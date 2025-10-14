// components/PlayerArea.tsx - MIGRATED VERSION
'use client';

import React from 'react';
import { observer } from 'mobx-react-lite';
import { Users, Crown } from 'lucide-react';
import { Avatar, Card } from './presentational';
import { useUIStore } from './di-provider';
import { PlayerState } from '@/shared';
import { useGameClient } from '@/client';

type PlayerPosition = 'bottom' | 'left' | 'top' | 'right';

interface PlayerAreaProps {
  player: PlayerState;
  position: PlayerPosition;
  isCurrentPlayer: boolean;
  isThinking: boolean;
  onCardClick?: (index: number) => void;
  gamePhase: 'setup' | 'playing' | 'final' | 'scoring';
  isSelectingSwapPosition?: boolean;
  isDeclaringRank?: boolean;
  swapPosition?: number | null;
  isSelectingActionTarget?: boolean;
}

export const PlayerArea = observer(function PlayerArea({
  player,
  position,
  isCurrentPlayer,
  onCardClick,
  gamePhase,
  isSelectingSwapPosition = false,
  isDeclaringRank = false,
  swapPosition = null,
  isSelectingActionTarget = false,
}: PlayerAreaProps) {
  // Get coalition leader status from GameClient
  const gameClient = useGameClient();
  const uiStore = useUIStore();
  const humanPlayer = gameClient.state.players.find((p) => p.isHuman);
  const coalitionLeader = gameClient.coalitionLeader;

  // Get UI state for this player
  const temporarilyVisibleCards = uiStore.getTemporarilyVisibleCards(player.id);
  const highlightedCards = uiStore.getHighlightedCards(player.id);

  // Check if this player is the coalition leader
  const isCoalitionLeader = coalitionLeader?.id === player.id;

  // Determine if we can see this player's cards based on official Vinto rules
  const canSeeCards = (cardIndex: number): boolean => {
    // During setup phase, human can see their own cards for memorization
    if (
      gamePhase === 'setup' &&
      player.isHuman &&
      (player.knownCardPositions.includes(cardIndex) ||
        temporarilyVisibleCards.has(cardIndex))
    ) {
      return true;
    }

    // During gameplay, show temporarily visible cards (from actions like peek)
    // This works for both human and bot cards when they're being peeked
    if (
      (gamePhase === 'playing' || gamePhase === 'final') &&
      temporarilyVisibleCards.has(cardIndex)
    ) {
      return true;
    }

    // Coalition leader sees ALL coalition member cards during final phase
    if (
      (gamePhase === 'playing' || gamePhase === 'final') &&
      coalitionLeader &&
      humanPlayer &&
      coalitionLeader.id === humanPlayer.id &&
      player.coalitionWith.length > 0 &&
      !player.isVintoCaller
    ) {
      // Human is coalition leader and this player is a coalition member
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
        position === 'bottom' || position === 'top'
          ? 'flex-row gap-2 md:gap-4'
          : 'flex-col gap-1 md:gap-2'
      }`}
    >
      {/* Avatar comes BEFORE cards for bottom/left */}
      {(position === 'bottom' || position === 'left') && (
        <div className="flex flex-col items-center justify-center gap-1 md:gap-2">
          {/* Mobile: Combined avatar + name in rounded box */}
          <div
            className={`
              md:hidden
              flex items-center gap-2
              bg-surface-primary/95 backdrop-blur-sm
              px-2 py-1
              rounded-full
              shadow-theme-lg border-2
              ${isCurrentPlayer ? 'border-accent' : 'border-primary'}
            `}
            style={
              isCurrentPlayer
                ? {
                    animation: 'border-flash 1s ease-in-out infinite',
                  }
                : undefined
            }
          >
            <div className="w-8 h-8">
              <Avatar playerName={player.name} size="sm" />
            </div>
            <div
              className={`
                text-xs
                font-extrabold
                ${isCurrentPlayer ? 'text-success' : 'text-primary'}
              `}
            >
              {player.name}
            </div>
            {player.coalitionWith.length > 0 && (
              <div
                className="bg-info text-white rounded-full p-0.5"
                title="Coalition Member"
              >
                <Users size={10} />
              </div>
            )}
            {isCoalitionLeader && (
              <div
                className="bg-warning text-white rounded-full p-0.5"
                title="Coalition Leader"
              >
                <Crown size={10} />
              </div>
            )}
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
                filter: isCurrentPlayer
                  ? 'drop-shadow(0 0 10px rgba(16, 185, 129, 0.5))'
                  : undefined,
              }}
            >
              <Avatar playerName={player.name} size="lg" />
            </div>

            <div className="flex flex-col items-center gap-2">
              <div
                className={`
                  text-lg
                  font-extrabold
                  ${isCurrentPlayer ? 'text-success' : 'text-primary'}
                  bg-surface-primary/95 backdrop-blur-sm
                  px-4 py-2
                  rounded-full
                  shadow-theme-lg border-2
                  ${isCurrentPlayer ? 'border-accent' : 'border-primary'}
                `}
                style={
                  isCurrentPlayer
                    ? {
                        animation: 'border-flash 1s ease-in-out infinite',
                      }
                    : undefined
                }
              >
                {player.name}
              </div>
              {player.coalitionWith.length > 0 && (
                <div
                  className="flex items-center gap-1 bg-info text-white rounded-full px-3 py-1 text-sm font-semibold shadow-theme-md"
                  title="Coalition Member"
                >
                  <Users size={14} />
                  <span>Team</span>
                </div>
              )}
              {isCoalitionLeader && (
                <div
                  className="flex items-center gap-1 bg-warning text-white rounded-full px-3 py-1 text-sm font-semibold shadow-theme-md"
                  title="Coalition Leader"
                >
                  <Crown size={14} />
                  <span>Leader</span>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Cards container with responsive styling and wrapping */}
      <div
        className={`${cardContainerClasses[position]} ${
          isCurrentPlayer
            ? 'p-0.5 md:p-1 rounded md:rounded-lg border border-success md:border-2 bg-success-light/20'
            : ''
        } ${isSelectingActionTarget && !onCardClick ? 'area-dimmed' : ''}`}
        style={
          isCurrentPlayer
            ? {
                animation: 'gentle-pulse 2s infinite',
              }
            : undefined
        }
      >
        {player.cards.map((card, index) => {
          const isSidePlayer = position === 'left' || position === 'right';

          // Determine if this card is selectable
          const isCardSelectable =
            !!onCardClick &&
            ((isSelectingSwapPosition && !isDeclaringRank) ||
              (isDeclaringRank && swapPosition === index) ||
              isSelectingActionTarget);

          return (
            <Card
              key={`${card.id}-${index}`}
              card={card}
              revealed={
                canSeeCards(index) &&
                !(player.isHuman && isSelectingSwapPosition)
              }
              position={index + 1}
              size={getCardSize()}
              selectable={isCardSelectable}
              notSelectable={isSelectingActionTarget && !onCardClick}
              highlighted={
                (isSelectingSwapPosition && !isDeclaringRank) ||
                (isDeclaringRank && swapPosition === index) ||
                isSelectingActionTarget
              }
              botPeeking={highlightedCards.has(index)}
              onClick={() => onCardClick?.(index)}
              rotated={isSidePlayer}
              playerId={player.id}
              cardIndex={index}
            />
          );
        })}
      </div>

      {/* Avatar comes AFTER cards for top/right */}
      {(position === 'top' || position === 'right') && (
        <div className="flex flex-col items-center justify-center gap-1 md:gap-2">
          {/* Mobile: Combined avatar + name in rounded box */}
          <div
            className={`
              md:hidden
              flex items-center gap-2
              bg-surface-primary/95 backdrop-blur-sm
              px-2 py-1
              rounded-full
              shadow-theme-lg border-2
              ${isCurrentPlayer ? 'border-accent' : 'border-primary'}
            `}
            style={
              isCurrentPlayer
                ? {
                    animation: 'border-flash 1s ease-in-out infinite',
                  }
                : undefined
            }
          >
            <div className="w-8 h-8">
              <Avatar playerName={player.name} size="sm" />
            </div>
            <div
              className={`
                text-xs
                font-extrabold
                ${isCurrentPlayer ? 'text-success' : 'text-primary'}
              `}
            >
              {player.name}
            </div>
            {player.coalitionWith.length > 0 && (
              <div
                className="bg-info text-white rounded-full p-0.5"
                title="Coalition Member"
              >
                <Users size={10} />
              </div>
            )}
            {isCoalitionLeader && (
              <div
                className="bg-warning text-white rounded-full p-0.5"
                title="Coalition Leader"
              >
                <Crown size={10} />
              </div>
            )}
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
                filter: isCurrentPlayer
                  ? 'drop-shadow(0 0 10px rgba(16, 185, 129, 0.5))'
                  : undefined,
              }}
            >
              <Avatar playerName={player.name} size="lg" />
            </div>

            <div className="flex flex-col items-center gap-2">
              <div
                className={`
                  text-lg
                  font-extrabold
                  ${isCurrentPlayer ? 'text-success' : 'text-primary'}
                  bg-surface-primary/95 backdrop-blur-sm
                  px-4 py-2
                  rounded-full
                  shadow-theme-lg border-2
                  ${isCurrentPlayer ? 'border-accent' : 'border-primary'}
                `}
                style={
                  isCurrentPlayer
                    ? {
                        animation: 'border-flash 1s ease-in-out infinite',
                      }
                    : undefined
                }
              >
                {player.name}
              </div>
              {player.coalitionWith.length > 0 && (
                <div
                  className="flex items-center gap-1 bg-info text-white rounded-full px-3 py-1 text-sm font-semibold shadow-theme-md"
                  title="Coalition Member"
                >
                  <Users size={14} />
                  <span>Team</span>
                </div>
              )}
              {isCoalitionLeader && (
                <div
                  className="flex items-center gap-1 bg-warning text-white rounded-full px-3 py-1 text-sm font-semibold shadow-theme-md"
                  title="Coalition Leader"
                >
                  <Crown size={14} />
                  <span>Leader</span>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
});

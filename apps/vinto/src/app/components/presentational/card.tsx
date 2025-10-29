// components/Card.tsx
'use client';

import React, { FC } from 'react';
import { NeverError, Rank } from '@vinto/shapes';
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

/**
 * Card selection states:
 * - 'default': No interaction, not clickable
 * - 'selectable': Prominent pulsing animation (for action target selection)
 * - 'not-selectable': Dimmed/disabled state
 */
export type CardSelectionState = 'default' | 'selectable' | 'not-selectable';

/**
 * Card selection variant - determines the visual style of selection animation
 * - 'action': For selecting action targets (amber accent color)
 * - 'swap': For selecting card position during swap (blue info color)
 */
export type CardSelectionVariant = 'action' | 'swap';

/**
 * Card feedback intent - shows success/failure feedback for actions
 * - 'success': Shows success indicator (green checkmark)
 * - 'failure': Shows failure indicator (red X)
 */
export type CardIntent = 'success' | 'failure';

interface CardProps {
  rank?: Rank;
  revealed?: boolean;
  size?: CardSize;
  highlighted?: boolean;
  botPeeking?: boolean;
  onClick?: () => void;
  rotated?: boolean;
  // For animation tracking
  playerId?: string;
  cardIndex?: number; // The actual array index (0-based)
  isPending?: boolean;
  // Selection state - required for explicit control
  selectionState: CardSelectionState;
  // Selection variant - determines animation style
  selectionVariant?: CardSelectionVariant;
  // Intent feedback - shows success/failure indicator for actions (declarations, toss-ins, etc.)
  intent?: CardIntent;
  // Action target selected - for showing which cards are selected as action targets (Q, K, etc.)
  actionTargetSelected?: boolean;
  hidden?: boolean;
  // Disable flip animation - for cards that should not flip (drawn/discard pile)
  disableFlipAnimation?: boolean;
}

export function Card({
  rank,
  revealed = false,
  size = 'md',
  highlighted = false,
  botPeeking = false,
  onClick,
  rotated = false,
  playerId,
  cardIndex,
  isPending = false,
  selectionState,
  selectionVariant = 'action',
  intent,
  hidden = false,
  actionTargetSelected = false,
  disableFlipAnimation = false,
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
    // Pending card (drawn card waiting for swap) takes priority
    if (isPending) {
      return 'animate-pending-card-border';
    }

    switch (selectionState) {
      case 'not-selectable':
        return 'card-not-selectable';
      case 'selectable':
        // Use different animation based on selection variant
        const animationClass =
          selectionVariant === 'swap'
            ? 'animate-swap-select-border'
            : 'animate-card-select-pulse';
        return `card-selectable ${animationClass}`;
      case 'default':
        return '';
    }
  };

  // Determine intent feedback classes
  const getIntentFeedbackClasses = () => {
    if (intent === 'success') return 'declaration-correct';
    if (intent === 'failure') return 'declaration-incorrect';
    return '';
  };

  if (hidden) {
    // Render an empty, borderless, transparent box with same size
    return (
      <div
        className={`
          ${size === 'auto' ? 'w-full h-full' : CARD_SIZES[size]}
          flex items-center justify-center
          bg-transparent
        `}
        style={{ border: 'none', opacity: 0.2 }}
        aria-hidden="true"
      />
    );
  }

  return (
    <div
      className={`
        ${size === 'auto' ? 'w-full h-full' : CARD_SIZES[size]}
        relative select-none
        ${rotated ? 'transform-gpu card-rotated' : ''}
        ${getCardStateClasses()}
        ${getIntentFeedbackClasses()}
        ${actionTargetSelected ? 'action-target-selected' : ''}
      `}
      style={
        highlighted && selectionState !== 'selectable'
          ? {
              animation: 'ring-pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite',
            }
          : undefined
      }
      onClick={selectionState === 'selectable' ? onClick : undefined}
      {...dataAttributes}
    >
      {/* Flip card container with 3D perspective */}
      {disableFlipAnimation ? (
        // No flip animation - just show the revealed/unrevealed state directly
        <div className="w-full h-full">
          {revealed && rank ? (
            <RankComponent rank={rank} />
          ) : (
            <CardBackComponent botPeeking={botPeeking} />
          )}
        </div>
      ) : (
        // With flip animation
        <div className="flip-card-container">
          <div
            className={`flip-card-inner ${
              revealed && rank ? 'flip-card-revealed' : ''
            }`}
          >
            {/* Front side - Card face (shown when revealed) */}
            <div className="flip-card-front">
              {rank && <RankComponent rank={rank} />}
            </div>

            {/* Back side - Card back (shown when not revealed) */}
            <div className="flip-card-back">
              <CardBackComponent botPeeking={botPeeking} />
            </div>
          </div>
        </div>
      )}

      {/* Intent feedback overlay */}
      {intent && (
        <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
          {intent === 'failure' && (
            <div className="absolute inset-0 bg-error/20 border-2 border-error rounded animate-pulse" />
          )}
          <div
            className={`relative text-4xl font-bold drop-shadow-lg ${
              intent === 'success' ? 'text-success' : 'text-error'
            }`}
          >
            {intent === 'success' ? '✓' : '✗'}
          </div>
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
    botPeeking ? 'border-warning bg-card-revealed-gradient' : 'bg-card-gradient'
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
  const containerClassName = 'h-full w-auto rounded shadow-sm overflow-hidden';
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

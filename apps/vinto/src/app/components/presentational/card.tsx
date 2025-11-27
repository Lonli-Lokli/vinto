// components/Card.tsx

import React, { FC } from 'react';
import { Rank } from '@vinto/shapes';
import { CARD_SIZE_CONFIG, CardSize, RANK_IMAGE_MAP } from '../helpers';
import { Image_Cover } from './image';

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

export interface CardProps {
  rank?: Rank;
  revealed?: boolean;
  size?: CardSize;
  highlighted?: boolean;
  botPeeking?: boolean;
  isPeeked?: boolean; // New prop: card is currently being peeked (shows minimalistic border)
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
  // Bot knowledge indicator - shows if this card is known by bots (for final round UI)
  isBotKnown?: boolean;
}

export function Card({
  rank,
  revealed = false,
  size = 'md',
  highlighted = false,
  botPeeking = false,
  isPeeked = false,
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
  isBotKnown = false,
}: CardProps) {
  const config = CARD_SIZE_CONFIG[size];
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
    // Keep data attributes so position can be captured for animations
    return (
      <div
        className={`
           ${config.className}
          flex items-center justify-center
          bg-transparent
        `}
        style={{ border: 'none', opacity: 0 }}
        aria-hidden="true"
        {...dataAttributes}
      />
    );
  }

  return (
    <div
      className={`
          ${config.className}
        relative select-none
        ${rotated ? 'transform-gpu card-rotated' : ''}
        ${getCardStateClasses()}
        ${getIntentFeedbackClasses()}
        ${actionTargetSelected ? 'action-target-selected' : ''}
        ${isBotKnown ? 'bot-known-card' : ''}
      `}
      style={
        isPeeked
          ? {
              boxShadow:
                '0 0 0 3px rgba(59, 130, 246, 0.6), 0 0 20px rgba(59, 130, 246, 0.4)',
              outline: '3px solid rgb(96, 165, 250)',
              outlineOffset: '2px',
            }
          : isBotKnown
          ? {
              boxShadow:
                '0 0 0 2px rgba(234, 179, 8, 0.5), 0 0 15px rgba(234, 179, 8, 0.3)',
              outline: '2px solid rgb(250, 204, 21)',
              outlineOffset: '1px',
            }
          : highlighted && selectionState !== 'selectable'
          ? {
              animation: 'ring-pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite',
            }
          : undefined
      }
      {...dataAttributes}
    >
      {/* Flip card container with 3D perspective */}
      {disableFlipAnimation ? (
        // No flip animation - just show the revealed/unrevealed state directly
        <div className="w-full h-full">
          {revealed && rank ? (
            <RankComponent
              rank={rank}
              fill={config.fill}
              width={config.width}
              height={config.height}
              sizes={config.sizes}
            />
          ) : (
            <CardBackComponent
              botPeeking={botPeeking}
              fill={config.fill}
              width={config.width}
              height={config.height}
              sizes={config.sizes}
            />
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
              {rank && (
                <RankComponent
                  rank={rank}
                  fill={config.fill}
                  width={config.width}
                  height={config.height}
                  sizes={config.sizes}
                />
              )}
            </div>

            {/* Back side - Card back (shown when not revealed) */}
            <div className="flip-card-back">
              <CardBackComponent
                botPeeking={botPeeking}
                fill={config.fill}
                width={config.width}
                height={config.height}
                sizes={config.sizes}
              />
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

const CardBackComponent: FC<{
  botPeeking?: boolean;
  width?: number;
  height?: number;
  fill?: boolean;
  sizes?: string;
}> = ({ botPeeking = false, width, height, sizes, fill }) => {
  const containerClassName = `h-full w-auto rounded border shadow-theme-sm overflow-hidden ${
    botPeeking ? 'border-warning bg-card-revealed-gradient' : 'bg-card-gradient'
  }`;

  const imageClassName = 'h-full w-full object-cover';

  return (
    <div className={containerClassName}>
      <Image_Cover
        className={imageClassName}
        width={width}
        height={height}
        fill={fill}
        sizes={sizes}
        priority={true}
      />
    </div>
  );
};

const RankComponent: FC<{
  rank: Rank;
  width?: number;
  height?: number;
  fill?: boolean;
  sizes?: string;
}> = ({ rank, width, height, fill, sizes }) => {
  // Container has border and background, image fills it completely
  const containerClassName = 'h-full w-auto rounded shadow-sm overflow-hidden';
  const imageClassName = 'h-full w-full object-contain';

  const ImageComponent = RANK_IMAGE_MAP[rank];

  return (
    <div className={containerClassName}>
      {
        <ImageComponent
          className={imageClassName}
          width={width}
          height={height}
          sizes={sizes}
          fill={fill}
        />
      }
    </div>
  );
};

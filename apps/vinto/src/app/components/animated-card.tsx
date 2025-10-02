// components/animated-card.tsx
'use client';

import React, { useLayoutEffect, useState, useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { observer } from 'mobx-react-lite';
import * as Sentry from '@sentry/nextjs';
import { Card } from './card';
import {
  useCardAnimationStore,
  usePlayerStore,
  useActionStore,
} from './di-provider';

interface AnimatedCardData {
  id: string;
  card: any;
  fromX: number;
  fromY: number;
  toX: number;
  toY: number;
}

/**
 * Overlay component that renders animated cards during transitions
 * Uses Framer Motion for smooth, declarative animations
 */
export const AnimatedCardOverlay = observer(() => {
  const animationStore = useCardAnimationStore();
  const playerStore = usePlayerStore();
  const actionStore = useActionStore();
  const [animatedCards, setAnimatedCards] = useState<AnimatedCardData[]>([]);

  // Memoize animations array to prevent unnecessary recalculations
  const animations = useMemo(
    () => Array.from(animationStore.activeAnimations.values()),
    [animationStore.activeAnimations]
  );

  // Use layoutEffect to calculate positions synchronously after DOM updates
  useLayoutEffect(() => {
    if (animations.length === 0) {
      setAnimatedCards([]);
      return;
    }

    // Helper function to check if a rect is valid (not all zeros)
    const isValidRect = (rect: DOMRect): boolean => {
      return rect.width > 0 && rect.height > 0;
    };

    // Function to attempt to get valid positions with retries
    const calculatePositions = (attempt = 0): void => {
      const maxAttempts = 5;
      const cards: AnimatedCardData[] = [];
      let hasInvalidRects = false;

      animations.forEach((animation) => {
        // Get positions
        let fromEl: Element | null = null;
        let toEl: Element | null = null;

        if (animation.type === 'swap') {
          // From: pending card (if fromPosition is -1) or deck pile
          if (animation.fromPosition === -1) {
            fromEl = document.querySelector('[data-pending-card="true"]');
          }
          // If no pending card found or fromPosition is not -1, try deck pile
          if (!fromEl) {
            fromEl = document.querySelector('[data-deck-pile="true"]');
          }
          // To: player card slot
          toEl = document.querySelector(
            `[data-player-id="${animation.toPlayerId}"][data-card-position="${animation.toPosition}"]`
          );
        } else if (animation.type === 'discard') {
          // From: player card slot
          fromEl = document.querySelector(
            `[data-player-id="${animation.fromPlayerId}"][data-card-position="${animation.fromPosition}"]`
          );
          // To: discard pile
          toEl = document.querySelector('[data-discard-pile="true"]');
        }

        if (!fromEl || !toEl) {
          if (attempt === 0) {
            console.warn('Could not find elements for animation', {
              fromEl,
              toEl,
              animation,
            });
          }
          hasInvalidRects = true;
          return;
        }

        const fromRect = fromEl.getBoundingClientRect();
        const toRect = toEl.getBoundingClientRect();

        // Check if positions are valid
        if (!isValidRect(fromRect)) {
          if (attempt === 0) {
            console.warn('Invalid from position', fromRect, {
              element: fromEl,
              animation,
            });
          }
          hasInvalidRects = true;
          return;
        }
        if (!isValidRect(toRect)) {
          if (attempt === 0) {
            console.warn('Invalid to position', toRect, {
              element: toEl,
              animation,
            });
          }
          hasInvalidRects = true;
          return;
        }

        // Get the card to display
        let card;
        if (animation.type === 'swap') {
          if (animation.fromPosition === -1) {
            card = actionStore.pendingCard;
          } else if (animation.fromPlayerId && typeof animation.fromPosition === 'number') {
            const player = playerStore.getPlayer(animation.fromPlayerId);
            card = player?.cards[animation.fromPosition];
          }
        } else if (animation.type === 'discard') {
          card = animation.card;
        }

        if (!card) {
          console.warn('No card for animation', animation);
          return;
        }

        cards.push({
          id: animation.id,
          card,
          fromX: fromRect.left,
          fromY: fromRect.top,
          toX: toRect.left,
          toY: toRect.top,
        });
      });

      // If we have invalid rects and haven't exceeded max attempts, retry
      if (hasInvalidRects && attempt < maxAttempts) {
        requestAnimationFrame(() => calculatePositions(attempt + 1));
        return;
      }

      // Only set cards if we have valid positions or we've exhausted retries
      if (cards.length > 0) {
        setAnimatedCards(cards);
      } else if (attempt >= maxAttempts) {
        const errorMessage = 'Failed to get valid positions after max attempts';
        console.error(errorMessage, { animations, hasInvalidRects });

        // Log to Sentry with context
        Sentry.captureMessage(errorMessage, {
          level: 'warning',
          contexts: {
            animation: {
              attempts: maxAttempts,
              animationCount: animations.length,
              animations: animations.map(a => ({
                id: a.id,
                type: a.type,
                fromPlayerId: a.fromPlayerId,
                fromPosition: a.fromPosition,
                toPlayerId: a.toPlayerId,
                toPosition: a.toPosition,
              })),
            },
            dom: {
              deckPileExists: !!document.querySelector('[data-deck-pile="true"]'),
              discardPileExists: !!document.querySelector('[data-discard-pile="true"]'),
              pendingCardExists: !!document.querySelector('[data-pending-card="true"]'),
            },
          },
        });

        // Clear animations since we can't render them
        animations.forEach(anim => animationStore.removeAnimation(anim.id));
      }
    };

    // Start the calculation process
    calculatePositions();
  }, [animations, playerStore, actionStore, animationStore]);

  return (
    <div className="fixed inset-0 pointer-events-none" style={{ zIndex: 100 }}>
      <AnimatePresence mode="sync">
        {animatedCards.map((animData) => (
          <motion.div
            key={animData.id}
            initial={{
              x: animData.fromX,
              y: animData.fromY,
              scale: 1,
              rotate: 0,
              opacity: 1,
            }}
            animate={{
              x: animData.toX,
              y: animData.toY,
              scale: [1, 1.2, 1],
              rotate: [0, 10, -10, 0],
              opacity: 1,
            }}
            exit={{
              opacity: 0,
              scale: 0.8,
            }}
            transition={{
              duration: 1.5,
              ease: [0.43, 0.13, 0.23, 0.96],
              scale: {
                times: [0, 0.5, 1],
                duration: 1.5,
              },
              rotate: {
                times: [0, 0.3, 0.6, 1],
                duration: 1.5,
              },
            }}
            onAnimationComplete={() => {
              // Remove from store when animation completes
              animationStore.removeAnimation(animData.id);
            }}
            className="absolute"
            style={{
              filter: 'drop-shadow(0 20px 40px rgba(0,0,0,0.4))',
              zIndex: 101,
            }}
          >
            <Card
              card={animData.card}
              revealed={true}
              size="lg"
              clickable={false}
            />
          </motion.div>
        ))}
      </AnimatePresence>
    </div>
  );
});

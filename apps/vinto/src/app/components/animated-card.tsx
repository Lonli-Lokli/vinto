// components/animated-card.tsx
'use client';

import React, { useLayoutEffect, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { observer } from 'mobx-react-lite';
import { Card } from './card';
import { useCardAnimationStore, usePlayerStore, useActionStore } from './di-provider';

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

  const animations = Array.from(animationStore.activeAnimations.values());

  // Use layoutEffect to calculate positions synchronously after DOM updates
  useLayoutEffect(() => {
    if (animations.length === 0) {
      setAnimatedCards([]);
      return;
    }

    const cards: AnimatedCardData[] = [];

    animations.forEach((animation) => {
      // Get positions
      let fromEl: Element | null = null;
      let toEl: Element | null = null;

      if (animation.type === 'swap') {
        // From: deck or pending card
        fromEl = document.querySelector('[data-deck-pile="true"]');
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
        console.warn('Could not find elements for animation', { fromEl, toEl, animation });
        return;
      }

      const fromRect = fromEl.getBoundingClientRect();
      const toRect = toEl.getBoundingClientRect();

      // Check if positions are valid (not 0,0)
      if (fromRect.left === 0 && fromRect.top === 0 && fromRect.width === 0) {
        console.warn('Invalid from position', fromRect);
        return;
      }
      if (toRect.left === 0 && toRect.top === 0 && toRect.width === 0) {
        console.warn('Invalid to position', toRect);
        return;
      }

      // Get the card to display
      let card;
      if (animation.type === 'swap') {
        card = animation.fromPosition === -1
          ? actionStore.pendingCard
          : playerStore.getPlayer(animation.fromPlayerId!)?.cards[animation.fromPosition!];
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

    if (cards.length > 0) {
      setAnimatedCards(cards);
    }
  }, [animations, playerStore, actionStore]); // Recalculate when animations change

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
              opacity: 1
            }}
            animate={{
              x: animData.toX,
              y: animData.toY,
              scale: [1, 1.2, 1],
              rotate: [0, 10, -10, 0],
              opacity: 1
            }}
            exit={{
              opacity: 0,
              scale: 0.8
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
              }
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

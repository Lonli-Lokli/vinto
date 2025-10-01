// components/animated-card.tsx
'use client';

import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { observer } from 'mobx-react-lite';
import { Card } from './card';
import { useCardAnimationStore, usePlayerStore, useActionStore } from './di-provider';

/**
 * Overlay component that renders animated cards during transitions
 * Uses Framer Motion for smooth, declarative animations
 */
export const AnimatedCardOverlay = observer(() => {
  const animationStore = useCardAnimationStore();
  const playerStore = usePlayerStore();
  const actionStore = useActionStore();
  const [ready, setReady] = useState(false);

  const animations = Array.from(animationStore.activeAnimations.values());

  // Wait for layout before starting animations
  useEffect(() => {
    if (animations.length === 0) {
      setReady(false);
      return undefined;
    }

    if (!ready) {
      // Small delay to ensure DOM is laid out
      const timer = setTimeout(() => setReady(true), 50);
      return () => clearTimeout(timer);
    }

    return undefined;
  }, [animations.length, ready]);

  // Get position for a card element
  const getCardPosition = (playerId: string, position: number) => {
    if (position === -1) {
      // Deck/center position
      const deckElement = document.querySelector('[data-deck-pile="true"]');
      if (deckElement) {
        const rect = deckElement.getBoundingClientRect();
        return { x: rect.left, y: rect.top };
      }
      return { x: window.innerWidth / 2 - 40, y: window.innerHeight / 2 - 60 };
    }

    const selector = `[data-player-id="${playerId}"][data-card-position="${position}"]`;
    const element = document.querySelector(selector);

    if (element) {
      const rect = element.getBoundingClientRect();
      return { x: rect.left, y: rect.top };
    }

    return null;
  };

  // Get discard pile position
  const getDiscardPosition = () => {
    const discardElement = document.querySelector('[data-discard-pile="true"]');
    if (discardElement) {
      const rect = discardElement.getBoundingClientRect();
      return { x: rect.left, y: rect.top };
    }
    return { x: window.innerWidth / 2 + 100, y: window.innerHeight / 2 - 60 };
  };

  // Don't render until ready
  if (!ready || animations.length === 0) {
    return null;
  }

  return (
    <div className="fixed inset-0 pointer-events-none" style={{ zIndex: 100 }}>
      <AnimatePresence mode="sync">
        {animations.map((animation) => {
          // Calculate positions immediately (after initial render)
          const fromPos = animation.type === 'discard'
            ? getCardPosition(animation.fromPlayerId!, animation.fromPosition!)
            : getCardPosition(animation.fromPlayerId!, animation.fromPosition!);

          const toPos = animation.type === 'discard'
            ? getDiscardPosition()
            : getCardPosition(animation.toPlayerId!, animation.toPosition!);

          if (!fromPos || !toPos) {
            return null;
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

          if (!card) return null;

          return (
            <motion.div
              key={animation.id}
              initial={{
                x: fromPos.x,
                y: fromPos.y,
                scale: 1,
                rotate: 0,
                opacity: 1
              }}
              animate={{
                x: toPos.x,
                y: toPos.y,
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
                animationStore.removeAnimation(animation.id);
              }}
              className="absolute"
              style={{
                filter: 'drop-shadow(0 20px 40px rgba(0,0,0,0.4))',
                zIndex: 101,
              }}
            >
              <Card
                card={card}
                revealed={true}
                size="lg"
                clickable={false}
              />
            </motion.div>
          );
        })}
      </AnimatePresence>
    </div>
  );
});

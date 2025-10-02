// components/animated-card.tsx
'use client';

import React, { useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { observer } from 'mobx-react-lite';
import { Card } from './card';
import {
  useCardAnimationStore,
  usePlayerStore,
  useActionStore,
} from './di-provider';
import { Card as CardType } from '../shapes';

interface VirtualCard {
  id: string;
  card: CardType;
  fromX: number;
  fromY: number;
  toX: number;
  toY: number;
  revealed: boolean;
}

/**
 * Overlay component that renders animated cards during transitions
 * Uses completely virtual cards that are cloned and animated independently
 */
export const AnimatedCardOverlay = observer(() => {
  const animationStore = useCardAnimationStore();
  const playerStore = usePlayerStore();
  const actionStore = useActionStore();
  const [virtualCards, setVirtualCards] = useState<VirtualCard[]>([]);

  useEffect(() => {
    const animations = Array.from(animationStore.activeAnimations.values());

    if (animations.length === 0) {
      setVirtualCards([]);
      return;
    }

    console.log('[AnimatedCard] Processing animations:', animations.length);

    const cards: VirtualCard[] = [];

    animations.forEach((animation) => {
      // Skip if we don't have pre-captured positions or card
      if (
        animation.fromX === undefined ||
        animation.fromY === undefined ||
        animation.toX === undefined ||
        animation.toY === undefined
      ) {
        console.warn(
          '[AnimatedCard] Missing positions for animation:',
          animation.id
        );
        return;
      }

      if (!animation.card) {
        console.warn('[AnimatedCard] No card for animation:', animation.id);
        return;
      }

      cards.push({
        id: animation.id,
        card: animation.card,
        fromX: animation.fromX,
        fromY: animation.fromY,
        toX: animation.toX,
        toY: animation.toY,
        revealed: animation.revealed ?? true,
      });
    });

    console.log('[AnimatedCard] Created virtual cards:', cards.length);
    setVirtualCards(cards);
  }, [
    animationStore.hasActiveAnimations,
    playerStore,
    actionStore,
    animationStore.activeAnimations,
  ]);

  return (
    <div className="fixed inset-0 pointer-events-none" style={{ zIndex: 100 }}>
      <AnimatePresence>
        {virtualCards.map((virtualCard) => (
          <motion.div
            key={virtualCard.id}
            className="absolute"
            initial={{
              left: virtualCard.fromX,
              top: virtualCard.fromY,
              scale: 1,
              rotate: 0,
              opacity: 1,
            }}
            animate={{
              left: virtualCard.toX,
              top: virtualCard.toY,
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
              console.log('[AnimatedCard] Animation complete:', virtualCard.id);
              animationStore.removeAnimation(virtualCard.id);
            }}
            style={{
              filter: 'drop-shadow(0 20px 40px rgba(0,0,0,0.4))',
              zIndex: 101,
            }}
          >
            <Card
              card={virtualCard.card}
              revealed={virtualCard.revealed}
              size="lg"
              clickable={false}
            />
          </motion.div>
        ))}
      </AnimatePresence>
    </div>
  );
});

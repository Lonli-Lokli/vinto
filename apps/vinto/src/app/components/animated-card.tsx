// components/animated-card.tsx
'use client';

import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { observer } from 'mobx-react-lite';
import { Card } from './card';
import { useCardAnimationStore } from './di-provider';
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

  // Convert MobX observable directly into virtual cards
  // This will automatically re-render when activeAnimations changes
  const animations = Array.from(animationStore.activeAnimations.values());

  console.log('[AnimatedCard] Rendering with animations:', animations.length);

  const virtualCards: VirtualCard[] = [];

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

    virtualCards.push({
      id: animation.id,
      card: animation.card,
      fromX: animation.fromX,
      fromY: animation.fromY,
      toX: animation.toX,
      toY: animation.toY,
      revealed: animation.revealed ?? true,
    });
  });

  console.log('[AnimatedCard] Created virtual cards:', virtualCards.length);

  return (
    <div className="fixed inset-0 pointer-events-none" style={{ zIndex: 100 }}>
      <AnimatePresence>
        {virtualCards.map((virtualCard) => {
          const animation = animationStore.activeAnimations.get(virtualCard.id);
          const isHighlight = animation?.type === 'highlight';

          return (
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
              animate={
                isHighlight
                  ? {
                      scale: [1, 1.15, 1, 1.15, 1],
                      opacity: 1,
                    }
                  : {
                      left: virtualCard.toX,
                      top: virtualCard.toY,
                      scale: [1, 1.2, 1],
                      rotate: [0, 10, -10, 0],
                      opacity: 1,
                    }
              }
              exit={{
                opacity: 0,
                scale: 0.8,
              }}
              transition={
                isHighlight
                  ? {
                      duration: 2,
                      ease: 'easeInOut',
                      scale: {
                        times: [0, 0.25, 0.5, 0.75, 1],
                        duration: 2,
                      },
                    }
                  : {
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
                    }
              }
              onAnimationComplete={() => {
                console.log(
                  '[AnimatedCard] Animation complete:',
                  virtualCard.id
                );
                animationStore.removeAnimation(virtualCard.id);
              }}
              style={
                isHighlight
                  ? {
                      filter:
                        'drop-shadow(0 0 20px rgba(59, 130, 246, 0.8)) drop-shadow(0 0 40px rgba(59, 130, 246, 0.6))',
                      zIndex: 101,
                    }
                  : {
                      filter: 'drop-shadow(0 20px 40px rgba(0,0,0,0.4))',
                      zIndex: 101,
                    }
              }
            >
              <Card
                card={virtualCard.card}
                revealed={virtualCard.revealed}
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

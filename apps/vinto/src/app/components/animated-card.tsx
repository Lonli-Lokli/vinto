// components/animated-card.tsx
'use client';

import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { observer } from 'mobx-react-lite';
import { useCardAnimationStore } from './di-provider';
import { Rank } from '@vinto/shapes';
import { Card } from './presentational';

interface VirtualCard {
  id: string;
  rank: Rank;
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

    if (!animation.rank) {
      console.warn('[AnimatedCard] No card for animation:', animation.id);
      return;
    }

    virtualCards.push({
      id: animation.id,
      rank: animation.rank,
      fromX: animation.fromX,
      fromY: animation.fromY,
      toX: animation.toX,
      toY: animation.toY,
      revealed: animation.revealed ?? true,
    });
  });

  return (
    <div className="fixed inset-0 pointer-events-none" style={{ zIndex: 100 }}>
      <AnimatePresence>
        {virtualCards.map((virtualCard) => {
          const animation = animationStore.activeAnimations.get(virtualCard.id);
          const isHighlight = animation?.type === 'highlight';
          const isPlayAction = animation?.type === 'play-action';
          const isShake = animation?.type === 'shake';
          const hasFullRotation = animation?.fullRotation;
          const targetRotation = animation?.targetRotation ?? 0;

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
                  : isShake
                  ? {
                      x: [0, -10, 10, -10, 10, -5, 5, 0],
                      rotate: [0, -5, 5, -5, 5, -2, 2, 0],
                      opacity: 1,
                    }
                  : isPlayAction
                  ? {
                      left: virtualCard.toX,
                      top: virtualCard.toY,
                      scale: [1, 1.4, 1.3],
                      rotate: [0, 360, targetRotation],
                      opacity: 1,
                    }
                  : hasFullRotation
                  ? {
                      left: virtualCard.toX,
                      top: virtualCard.toY,
                      scale: [1, 1.2, 1],
                      rotate: [0, 360, targetRotation],
                      opacity: 1,
                    }
                  : {
                      left: virtualCard.toX,
                      top: virtualCard.toY,
                      scale: [1, 1.2, 1],
                      rotate: [0, 10, -10, targetRotation],
                      opacity: 1,
                    }
              }
              exit={{
                opacity: 0,
                scale: 0.8,
                rotate: targetRotation,
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
                  : isShake
                  ? {
                      duration: 0.8,
                      ease: 'easeInOut',
                      x: {
                        times: [0, 0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 1],
                        duration: 0.8,
                      },
                      rotate: {
                        times: [0, 0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 1],
                        duration: 0.8,
                      },
                    }
                  : isPlayAction
                  ? {
                      duration: 2,
                      ease: [0.34, 1.56, 0.64, 1],
                      scale: {
                        times: [0, 0.5, 1],
                        duration: 2,
                      },
                      rotate: {
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
                animationStore.removeAnimation(virtualCard.id);
              }}
              style={
                isHighlight
                  ? {
                      filter:
                        'drop-shadow(0 0 20px rgba(59, 130, 246, 0.8)) drop-shadow(0 0 40px rgba(59, 130, 246, 0.6))',
                      zIndex: 101,
                    }
                  : isShake
                  ? {
                      filter:
                        'drop-shadow(0 0 20px rgba(239, 68, 68, 0.8)) drop-shadow(0 0 40px rgba(239, 68, 68, 0.6))',
                      zIndex: 101,
                    }
                  : isPlayAction
                  ? {
                      filter:
                        'drop-shadow(0 0 30px rgba(34, 197, 94, 0.9)) drop-shadow(0 0 60px rgba(34, 197, 94, 0.7)) drop-shadow(0 10px 40px rgba(0,0,0,0.5))',
                      zIndex: 102,
                    }
                  : {
                      filter: 'drop-shadow(0 20px 40px rgba(0,0,0,0.4))',
                      zIndex: 101,
                    }
              }
            >
              <Card
                rank={virtualCard.rank}
                revealed={virtualCard.revealed}
                size="lg"
                selectionState="default"
              />
            </motion.div>
          );
        })}
      </AnimatePresence>
    </div>
  );
});

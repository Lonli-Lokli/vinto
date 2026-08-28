// components/penalty-indicator-overlay.tsx
'use client';

import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { observer } from 'mobx-react-lite';
import { useCardAnimationStore } from './di-provider';

/**
 * Overlay component that shows penalty indicators on player card areas
 * Used for Ace action to show which player will receive the penalty card
 * Displays a pulsing red border effect around the player's entire card area
 */
export const PenaltyIndicatorOverlay = observer(() => {
  const animationStore = useCardAnimationStore();

  // Find all penalty-indicator animations
  const penaltyIndicators = Array.from(
    animationStore.activeAnimations.values()
  ).filter((anim) => anim.type === 'penalty-indicator');

  if (penaltyIndicators.length === 0) {
    return null;
  }

  return (
    <div className="fixed inset-0 pointer-events-none" style={{ zIndex: 150 }}>
      <AnimatePresence>
        {penaltyIndicators.map((indicator) => {
          const targetPlayerId =
            indicator.to?.type === 'player' ? indicator.to.playerId : null;

          if (!targetPlayerId) return null;

          // Find the player's card area element
          const cardsElement = document.querySelector(
            `[data-player-cards="${targetPlayerId}"]`
          );

          if (!cardsElement) {
            console.warn(
              `[PenaltyIndicatorOverlay] Player cards element not found for ${targetPlayerId}`
            );
            return null;
          }

          const rect = cardsElement.getBoundingClientRect();

          return (
            <motion.div
              key={indicator.id}
              className="absolute rounded-lg"
              style={{
                left: rect.left - 12,
                top: rect.top - 12,
                width: rect.width + 24,
                height: rect.height + 24,
              }}
              initial={{
                opacity: 0,
                scale: 0.95,
              }}
              animate={{
                opacity: [0, 1, 0.8, 1, 0.8, 1, 0.6, 0],
                scale: [0.95, 1.02, 1, 1.02, 1, 1.02, 1, 0.98],
                boxShadow: [
                  '0 0 0 0px rgba(239, 68, 68, 0)',
                  '0 0 0 8px rgba(239, 68, 68, 0.8), 0 0 40px 12px rgba(239, 68, 68, 0.6)',
                  '0 0 0 4px rgba(239, 68, 68, 0.6), 0 0 30px 8px rgba(239, 68, 68, 0.4)',
                  '0 0 0 8px rgba(239, 68, 68, 0.8), 0 0 40px 12px rgba(239, 68, 68, 0.6)',
                  '0 0 0 4px rgba(239, 68, 68, 0.6), 0 0 30px 8px rgba(239, 68, 68, 0.4)',
                  '0 0 0 8px rgba(239, 68, 68, 0.8), 0 0 40px 12px rgba(239, 68, 68, 0.6)',
                  '0 0 0 4px rgba(239, 68, 68, 0.4), 0 0 20px 6px rgba(239, 68, 68, 0.3)',
                  '0 0 0 0px rgba(239, 68, 68, 0)',
                ],
              }}
              exit={{
                opacity: 0,
                scale: 0.98,
              }}
              transition={{
                duration: 1.5,
                ease: 'easeInOut',
                times: [0, 0.1, 0.25, 0.4, 0.55, 0.7, 0.85, 1],
              }}
              onAnimationComplete={() => {
                animationStore.removeAnimation(indicator.id);
              }}
            >
              {/* Red pulsing border overlay */}
              <div
                className="absolute inset-0 rounded-lg border-4 border-error"
                style={{
                  background:
                    'radial-gradient(ellipse at center, rgba(239, 68, 68, 0.15) 0%, rgba(239, 68, 68, 0.05) 50%, transparent 70%)',
                }}
              />
              
              {/* Corner flashes for extra impact */}
              <motion.div
                className="absolute -top-2 -left-2 w-6 h-6 bg-error rounded-full"
                animate={{
                  opacity: [0, 1, 0, 1, 0],
                  scale: [0.5, 1, 0.5, 1, 0.5],
                }}
                transition={{
                  duration: 1.5,
                  times: [0, 0.2, 0.4, 0.6, 1],
                }}
              />
              <motion.div
                className="absolute -top-2 -right-2 w-6 h-6 bg-error rounded-full"
                animate={{
                  opacity: [0, 1, 0, 1, 0],
                  scale: [0.5, 1, 0.5, 1, 0.5],
                }}
                transition={{
                  duration: 1.5,
                  times: [0, 0.2, 0.4, 0.6, 1],
                  delay: 0.1,
                }}
              />
              <motion.div
                className="absolute -bottom-2 -left-2 w-6 h-6 bg-error rounded-full"
                animate={{
                  opacity: [0, 1, 0, 1, 0],
                  scale: [0.5, 1, 0.5, 1, 0.5],
                }}
                transition={{
                  duration: 1.5,
                  times: [0, 0.2, 0.4, 0.6, 1],
                  delay: 0.2,
                }}
              />
              <motion.div
                className="absolute -bottom-2 -right-2 w-6 h-6 bg-error rounded-full"
                animate={{
                  opacity: [0, 1, 0, 1, 0],
                  scale: [0.5, 1, 0.5, 1, 0.5],
                }}
                transition={{
                  duration: 1.5,
                  times: [0, 0.2, 0.4, 0.6, 1],
                  delay: 0.3,
                }}
              />
            </motion.div>
          );
        })}
      </AnimatePresence>
    </div>
  );
});

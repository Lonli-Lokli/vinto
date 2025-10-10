// stores/card-animation-store.ts
/**
 * Store for managing card animations
 * Provides a centralized system for coordinating card movements and effects
 */

import { makeObservable, observable, action, computed } from 'mobx';
import { inject, injectable } from 'tsyringe';
import { AnimationPositionCapture } from '../services/animation-position-capture';
import { Card } from '@/shared';

export type AnimationType =
  | 'swap'
  | 'draw'
  | 'discard'
  | 'peek'
  | 'toss-in'
  | 'highlight'
  | 'play-action';

export type AnimationActor = 'draw' | 'discard' | 'drawn' | 'player';
export type AnimationDrawTarget = {
  type: 'draw';
};
export type AnimationDiscardTarget = {
  type: 'discard';
};
export type AnimationPlayerTarget = {
  type: 'player';
  playerId: string;
  position: number;
};
export type AnimationDrawnTarget = {
  type: 'drawn';
};

export type AnimationTarget =
  | AnimationDrawTarget
  | AnimationDiscardTarget
  | AnimationPlayerTarget
  | AnimationDrawnTarget;

export interface CardAnimationState {
  id: string;
  type: AnimationType;
  card?: Card;
  // Pre-captured positions (for immediate measurement)
  fromX?: number;
  fromY?: number;
  toX?: number;
  toY?: number;
  // Animation timing
  startTime: number;
  duration: number;
  // Reveal card during animation
  revealed?: boolean;
  // Full 360 rotation for bot moves
  fullRotation?: boolean;
  // Target rotation angle (0 for normal, 90 for left/right players)
  targetRotation?: number;
  // Status
  completed: boolean;
}

@injectable()
export class CardAnimationStore {
  activeAnimations: Map<string, CardAnimationState> = new Map();
  private animationCounter = 0;
  private positionCapture: AnimationPositionCapture;

  constructor(
    @inject(AnimationPositionCapture) positionCapture: AnimationPositionCapture
  ) {
    this.positionCapture = positionCapture;

    makeObservable(this, {
      activeAnimations: observable,
      hasActiveAnimations: computed,
      startSwapAnimation: action,
      startDrawAnimation: action,
      startDiscardAnimation: action,
      startPlayActionAnimation: action,
      startHighlightAnimation: action,
      removeAnimation: action,
      reset: action,
    });
  }

  /**
   * Get the target rotation for a player based on their position
   * Returns 90 for left/right players, 0 for top/bottom
   *
   * Note: playerPosition should be passed from GameState
   */
  private getTargetRotation(
    target: AnimationTarget,
    playerPosition?: string
  ): number {
    if (target.type === 'player' && playerPosition) {
      if (playerPosition === 'left' || playerPosition === 'right') {
        return 90;
      }
    }
    return 0;
  }

  get hasActiveAnimations(): boolean {
    return this.activeAnimations.size > 0;
  }

  /**
   * Start a swap animation - card moving from one position to another
   * revealed: whether to show the card face during animation (default: true)
   * targetPlayerPosition: position of target player ('left', 'right', 'top', 'bottom') for rotation
   */
  startSwapAnimation(
    card: Card,
    from: AnimationTarget,
    to: AnimationTarget,
    duration = 1500,
    revealed: boolean,
    targetPlayerPosition?: string
  ): string {
    const id = `swap-${this.animationCounter++}`;

    // Capture positions immediately
    const fromPos =
      from.type === 'drawn'
        ? this.positionCapture.getPendingCardPosition()
        : from.type === 'discard'
        ? this.positionCapture.getDiscardPilePosition()
        : from.type === 'draw'
        ? this.positionCapture.getDeckPilePosition()
        : this.positionCapture.getPlayerCardPosition(
            from.playerId,
            from.position
          );

    const toPos =
      to.type === 'drawn'
        ? this.positionCapture.getPendingCardPosition()
        : to.type === 'discard'
        ? this.positionCapture.getDiscardPilePosition()
        : to.type === 'draw'
        ? this.positionCapture.getDeckPilePosition()
        : this.positionCapture.getPlayerCardPosition(to.playerId, to.position);

    if (!fromPos || !toPos) {
      console.warn(
        '[CardAnimationStore] Could not capture positions for swap animation'
      );
      return id; // Return id but don't add to animations
    }

    const animation = {
      id,
      type: 'swap' as const,
      card,
      fromX: fromPos.x,
      fromY: fromPos.y,
      toX: toPos.x,
      toY: toPos.y,
      startTime: Date.now(),
      duration,
      revealed,
      targetRotation: this.getTargetRotation(to, targetPlayerPosition),
      completed: false,
    };

    this.activeAnimations.set(id, animation);
    return id;
  }

  /**
   * Start a draw animation from deck/drawn position to player
   * targetPlayerPosition: position of target player for rotation
   */
  startDrawAnimation(
    card: Card,
    from: AnimationDrawTarget | AnimationDrawnTarget,
    to: AnimationPlayerTarget | AnimationDrawnTarget,
    duration = 1500,
    revealed = true,
    fullRotation = false,
    targetPlayerPosition?: string
  ): string {
    const id = `draw-${this.animationCounter++}`;

    // Capture positions immediately
    const fromPos =
      from.type === 'drawn'
        ? this.positionCapture.getPendingCardPosition()
        : this.positionCapture.getDeckPilePosition();
    const toPos =
      to.type === 'drawn'
        ? this.positionCapture.getPendingCardPosition()
        : this.positionCapture.getPlayerCardPosition(to.playerId, to.position);

    if (!fromPos || !toPos) {
      console.warn(
        '[CardAnimationStore] Could not capture positions for draw animation'
      );
      return id; // Return id but don't add to animations
    }

    this.activeAnimations.set(id, {
      id,
      type: 'draw',
      card,
      fromX: fromPos.x,
      fromY: fromPos.y,
      toX: toPos.x,
      toY: toPos.y,
      startTime: Date.now(),
      duration,
      revealed,
      fullRotation,
      targetRotation: this.getTargetRotation(to, targetPlayerPosition),
      completed: false,
    });

    return id;
  }

  /**
   * Start a discard animation from player/drawn to discard pile
   */
  startDiscardAnimation(
    card: Card,
    from: AnimationPlayerTarget | AnimationDrawnTarget,
    to: AnimationDiscardTarget,
    duration = 1500
  ): string {
    const id = `discard-${this.animationCounter++}`;

    // Capture positions immediately
    const fromPos =
      from.type === 'drawn'
        ? this.positionCapture.getPendingCardPosition()
        : this.positionCapture.getPlayerCardPosition(
            from.playerId,
            from.position
          );
    const toPos = this.positionCapture.getDiscardPilePosition();

    if (!fromPos || !toPos) {
      console.warn(
        '[CardAnimationStore] Could not capture positions for discard animation'
      );
      return id; // Return id but don't add to animations
    }

    this.activeAnimations.set(id, {
      id,
      type: 'discard',
      card,
      fromX: fromPos.x,
      fromY: fromPos.y,
      toX: toPos.x,
      toY: toPos.y,
      startTime: Date.now(),
      duration,
      revealed: true, // Discard animations always show the card
      completed: false,
    });

    return id;
  }

  /**
   * Start a play-action animation - card moves to center, shows action effect, then to discard
   * This is a two-stage animation:
   * 1. Move to center of table with glow effect
   * 2. Move from center to discard pile
   */
  startPlayActionAnimation(
    card: Card,
    from: AnimationPlayerTarget | AnimationDrawnTarget,
    duration = 2000
  ): string {
    const id = `play-action-${this.animationCounter++}`;

    // Capture positions immediately
    const fromPos =
      from.type === 'drawn'
        ? this.positionCapture.getPendingCardPosition()
        : this.positionCapture.getPlayerCardPosition(
            from.playerId,
            from.position
          );

    // For play-action, the "to" position is the center of the viewport
    // We'll calculate center dynamically in the component
    const toPos = {
      x: typeof window !== 'undefined' ? window.innerWidth / 2 - 50 : 0,
      y: typeof window !== 'undefined' ? window.innerHeight / 2 - 70 : 0,
    };

    if (!fromPos) {
      console.warn(
        '[CardAnimationStore] Could not capture positions for play-action animation'
      );
      return id; // Return id but don't add to animations
    }

    this.activeAnimations.set(id, {
      id,
      type: 'play-action',
      card,
      fromX: fromPos.x,
      fromY: fromPos.y,
      toX: toPos.x,
      toY: toPos.y,
      startTime: Date.now(),
      duration,
      revealed: true, // Always show the card during play-action
      completed: false,
    });

    return id;
  }

  /**
   * Start a highlight animation - card pulses at its current position
   */
  startHighlightAnimation(
    card: Card,
    target: AnimationPlayerTarget,
    duration = 2000
  ): string {
    const id = `highlight-${this.animationCounter++}`;

    // Capture position
    const pos = this.positionCapture.getPlayerCardPosition(
      target.playerId,
      target.position
    );

    if (!pos) {
      console.warn(
        '[CardAnimationStore] Could not capture position for highlight animation'
      );
      return id;
    }

    this.activeAnimations.set(id, {
      id,
      type: 'highlight',
      card,
      fromX: pos.x,
      fromY: pos.y,
      toX: pos.x,
      toY: pos.y,
      startTime: Date.now(),
      duration,
      revealed: false,
      completed: false,
    });

    return id;
  }

  /**
   * Remove an animation immediately
   */
  removeAnimation(id: string) {
    this.activeAnimations.delete(id);
  }

  /**
   * Wait for a specific animation to complete
   */
  async waitForAnimation(id: string, timeoutMs = 2000): Promise<void> {
    if (!this.activeAnimations.has(id)) {
      return; // Animation already completed or never started
    }

    return new Promise((resolve) => {
      const checkInterval = 50;
      let elapsed = 0;

      const check = () => {
        if (!this.activeAnimations.has(id) || elapsed >= timeoutMs) {
          resolve();
        } else {
          elapsed += checkInterval;
          setTimeout(check, checkInterval);
        }
      };

      check();
    });
  }

  /**
   * Wait for all active animations to complete
   */
  async waitForAllAnimations(timeoutMs = 2000): Promise<void> {
    if (this.activeAnimations.size === 0) {
      return;
    }

    const animationIds = Array.from(this.activeAnimations.keys());
    await Promise.all(
      animationIds.map((id) => this.waitForAnimation(id, timeoutMs))
    );
  }

  /**
   * Reset all animations
   */
  reset() {
    this.activeAnimations.clear();
    this.animationCounter = 0;
  }
}

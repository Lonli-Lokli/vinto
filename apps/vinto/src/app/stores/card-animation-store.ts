// stores/card-animation-store.ts
/**
 * Store for managing card animations
 * Provides a centralized system for coordinating card movements and effects
 */

import { makeObservable, observable, action, computed } from 'mobx';
import { inject, injectable } from 'tsyringe';
import { Card } from '../shapes';
import { AnimationPositionCapture } from '../services/animation-position-capture';

export type AnimationType = 'swap' | 'draw' | 'discard' | 'peek' | 'toss-in';

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
      removeAnimation: action,
      reset: action,
    });
  }

  get hasActiveAnimations(): boolean {
    return this.activeAnimations.size > 0;
  }

  /**
   * Start a swap animation - card moving from one position to another
   * revealed: whether to show the card face during animation (default: true)
   */
  startSwapAnimation(
    card: Card,
    from: AnimationTarget,
    to: AnimationTarget,
    duration = 1500,
    revealed: boolean
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
      completed: false,
    };

    console.log('[CardAnimationStore] Starting swap animation:', animation);
    this.activeAnimations.set(id, animation);
    console.log(
      '[CardAnimationStore] Active animations count:',
      this.activeAnimations.size
    );
    return id;
  }

  /**
   * Start a draw animation from deck/drawn position to player
   */
  startDrawAnimation(
    card: Card,
    from: AnimationDrawTarget | AnimationDrawnTarget,
    to: AnimationPlayerTarget,
    duration = 1500,
    revealed = true
  ): string {
    const id = `draw-${this.animationCounter++}`;

    // Capture positions immediately
    const fromPos =
      from.type === 'drawn'
        ? this.positionCapture.getPendingCardPosition()
        : this.positionCapture.getDeckPilePosition();
    const toPos = this.positionCapture.getPlayerCardPosition(
      to.playerId,
      to.position
    );

    console.log('[CardAnimationStore] Draw animation positions:', {
      id,
      fromPos,
      toPos,
      from,
      to,
    });

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
      completed: false,
    });

    console.log('[CardAnimationStore] Starting draw animation:', id);
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

    console.log('[CardAnimationStore] Discard animation positions:', {
      id,
      fromPos,
      toPos,
      from,
      to,
    });

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

    console.log('[CardAnimationStore] Starting discard animation:', id);
    return id;
  }

  /**
   * Remove an animation immediately
   */
  removeAnimation(id: string) {
    console.log('[CardAnimationStore] Removing animation:', id);
    this.activeAnimations.delete(id);
    console.log(
      '[CardAnimationStore] Remaining animations:',
      this.activeAnimations.size
    );
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

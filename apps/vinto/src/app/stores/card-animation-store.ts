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

export interface CardAnimationState {
  id: string;
  type: AnimationType;
  card?: Card;
  fromPlayerId?: string;
  fromPosition?: number;
  toPlayerId?: string;
  toPosition?: number;
  // For drawing/discarding
  fromDeck?: boolean;
  toDeck?: boolean;
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
   * fromPosition: -1 means from pending card, otherwise from player's hand
   * revealed: whether to show the card face during animation (default: true)
   */
  startSwapAnimation(
    card: Card,
    fromPlayerId: string,
    fromPosition: number,
    toPlayerId: string,
    toPosition: number,
    duration = 1500,
    revealed = true
  ): string {
    const id = `swap-${this.animationCounter++}`;

    // Capture positions immediately
    const fromPos =
      fromPosition === -1
        ? this.positionCapture.getPendingCardPosition()
        : this.positionCapture.getPlayerCardPosition(
            fromPlayerId,
            fromPosition
          );

    const toPos = this.positionCapture.getPlayerCardPosition(
      toPlayerId,
      toPosition
    );

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
      fromPlayerId,
      fromPosition,
      toPlayerId,
      toPosition,
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
   * Start a draw animation from deck to player
   */
  startDrawAnimation(
    card: Card,
    toPlayerId: string,
    toPosition: number,
    duration = 1500,
    revealed = true
  ): string {
    const id = `draw-${this.animationCounter++}`;

    // Capture positions immediately
    const fromPos = this.positionCapture.getDeckPilePosition();
    const toPos = this.positionCapture.getPlayerCardPosition(
      toPlayerId,
      toPosition
    );

    console.log('[CardAnimationStore] Draw animation positions:', {
      id,
      fromPos,
      toPos,
      toPlayerId,
      toPosition,
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
      fromDeck: true,
      toPlayerId,
      toPosition,
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
   * Start a discard animation from player to discard pile
   */
  startDiscardAnimation(
    card: Card,
    fromPlayerId: string,
    fromPosition: number,
    duration = 1500
  ): string {
    const id = `discard-${this.animationCounter++}`;

    // Capture positions immediately
    const fromPos = this.positionCapture.getPlayerCardPosition(
      fromPlayerId,
      fromPosition
    );
    const toPos = this.positionCapture.getDiscardPilePosition();

    console.log('[CardAnimationStore] Discard animation positions:', {
      id,
      fromPos,
      toPos,
      fromPlayerId,
      fromPosition,
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
      fromPlayerId,
      fromPosition,
      toDeck: true,
      fromX: fromPos.x,
      fromY: fromPos.y,
      toX: toPos.x,
      toY: toPos.y,
      startTime: Date.now(),
      duration,
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
   * Get animation state for a specific card position
   */
  getAnimationForPosition(
    playerId: string,
    position: number
  ): CardAnimationState | undefined {
    return Array.from(this.activeAnimations.values()).find(
      (anim) =>
        (anim.fromPlayerId === playerId && anim.fromPosition === position) ||
        (anim.toPlayerId === playerId && anim.toPosition === position)
    );
  }

  /**
   * Check if a specific position is currently animating
   */
  isPositionAnimating(playerId: string, position: number): boolean {
    return this.getAnimationForPosition(playerId, position) !== undefined;
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

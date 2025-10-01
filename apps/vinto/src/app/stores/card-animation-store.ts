// stores/card-animation-store.ts
/**
 * Store for managing card animations
 * Provides a centralized system for coordinating card movements and effects
 */

import { makeObservable, observable, action, computed } from 'mobx';
import { injectable } from 'tsyringe';
import { Card } from '../shapes';

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
  // Animation timing
  startTime: number;
  duration: number;
  // Status
  completed: boolean;
}

@injectable()
export class CardAnimationStore {
  activeAnimations: Map<string, CardAnimationState> = new Map();
  private animationCounter = 0;

  constructor() {
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
   * Start a swap animation between two cards
   * Framer Motion handles the animation timing and calls removeAnimation when done
   */
  startSwapAnimation(
    fromPlayerId: string,
    fromPosition: number,
    toPlayerId: string,
    toPosition: number,
    duration = 1500
  ): string {
    const id = `swap-${this.animationCounter++}`;

    const animation = {
      id,
      type: 'swap' as const,
      fromPlayerId,
      fromPosition,
      toPlayerId,
      toPosition,
      startTime: Date.now(),
      duration,
      completed: false,
    };

    this.activeAnimations.set(id, animation);
    return id;
  }

  /**
   * Start a draw animation from deck to player
   */
  startDrawAnimation(
    card: Card,
    toPlayerId: string,
    toPosition: number,
    duration = 1500
  ): string {
    const id = `draw-${this.animationCounter++}`;

    this.activeAnimations.set(id, {
      id,
      type: 'draw',
      card,
      fromDeck: true,
      toPlayerId,
      toPosition,
      startTime: Date.now(),
      duration,
      completed: false,
    });

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

    this.activeAnimations.set(id, {
      id,
      type: 'discard',
      card,
      fromPlayerId,
      fromPosition,
      toDeck: true,
      startTime: Date.now(),
      duration,
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
   * Get animation state for a specific card position
   */
  getAnimationForPosition(playerId: string, position: number): CardAnimationState | undefined {
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
   * Reset all animations
   */
  reset() {
    this.activeAnimations.clear();
    this.animationCounter = 0;
  }
}

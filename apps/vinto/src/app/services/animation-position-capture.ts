// services/animation-position-capture.ts
/**
 * Service responsible for capturing DOM element positions for animations
 * Decouples position measurement from animation logic
 */

import { injectable } from 'tsyringe';

export interface Position {
  x: number;
  y: number;
}

@injectable()
export class AnimationPositionCapture {
  /**
   * Get position of pending/drawn card
   */
  getPendingCardPosition(): Position | null {
    const el = document.querySelector('[data-pending-card="true"]');
    if (!el) {
      console.warn('[AnimationPositionCapture] Pending card element not found');
      return null;
    }
    const rect = el.getBoundingClientRect();
    return { x: rect.left, y: rect.top };
  }

  /**
   * Get position of a player's card slot
   */
  getPlayerCardPosition(playerId: string, position: number): Position | null {
    const selector = `[data-player-id="${playerId}"][data-card-position="${position}"]`;
    const el = document.querySelector(selector);

    if (!el) {
      console.warn('[AnimationPositionCapture] Player card slot not found:', {
        playerId,
        position,
        selector,
      });
      return null;
    }

    const rect = el.getBoundingClientRect();
    return { x: rect.left, y: rect.top };
  }

  /**
   * Get position of discard pile
   */
  getDiscardPilePosition(): Position | null {
    const el = document.querySelector('[data-discard-pile="true"]');
    if (!el) {
      console.warn('[AnimationPositionCapture] Discard pile element not found');
      return null;
    }

    const rect = el.getBoundingClientRect();
    return { x: rect.left, y: rect.top };
  }

  /**
   * Get position of deck pile
   */
  getDeckPilePosition(): Position | null {
    const el = document.querySelector('[data-deck-pile="true"]');
    if (!el) {
      console.warn('[AnimationPositionCapture] Deck pile element not found');
      return null;
    }

    const rect = el.getBoundingClientRect();
    return { x: rect.left, y: rect.top };
  }
}

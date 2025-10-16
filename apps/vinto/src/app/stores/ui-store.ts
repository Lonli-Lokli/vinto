import { makeAutoObservable } from 'mobx';
import { injectable } from 'tsyringe';

/**
 * UIStore - Manages UI-specific state that doesn't belong in the game engine
 *
 * This store handles:
 * - Temporary card visibility during peek actions
 * - Highlighted cards during bot actions
 * - Modal/confirmation states
 * - Other ephemeral UI state
 */
@injectable()
export class UIStore {
  // Modal states
  showVintoConfirmation = false;
  showCoalitionLeaderSelection = false;

  // Swap selection state (UI-only, not part of game state)
  isSelectingSwapPosition = false;
  selectedSwapPosition: number | null = null;

  // Card visibility states (per player)
  // Map<playerId, Set<cardPosition>>
  temporarilyVisibleCards = new Map<string, Set<number>>();
  highlightedCards = new Map<string, Set<number>>();

  constructor() {
    makeAutoObservable(this);
  }

  // Modal actions
  setShowVintoConfirmation(value: boolean) {
    this.showVintoConfirmation = value;
  }

  setShowCoalitionLeaderSelection(value: boolean) {
    this.showCoalitionLeaderSelection = value;
  }

  openCoalitionLeaderSelection() {
    this.showCoalitionLeaderSelection = true;
  }

  closeCoalitionLeaderSelection() {
    this.showCoalitionLeaderSelection = false;
  }

  // Swap selection actions
  startSelectingSwapPosition() {
    this.isSelectingSwapPosition = true;
    this.selectedSwapPosition = null;
  }

  setSelectedSwapPosition(position: number) {
    this.selectedSwapPosition = position;
  }

  cancelSwapSelection() {
    this.isSelectingSwapPosition = false;
    this.selectedSwapPosition = null;
  }

  // Card visibility actions
  addTemporarilyVisibleCard(playerId: string, position: number) {
    if (!this.temporarilyVisibleCards.has(playerId)) {
      this.temporarilyVisibleCards.set(playerId, new Set());
    }
    this.temporarilyVisibleCards.get(playerId)?.add(position);
  }

  clearTemporaryCardVisibility() {
    this.temporarilyVisibleCards.clear();
  }

  clearTemporaryCardVisibilityForPlayer(playerId: string) {
    this.temporarilyVisibleCards.get(playerId)?.clear();
  }

  getTemporarilyVisibleCards(playerId: string): Set<number> {
    return this.temporarilyVisibleCards.get(playerId) || new Set();
  }

  // Highlighted cards (for bot actions)
  addHighlightedCard(playerId: string, position: number) {
    if (!this.highlightedCards.has(playerId)) {
      this.highlightedCards.set(playerId, new Set());
    }
    this.highlightedCards.get(playerId)?.add(position);
  }

  clearHighlightedCards() {
    this.highlightedCards.clear();
  }

  clearHighlightedCardsForPlayer(playerId: string) {
    this.highlightedCards.get(playerId)?.clear();
  }

  getHighlightedCards(playerId: string): Set<number> {
    return this.highlightedCards.get(playerId) || new Set();
  }

  // Declaration feedback (shows if rank declaration was correct)
  // For correct declaration: shows on drawn card (pending action area)
  // For incorrect declaration: shows on discard pile
  drawnCardDeclarationFeedback: {
    isCorrect: boolean;
    timestamp: number;
  } | null = null;
  discardPileDeclarationFeedback: {
    isCorrect: boolean;
    timestamp: number;
  } | null = null;

  // Failed toss-in feedback (shows error on card that was incorrectly tossed in)
  // Map<playerId, Map<position, timestamp>>
  failedTossInFeedback = new Map<string, Map<number, number>>();

  setDrawnCardDeclarationFeedback(isCorrect: boolean) {
    this.drawnCardDeclarationFeedback = {
      isCorrect,
      timestamp: Date.now(),
    };
  }

  setDiscardPileDeclarationFeedback(isCorrect: boolean) {
    this.discardPileDeclarationFeedback = {
      isCorrect,
      timestamp: Date.now(),
    };
  }

  clearDrawnCardDeclarationFeedback() {
    this.drawnCardDeclarationFeedback = null;
  }

  clearDiscardPileDeclarationFeedback() {
    this.discardPileDeclarationFeedback = null;
  }

  getDrawnCardDeclarationFeedback(): boolean | null {
    if (!this.drawnCardDeclarationFeedback) return null;

    // Check if expired (pure getter - no side effects)
    if (Date.now() - this.drawnCardDeclarationFeedback.timestamp > 2000) {
      // Schedule cleanup for next tick to avoid mutating during render
      if (typeof queueMicrotask !== 'undefined') {
        queueMicrotask(() => {
          if (
            this.drawnCardDeclarationFeedback &&
            Date.now() - this.drawnCardDeclarationFeedback.timestamp > 2000
          ) {
            this.clearDrawnCardDeclarationFeedback();
          }
        });
      }
      return null;
    }

    return this.drawnCardDeclarationFeedback.isCorrect;
  }

  getDiscardPileDeclarationFeedback(): boolean | null {
    if (!this.discardPileDeclarationFeedback) return null;

    // Check if expired (pure getter - no side effects)
    if (Date.now() - this.discardPileDeclarationFeedback.timestamp > 2000) {
      // Schedule cleanup for next tick to avoid mutating during render
      if (typeof queueMicrotask !== 'undefined') {
        queueMicrotask(() => {
          if (
            this.discardPileDeclarationFeedback &&
            Date.now() - this.discardPileDeclarationFeedback.timestamp > 2000
          ) {
            this.clearDiscardPileDeclarationFeedback();
          }
        });
      }
      return null;
    }

    return this.discardPileDeclarationFeedback.isCorrect;
  }

  // Failed toss-in feedback actions
  addFailedTossInFeedback(playerId: string, position: number) {
    if (!this.failedTossInFeedback.has(playerId)) {
      this.failedTossInFeedback.set(playerId, new Map());
    }
    this.failedTossInFeedback.get(playerId)?.set(position, Date.now());
  }

  hasFailedTossInFeedback(playerId: string, position: number): boolean {
    const playerFeedback = this.failedTossInFeedback.get(playerId);
    if (!playerFeedback) return false;

    const timestamp = playerFeedback.get(position);
    if (!timestamp) return false;

    // Check if expired (2 seconds)
    if (Date.now() - timestamp > 2000) {
      // Schedule cleanup for next tick
      if (typeof queueMicrotask !== 'undefined') {
        queueMicrotask(() => {
          const currentTimestamp = this.failedTossInFeedback
            .get(playerId)
            ?.get(position);
          if (currentTimestamp && Date.now() - currentTimestamp > 2000) {
            this.failedTossInFeedback.get(playerId)?.delete(position);
          }
        });
      }
      return false;
    }

    return true;
  }

  clearFailedTossInFeedback() {
    this.failedTossInFeedback.clear();
  }
}

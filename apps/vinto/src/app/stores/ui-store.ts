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
}

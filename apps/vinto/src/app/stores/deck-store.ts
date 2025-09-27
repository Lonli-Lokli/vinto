'use client';

import { makeAutoObservable } from 'mobx';
import { Card } from '../shapes';
import { shuffleDeck, createDeck } from '../lib/game-helpers';

export class DeckStore {
  drawPile: Card[] = [];
  discardPile: Card[] = [];

  constructor() {
    makeAutoObservable(this);
  }

  // Deck initialization
  initializeDeck(): Card[] {
    const deck = shuffleDeck(createDeck());
    this.discardPile = [];
    return deck; // Return for player distribution, drawPile will be set after dealing
  }

  // Set the remaining cards as draw pile after dealing to players
  setDrawPileAfterDealing(remainingCards: Card[]) {
    this.drawPile = remainingCards;
  }

  // Draw pile operations
  drawCard(): Card | null {
    if (this.drawPile.length === 0) {
      return null;
    }
    return this.drawPile.shift() || null;
  }

  drawCards(count: number): Card[] {
    const cards: Card[] = [];
    for (let i = 0; i < count && this.drawPile.length > 0; i++) {
      const card = this.drawCard();
      if (card) {
        cards.push(card);
      }
    }
    return cards;
  }

  peekTopCard(): Card | null {
    return this.drawPile[0] || null;
  }

  get drawPileSize(): number {
    return this.drawPile.length;
  }

  get hasDrawCards(): boolean {
    return this.drawPile.length > 0;
  }

  // Discard pile operations
  discardCard(card: Card) {
    // Mark the card as played if it's an action card being discarded after use
    if (card.action) {
      card.played = true;
    }
    this.discardPile.unshift(card);
  }

  discardCards(cards: Card[]) {
    cards.forEach((card) => this.discardCard(card));
  }

  takeFromDiscard(): Card | null {
    if (this.discardPile.length === 0) {
      return null;
    }
    return this.discardPile.shift() || null;
  }

  peekTopDiscard(): Card | null {
    return this.discardPile[0] || null;
  }

  get discardPileSize(): number {
    return this.discardPile.length;
  }

  get hasDiscardCards(): boolean {
    return this.discardPile.length > 0;
  }

  // Discard pile validation for toss-in
  canTossIn(card: Card): boolean {
    const topDiscard = this.peekTopDiscard();
    return topDiscard !== null && card.rank === topDiscard.rank;
  }

  // Advanced operations
  reshuffleDiscardIntoDraw() {
    if (this.discardPile.length <= 1) return; // Keep top card

    const topCard = this.discardPile.shift();
    const cardsToShuffle = [...this.discardPile];

    // Reset played status for reshuffled cards
    cardsToShuffle.forEach((card) => {
      card.played = false;
    });

    this.discardPile = topCard ? [topCard] : [];
    this.drawPile.push(...shuffleDeck(cardsToShuffle));
  }

  // Auto-reshuffle when draw pile is empty
  ensureDrawCards(): boolean {
    if (this.drawPile.length === 0 && this.discardPile.length > 1) {
      this.reshuffleDiscardIntoDraw();
      return true;
    }
    return this.drawPile.length > 0;
  }

  // Utility methods
  getAllCards(): Card[] {
    return [...this.drawPile, ...this.discardPile];
  }

  getTotalCardCount(): number {
    return this.drawPile.length + this.discardPile.length;
  }

  findCard(cardId: string): { pile: 'draw' | 'discard'; index: number } | null {
    const drawIndex = this.drawPile.findIndex((card) => card.id === cardId);
    if (drawIndex !== -1) {
      return { pile: 'draw', index: drawIndex };
    }

    const discardIndex = this.discardPile.findIndex(
      (card) => card.id === cardId
    );
    if (discardIndex !== -1) {
      return { pile: 'discard', index: discardIndex };
    }

    return null;
  }

  // State queries
  get isEmpty(): boolean {
    return this.drawPile.length === 0 && this.discardPile.length === 0;
  }

  get isDrawPileEmpty(): boolean {
    return this.drawPile.length === 0;
  }

  get isDiscardPileEmpty(): boolean {
    return this.discardPile.length === 0;
  }

  // Statistics
  getDiscardPilePreview(count = 5): Card[] {
    return this.discardPile.slice(0, count);
  }

  getDrawPilePreview(count = 1): Card[] {
    return this.drawPile.slice(0, count);
  }

  // Debugging helpers
  getDiscardHistory(): string[] {
    return this.discardPile.map(
      (card) => `${card.rank}${card.action ? '*' : ''}`
    );
  }

  getPileStats(): { draw: number; discard: number; total: number } {
    return {
      draw: this.drawPile.length,
      discard: this.discardPile.length,
      total: this.getTotalCardCount(),
    };
  }

  // Reset method
  reset() {
    this.drawPile = [];
    this.discardPile = [];
  }

  // Validation
  validateDeckIntegrity(): boolean {
    const allCards = this.getAllCards();
    const uniqueIds = new Set(allCards.map((card) => card.id));

    // Check for duplicates
    if (uniqueIds.size !== allCards.length) {
      console.error('Deck integrity error: Duplicate card IDs found');
      return false;
    }

    return true;
  }
}

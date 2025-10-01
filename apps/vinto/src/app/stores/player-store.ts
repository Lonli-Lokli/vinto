'use client';

import { injectable } from 'tsyringe';
import { makeAutoObservable } from 'mobx';
import { Player, Card, Difficulty } from '../shapes';

@injectable()
export class PlayerStore {
  players: Player[] = [];
  currentPlayerIndex = 0;
  turnCount = 0;
  setupPeeksRemaining = 2;

  constructor() {
    makeAutoObservable(this);
  }

  // Computed getters
  get currentPlayer(): Player | null {
    return this.players[this.currentPlayerIndex] || null;
  }

  get humanPlayer(): Player | null {
    return this.players.find((p) => p.isHuman) || null;
  }

  get botPlayers(): Player[] {
    return this.players.filter((p) => p.isBot);
  }

  get humanPlayerIndex(): number {
    return this.players.findIndex((p) => p.isHuman);
  }

  get previousPlayerIndex(): number {
    return (
      (this.currentPlayerIndex - 1 + this.players.length) % this.players.length
    );
  }

  get previousPlayer(): Player | null {
    return this.players[this.previousPlayerIndex] || null;
  }

  get isCurrentPlayerHuman(): boolean {
    return this.currentPlayer?.isHuman ?? false;
  }

  get isCurrentPlayerBot(): boolean {
    return this.currentPlayer ? !this.currentPlayer.isHuman : false;
  }

  get wasLastPlayerHuman(): boolean {
    return this.previousPlayer?.isHuman ?? false;
  }

  // Player creation and initialization
  initializePlayers(deck: Card[], difficulty: Difficulty) {
    this.players = [
      {
        id: 'human',
        name: 'You',
        cards: deck.splice(0, 5),
        knownCardPositions: new Set(),
        temporarilyVisibleCards: new Set(),
        highlightedCards: new Set(),
        isHuman: true,
        isBot: false,
        position: 'bottom',
        coalitionWith: new Set(),
      },
      {
        id: 'bot1',
        name: 'Michelangelo',
        cards: deck.splice(0, 5),
        knownCardPositions: new Set(),
        temporarilyVisibleCards: new Set(),
        highlightedCards: new Set(),
        isHuman: false,
        isBot: true,
        position: 'left',
        coalitionWith: new Set(),
      },
      {
        id: 'bot2',
        name: 'Donatello',
        cards: deck.splice(0, 5),
        knownCardPositions: new Set(),
        temporarilyVisibleCards: new Set(),
        highlightedCards: new Set(),
        isHuman: false,
        isBot: true,
        position: 'top',
        coalitionWith: new Set(),
      },
      {
        id: 'bot3',
        name: 'Raphael',
        cards: deck.splice(0, 5),
        knownCardPositions: new Set(),
        temporarilyVisibleCards: new Set(),
        highlightedCards: new Set(),
        isHuman: false,
        isBot: true,
        position: 'right',
        coalitionWith: new Set(),
      },
    ];

    // Give bots initial knowledge based on difficulty
    this.botPlayers.forEach((player) => {
      player.knownCardPositions.add(0);
      player.knownCardPositions.add(1);
    });

    this.currentPlayerIndex = 0;
    this.turnCount = 0;
    this.setupPeeksRemaining = 2;
  }

  // Player management
  getPlayer(playerId: string): Player | null {
    return this.players.find((p) => p.id === playerId) || null;
  }

  getPlayerByIndex(index: number): Player | null {
    return this.players[index] || null;
  }

  getOpponents(playerId: string): Player[] {
    return this.players.filter((p) => p.id !== playerId);
  }

  // Turn management
  advancePlayer() {
    this.turnCount++;
    this.currentPlayerIndex =
      (this.currentPlayerIndex + 1) % this.players.length;
  }

  setCurrentPlayer(playerId: string) {
    const index = this.players.findIndex((p) => p.id === playerId);
    if (index !== -1) {
      this.currentPlayerIndex = index;
    }
  }

  // Card knowledge management
  peekCard(playerId: string, position: number): Card | null {
    const player = this.getPlayer(playerId);
    if (!player || this.setupPeeksRemaining <= 0) return null;

    if (!player.knownCardPositions.has(position)) {
      player.knownCardPositions.add(position);
      this.setupPeeksRemaining--;
      return player.cards[position];
    }
    return null;
  }

  makeCardTemporarilyVisible(playerId: string, position: number) {
    const player = this.getPlayer(playerId);
    if (player && position >= 0 && position < player.cards.length) {
      player.temporarilyVisibleCards.add(position);
    }
  }

  clearTemporaryCardVisibility() {
    this.players.forEach((player) => {
      player.temporarilyVisibleCards.clear();
    });
  }

  highlightCard(playerId: string, position: number) {
    const player = this.getPlayer(playerId);
    if (player && position >= 0 && position < player.cards.length) {
      player.highlightedCards.add(position);
    }
  }

  clearHighlightedCards() {
    this.players.forEach((player) => {
      player.highlightedCards.clear();
    });
  }

  addKnownCardPosition(playerId: string, position: number) {
    const player = this.getPlayer(playerId);
    if (player) {
      player.knownCardPositions.add(position);
    }
  }

  // Card operations
  addCardToPlayer(playerId: string, card: Card) {
    const player = this.getPlayer(playerId);
    if (player) {
      player.cards.push(card);
    }
  }

  removeCardFromPlayer(playerId: string, position: number): Card | null {
    const player = this.getPlayer(playerId);
    if (!player || position < 0 || position >= player.cards.length) {
      return null;
    }

    // Update known positions when removing a card
    const updatedKnown = new Set<number>();
    player.knownCardPositions.forEach((idx) => {
      if (idx === position) return;
      updatedKnown.add(idx > position ? idx - 1 : idx);
    });
    player.knownCardPositions = updatedKnown;

    return player.cards.splice(position, 1)[0];
  }

  swapCards(
    player1Id: string,
    pos1: number,
    player2Id: string,
    pos2: number
  ): boolean {
    const player1 = this.getPlayer(player1Id);
    const player2 = this.getPlayer(player2Id);

    if (
      !player1 ||
      !player2 ||
      pos1 < 0 ||
      pos1 >= player1.cards.length ||
      pos2 < 0 ||
      pos2 >= player2.cards.length
    ) {
      return false;
    }

    const card1 = player1.cards[pos1];
    const card2 = player2.cards[pos2];

    player1.cards[pos1] = card2;
    player2.cards[pos2] = card1;

    return true;
  }

  replaceCard(playerId: string, position: number, newCard: Card): Card | null {
    const player = this.getPlayer(playerId);
    if (!player || position < 0 || position >= player.cards.length) {
      return null;
    }

    const oldCard = player.cards[position];
    player.cards[position] = newCard;

    // Update knowledge for the new card position
    player.knownCardPositions.add(position);

    return oldCard;
  }

  // Coalition management
  formCoalition(playerId1: string, playerId2: string) {
    const player1 = this.getPlayer(playerId1);
    const player2 = this.getPlayer(playerId2);

    if (player1 && player2) {
      player1.coalitionWith.add(playerId2);
      player2.coalitionWith.add(playerId1);
    }
  }

  breakCoalition(playerId1: string, playerId2: string) {
    const player1 = this.getPlayer(playerId1);
    const player2 = this.getPlayer(playerId2);

    if (player1 && player2) {
      player1.coalitionWith.delete(playerId2);
      player2.coalitionWith.delete(playerId1);
    }
  }

  isInCoalition(playerId1: string, playerId2: string): boolean {
    const player1 = this.getPlayer(playerId1);
    return player1?.coalitionWith.has(playerId2) ?? false;
  }

  // Scoring
  calculatePlayerScores(): Record<string, number> {
    const scores: Record<string, number> = {};

    this.players.forEach((player) => {
      const totalValue = player.cards.reduce(
        (sum, card) => sum + card.value,
        0
      );
      scores[player.id] = totalValue;
    });

    return scores;
  }

  // AI helpers for the current player
  getCurrentPlayerWorstKnownCard(): { position: number; value: number } | null {
    const player = this.currentPlayer;
    if (!player || player.isHuman) return null;

    let worstPosition = -1;
    let worstValue = -10;

    player.cards.forEach((card, index) => {
      if (player.knownCardPositions.has(index) && card.value > worstValue) {
        worstValue = card.value;
        worstPosition = index;
      }
    });

    return worstPosition !== -1
      ? { position: worstPosition, value: worstValue }
      : null;
  }

  // Validation and utility
  isValidCardPosition(playerId: string, position: number): boolean {
    const player = this.getPlayer(playerId);
    return player ? position >= 0 && position < player.cards.length : false;
  }

  getPlayerCardCount(playerId: string): number {
    const player = this.getPlayer(playerId);
    return player?.cards.length ?? 0;
  }

  hasCards(playerId: string): boolean {
    return this.getPlayerCardCount(playerId) > 0;
  }

  reset() {
    this.players = [];
    this.currentPlayerIndex = 0;
    this.turnCount = 0;
    this.setupPeeksRemaining = 2;
  }
}

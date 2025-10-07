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
  initializePlayers(deck: Card[], _difficulty: Difficulty) {
    this.players = [
      {
        id: 'human',
        name: 'You',
        cards: deck.splice(0, 5),
        knownCardPositions: new Set(),
        temporarilyVisibleCards: new Set(),
        highlightedCards: new Set(),
        opponentKnowledge: new Map(),
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
        opponentKnowledge: new Map(),
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
        opponentKnowledge: new Map(),
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
        opponentKnowledge: new Map(),
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

    // Initialize opponent knowledge for all bots
    this.initializeOpponentKnowledge();

    this.currentPlayerIndex = 0;
    this.turnCount = 0;
    this.setupPeeksRemaining = 2;
  }

  // Initialize opponent knowledge maps for bots
  private initializeOpponentKnowledge() {
    this.botPlayers.forEach((bot) => {
      const opponents = this.getOpponents(bot.id);
      opponents.forEach((opponent) => {
        bot.opponentKnowledge.set(opponent.id, {
          opponentId: opponent.id,
          knownCards: new Map(),
        });
      });
    });
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

  // Opponent knowledge management - only for bots
  recordOpponentCard(
    observerId: string,
    opponentId: string,
    position: number,
    card: Card
  ) {
    const observer = this.getPlayer(observerId);
    if (!observer || !observer.isBot) return;

    const knowledge = observer.opponentKnowledge.get(opponentId);
    if (knowledge) {
      knowledge.knownCards.set(position, card);
    }
  }

  // Clear opponent knowledge for a specific position (e.g., after card is removed)
  clearOpponentCardKnowledge(
    observerId: string,
    opponentId: string,
    position: number
  ) {
    const observer = this.getPlayer(observerId);
    if (!observer || !observer.isBot) return;

    const knowledge = observer.opponentKnowledge.get(opponentId);
    if (knowledge) {
      knowledge.knownCards.delete(position);
    }
  }

  // Update all bots' knowledge when positions shift (e.g., card removed)
  updateOpponentKnowledgeAfterRemoval(
    opponentId: string,
    removedPosition: number
  ) {
    this.botPlayers.forEach((bot) => {
      const knowledge = bot.opponentKnowledge.get(opponentId);
      if (knowledge) {
        const updatedKnownCards = new Map<number, Card>();
        knowledge.knownCards.forEach((card, pos) => {
          if (pos === removedPosition) return; // This card was removed
          const newPos = pos > removedPosition ? pos - 1 : pos;
          updatedKnownCards.set(newPos, card);
        });
        knowledge.knownCards = updatedKnownCards;
      }
    });
  }

  // Get what a bot knows about an opponent's card
  getOpponentCardKnowledge(
    observerId: string,
    opponentId: string,
    position: number
  ): Card | null {
    const observer = this.getPlayer(observerId);
    if (!observer || !observer.isBot) return null;

    const knowledge = observer.opponentKnowledge.get(opponentId);
    return knowledge?.knownCards.get(position) || null;
  }

  // Check if bot knows a specific opponent card
  knowsOpponentCard(
    observerId: string,
    opponentId: string,
    position: number
  ): boolean {
    return (
      this.getOpponentCardKnowledge(observerId, opponentId, position) !== null
    );
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

    // Update opponent knowledge for all bots
    this.updateOpponentKnowledgeAfterRemoval(playerId, position);

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

  setVintoCaller(playerId: string) {
    // Clear any existing Vinto caller
    this.players.forEach((p) => {
      p.isVintoCaller = false;
    });

    // Set new Vinto caller
    const player = this.getPlayer(playerId);
    if (player) {
      player.isVintoCaller = true;
    }
  }

  setCoalitionLeader(playerId: string) {
    // Clear any existing coalition leader
    this.players.forEach((p) => {
      p.isCoalitionLeader = false;
    });

    // Set new coalition leader
    const player = this.getPlayer(playerId);
    if (player) {
      player.isCoalitionLeader = true;
    }
  }

  get vintoCaller(): Player | null {
    return this.players.find((p) => p.isVintoCaller) || null;
  }

  get coalitionLeader(): Player | null {
    return this.players.find((p) => p.isCoalitionLeader) || null;
  }

  get coalitionMembers(): Player[] {
    return this.players.filter((p) => !p.isVintoCaller);
  }

  // Scoring
  calculatePlayerScores(): Record<string, number> {
    const scores: Record<string, number> = {};

    // Check if there's a Vinto caller (coalition mode)
    const vintoCaller = this.vintoCaller;

    if (vintoCaller) {
      // Coalition scoring: Coalition wins if ANY member has lower score than Vinto caller
      // For display/comparison purposes, we use the BEST (lowest) score from any coalition member

      // Calculate Vinto caller's score
      const vintoScore = vintoCaller.cards.reduce(
        (sum, card) => sum + card.value,
        0
      );
      scores[vintoCaller.id] = vintoScore;

      // Calculate best coalition score (lowest individual score among members)
      let bestCoalitionScore = Infinity;
      this.coalitionMembers.forEach((member) => {
        const memberScore = member.cards.reduce(
          (sum, card) => sum + card.value,
          0
        );
        bestCoalitionScore = Math.min(bestCoalitionScore, memberScore);
      });

      // Assign best coalition score to all coalition members
      this.coalitionMembers.forEach((member) => {
        scores[member.id] = bestCoalitionScore;
      });
    } else {
      // Normal scoring: each player gets their individual score
      this.players.forEach((player) => {
        const individualScore = player.cards.reduce(
          (sum, card) => sum + card.value,
          0
        );
        scores[player.id] = individualScore;
      });
    }

    return scores;
  }

  /**
   * Get all members of a player's coalition (including the player)
   */
  getCoalitionMembers(playerId: string): string[] {
    const player = this.getPlayer(playerId);
    if (!player) return [playerId];

    const members = new Set<string>([playerId]);

    // Add direct coalition partners
    player.coalitionWith.forEach((partnerId) => {
      members.add(partnerId);
    });

    // Recursively add coalition partners of partners (transitive coalitions)
    const toProcess = Array.from(player.coalitionWith);
    const processed = new Set<string>([playerId]);

    while (toProcess.length > 0) {
      const currentId = toProcess.pop()!;
      if (processed.has(currentId)) continue;
      processed.add(currentId);

      const currentPlayer = this.getPlayer(currentId);
      if (currentPlayer) {
        currentPlayer.coalitionWith.forEach((partnerId) => {
          if (!processed.has(partnerId)) {
            members.add(partnerId);
            toProcess.push(partnerId);
          }
        });
      }
    }

    return Array.from(members);
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

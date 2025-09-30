// commands/game-commands.ts
/**
 * Core game commands for state transitions
 */

import { Command, CommandData } from './command';
import { PlayerStore } from '../stores/player-store';
import { DeckStore } from '../stores/deck-store';
import { ActionStore } from '../stores/action-store';
import { Card, Rank } from '../shapes';

/**
 * Draw card command
 */
export class DrawCardCommand extends Command {
  constructor(
    private playerStore: PlayerStore,
    private deckStore: DeckStore,
    private playerId: string
  ) {
    super();
  }

  execute(): boolean {
    const card = this.deckStore.drawCard();
    if (!card) return false;

    // Store the drawn card for potential swap/discard
    return true;
  }

  toData(): CommandData {
    return this.createCommandData('DRAW_CARD', {
      playerId: this.playerId,
    });
  }

  getDescription(): string {
    const player = this.playerStore.getPlayer(this.playerId);
    return `${player?.name || this.playerId} drew a card`;
  }
}

/**
 * Swap cards command
 */
export class SwapCardsCommand extends Command {
  constructor(
    private playerStore: PlayerStore,
    private player1Id: string,
    private position1: number,
    private player2Id: string,
    private position2: number
  ) {
    super();
  }

  execute(): boolean {
    return this.playerStore.swapCards(
      this.player1Id,
      this.position1,
      this.player2Id,
      this.position2
    );
  }

  toData(): CommandData {
    return this.createCommandData('SWAP_CARDS', {
      player1Id: this.player1Id,
      position1: this.position1,
      player2Id: this.player2Id,
      position2: this.position2,
    });
  }

  getDescription(): string {
    const player1 = this.playerStore.getPlayer(this.player1Id);
    const player2 = this.playerStore.getPlayer(this.player2Id);
    return `Swapped ${player1?.name}[${this.position1}] ↔ ${player2?.name}[${this.position2}]`;
  }
}

/**
 * Peek card command
 */
export class PeekCardCommand extends Command {
  constructor(
    private playerStore: PlayerStore,
    private playerId: string,
    private position: number,
    private isPermanent: boolean = false
  ) {
    super();
  }

  execute(): boolean {
    const player = this.playerStore.getPlayer(this.playerId);
    if (!player || this.position < 0 || this.position >= player.cards.length) {
      return false;
    }

    if (this.isPermanent) {
      this.playerStore.addKnownCardPosition(this.playerId, this.position);
    } else {
      this.playerStore.makeCardTemporarilyVisible(this.playerId, this.position);
    }

    return true;
  }

  toData(): CommandData {
    return this.createCommandData('PEEK_CARD', {
      playerId: this.playerId,
      position: this.position,
      isPermanent: this.isPermanent,
    });
  }

  getDescription(): string {
    const player = this.playerStore.getPlayer(this.playerId);
    const card = player?.cards[this.position];
    return `${player?.name || this.playerId} peeked at ${card?.rank || '?'}[${this.position}]`;
  }
}

/**
 * Discard card command
 */
export class DiscardCardCommand extends Command {
  constructor(
    private deckStore: DeckStore,
    private card: Card
  ) {
    super();
  }

  execute(): boolean {
    this.deckStore.discardCard(this.card);
    return true;
  }

  toData(): CommandData {
    return this.createCommandData('DISCARD_CARD', {
      cardId: this.card.id,
      rank: this.card.rank,
      value: this.card.value,
    });
  }

  getDescription(): string {
    return `Discarded ${this.card.rank}`;
  }
}

/**
 * Replace card in hand command
 */
export class ReplaceCardCommand extends Command {
  private oldCard: Card | null = null;

  constructor(
    private playerStore: PlayerStore,
    private playerId: string,
    private position: number,
    private newCard: Card
  ) {
    super();
  }

  execute(): boolean {
    this.oldCard = this.playerStore.replaceCard(
      this.playerId,
      this.position,
      this.newCard
    );
    return this.oldCard !== null;
  }

  override undo(): boolean {
    if (!this.oldCard) return false;

    this.playerStore.replaceCard(
      this.playerId,
      this.position,
      this.oldCard
    );
    return true;
  }

  toData(): CommandData {
    return this.createCommandData('REPLACE_CARD', {
      playerId: this.playerId,
      position: this.position,
      oldCard: this.oldCard ? {
        id: this.oldCard.id,
        rank: this.oldCard.rank,
        value: this.oldCard.value,
      } : null,
      newCard: {
        id: this.newCard.id,
        rank: this.newCard.rank,
        value: this.newCard.value,
      },
    });
  }

  getDescription(): string {
    const player = this.playerStore.getPlayer(this.playerId);
    return `${player?.name || this.playerId} replaced ${this.oldCard?.rank || '?'} with ${this.newCard.rank}[${this.position}]`;
  }
}

/**
 * Advance turn command
 */
export class AdvanceTurnCommand extends Command {
  constructor(
    private playerStore: PlayerStore,
    private fromPlayerId: string
  ) {
    super();
  }

  execute(): boolean {
    this.playerStore.advancePlayer();
    return true;
  }

  toData(): CommandData {
    return this.createCommandData('ADVANCE_TURN', {
      fromPlayerId: this.fromPlayerId,
      toPlayerId: this.playerStore.currentPlayer?.id,
    });
  }

  getDescription(): string {
    const fromPlayer = this.playerStore.getPlayer(this.fromPlayerId);
    const toPlayer = this.playerStore.currentPlayer;
    return `Turn: ${fromPlayer?.name || this.fromPlayerId} → ${toPlayer?.name || 'Unknown'}`;
  }
}

/**
 * Declare King action command
 */
export class DeclareKingActionCommand extends Command {
  constructor(
    private actionStore: ActionStore,
    private rank: Rank
  ) {
    super();
  }

  execute(): boolean {
    this.actionStore.declareKingAction(this.rank);
    return true;
  }

  toData(): CommandData {
    return this.createCommandData('DECLARE_KING_ACTION', {
      declaredRank: this.rank,
      resultingAction: this.actionStore.actionContext?.action,
    });
  }

  getDescription(): string {
    return `King declared as ${this.rank} → ${this.actionStore.actionContext?.action || 'Unknown'}`;
  }
}

/**
 * Toss-in card command
 */
export class TossInCardCommand extends Command {
  private wasCorrect: boolean = false;

  constructor(
    private playerStore: PlayerStore,
    private playerId: string,
    private position: number,
    private matchingRank: Rank
  ) {
    super();
  }

  execute(): boolean {
    const player = this.playerStore.getPlayer(this.playerId);
    if (!player) return false;

    const card = player.cards[this.position];
    if (!card) return false;

    this.wasCorrect = card.rank === this.matchingRank;

    // Remove card from player's hand
    this.playerStore.removeCardFromPlayer(this.playerId, this.position);

    return true;
  }

  toData(): CommandData {
    return this.createCommandData('TOSS_IN', {
      playerId: this.playerId,
      position: this.position,
      matchingRank: this.matchingRank,
      wasCorrect: this.wasCorrect,
    });
  }

  getDescription(): string {
    const player = this.playerStore.getPlayer(this.playerId);
    const status = this.wasCorrect ? '✓' : '✗';
    return `${player?.name || this.playerId} tossed-in [${this.position}] ${status}`;
  }
}

/**
 * Add penalty card command
 */
export class AddPenaltyCardCommand extends Command {
  constructor(
    private playerStore: PlayerStore,
    private deckStore: DeckStore,
    private playerId: string
  ) {
    super();
  }

  execute(): boolean {
    const card = this.deckStore.drawCard();
    if (!card) return false;

    this.playerStore.addCardToPlayer(this.playerId, card);
    return true;
  }

  toData(): CommandData {
    return this.createCommandData('ADD_PENALTY_CARD', {
      playerId: this.playerId,
    });
  }

  getDescription(): string {
    const player = this.playerStore.getPlayer(this.playerId);
    return `${player?.name || this.playerId} received penalty card`;
  }
}

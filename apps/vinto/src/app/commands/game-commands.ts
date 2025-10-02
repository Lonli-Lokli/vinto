// commands/game-commands.ts
/**
 * Core game commands for state transitions
 */

import { Command, CommandData } from './command';
import type {
  PlayerStore,
  DeckStore,
  ActionStore,
  GamePhaseStore,
  GamePhase,
  GameSubPhase,
  TossInStore,
  CardAnimationStore,
} from '../stores';
import { Card, Rank, Difficulty, TossInTime } from '../shapes';

/**
 * Serialized card data
 */
interface SerializedCard {
  id: string;
  rank: Rank;
  value: number;
  action?: string;
  played: boolean;
}

/**
 * Serialized player data
 */
interface SerializedPlayer {
  id: string;
  name: string;
  cards: SerializedCard[];
  knownCardPositions: number[];
  isHuman: boolean;
  position: 'bottom' | 'left' | 'top' | 'right';
}

/**
 * Initialize game command - captures complete game state including all stores
 */
export class InitializeGameCommand extends Command {
  constructor(
    private playerStore: PlayerStore,
    private deckStore: DeckStore,
    private gamePhaseStore: GamePhaseStore,
    private actionStore: ActionStore,
    private tossInStore: TossInStore,
    private difficulty: Difficulty,
    private tossInTimeConfig: TossInTime
  ) {
    super();
  }

  execute(): boolean {
    // This command doesn't modify state, it just captures it
    return true;
  }

  toData(): CommandData {
    // Serialize all players with their complete state
    const players: SerializedPlayer[] = this.playerStore.players.map(
      (player) => ({
        id: player.id,
        name: player.name,
        cards: player.cards.map((card) => ({
          id: card.id,
          rank: card.rank,
          value: card.value,
          action: card.action,
          played: card.played,
        })),
        knownCardPositions: Array.from(player.knownCardPositions),
        isHuman: player.isHuman,
        position: player.position,
      })
    );

    // Serialize deck state
    const drawPile: SerializedCard[] = this.deckStore.drawPile.map((card) => ({
      id: card.id,
      rank: card.rank,
      value: card.value,
      action: card.action,
      played: card.played,
    }));

    const discardPile: SerializedCard[] = this.deckStore.discardPile.map(
      (card) => ({
        id: card.id,
        rank: card.rank,
        value: card.value,
        action: card.action,
        played: card.played,
      })
    );

    // Serialize game phase state
    const gamePhase: {
      phase: GamePhase;
      subPhase: GameSubPhase;
      finalTurnTriggered: boolean;
    } = {
      phase: this.gamePhaseStore.phase,
      subPhase: this.gamePhaseStore.subPhase,
      finalTurnTriggered: this.gamePhaseStore.finalTurnTriggered,
    };

    // Serialize action store state
    const actionState: {
      pendingCard: SerializedCard | null;
      actionContext: any;
      selectedSwapPosition: number | null;
      swapPosition: number | null;
      swapTargets: Array<{ playerId: string; position: number }>;
      peekTargets: Array<{
        playerId: string;
        position: number;
        card?: SerializedCard;
      }>;
    } = {
      pendingCard: this.actionStore.pendingCard
        ? {
            id: this.actionStore.pendingCard.id,
            rank: this.actionStore.pendingCard.rank,
            value: this.actionStore.pendingCard.value,
            action: this.actionStore.pendingCard.action,
            played: this.actionStore.pendingCard.played,
          }
        : null,
      actionContext: this.actionStore.actionContext,
      selectedSwapPosition: this.actionStore.selectedSwapPosition,
      swapPosition: this.actionStore.swapPosition,
      swapTargets: [...this.actionStore.swapTargets],
      peekTargets: this.actionStore.peekTargets.map((pt) => ({
        playerId: pt.playerId,
        position: pt.position,
        card: pt.card
          ? {
              id: pt.card.id,
              rank: pt.card.rank,
              value: pt.card.value,
              action: pt.card.action,
              played: pt.card.played,
            }
          : undefined,
      })),
    };

    // Serialize toss-in state
    const tossInState = this.tossInStore.getState();
    const tossInData = {
      queue: tossInState.queue.map((item) => ({
        playerId: item.playerId,
        card: {
          id: item.card.id,
          rank: item.card.rank,
          value: item.card.value,
          action: item.card.action,
          played: item.card.played,
        },
      })),
      timer: tossInState.timer,
      isActive: tossInState.isActive,
      currentQueueIndex: tossInState.currentQueueIndex,
      originalCurrentPlayer: tossInState.originalCurrentPlayer,
      playersWhoTossedIn: Array.from(tossInState.playersWhoTossedIn),
    };

    return this.createCommandData('INITIALIZE_GAME', {
      version: '2.0.0', // Bumped version for full state capture
      players,
      drawPile,
      discardPile,
      difficulty: this.difficulty,
      tossInTimeConfig: this.tossInTimeConfig,
      currentPlayerIndex: this.playerStore.currentPlayerIndex,
      setupPeeksRemaining: this.playerStore.setupPeeksRemaining,
      turnCount: this.playerStore.turnCount,
      gamePhase,
      actionState,
      tossInState: tossInData,
    });
  }

  getDescription(): string {
    const playerCount = this.playerStore.players.length;
    const deckSize = this.deckStore.drawPile.length;
    return `Game initialized: ${playerCount} players, ${deckSize} cards in deck, ${this.gamePhaseStore.phase}.${this.gamePhaseStore.subPhase}, difficulty: ${this.difficulty}`;
  }
}

/**
 * Draw card command
 */
export class DrawCardCommand extends Command {
  private drawnCard: Card | null = null;

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

    // Store the drawn card for potential swap/discard and for serialization
    this.drawnCard = card;
    return true;
  }

  toData(): CommandData {
    return this.createCommandData('DRAW_CARD', {
      playerId: this.playerId,
      card: this.drawnCard
        ? {
            id: this.drawnCard.id,
            rank: this.drawnCard.rank,
            value: this.drawnCard.value,
            action: this.drawnCard.action,
            played: this.drawnCard.played,
          }
        : null,
    });
  }

  getDescription(): string {
    const player = this.playerStore.getPlayer(this.playerId);
    const cardRank = this.drawnCard?.rank || '?';
    return `${player?.name || this.playerId} drew ${cardRank}`;
  }
}

/**
 * Swap cards command
 */
export class SwapCardsCommand extends Command {
  private card1: Card | null = null;
  private card2: Card | null = null;

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
    const player1 = this.playerStore.getPlayer(this.player1Id);
    const player2 = this.playerStore.getPlayer(this.player2Id);

    if (!player1 || !player2) {
      return false;
    }

    // Capture the cards being swapped before the swap
    this.card1 = player1.cards[this.position1];
    this.card2 = player2.cards[this.position2];

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
      card1: this.card1
        ? {
            id: this.card1.id,
            rank: this.card1.rank,
            value: this.card1.value,
            action: this.card1.action,
            played: this.card1.played,
          }
        : null,
      card2: this.card2
        ? {
            id: this.card2.id,
            rank: this.card2.rank,
            value: this.card2.value,
            action: this.card2.action,
            played: this.card2.played,
          }
        : null,
    });
  }

  getDescription(): string {
    const player1 = this.playerStore.getPlayer(this.player1Id);
    const player2 = this.playerStore.getPlayer(this.player2Id);
    const card1Rank = this.card1?.rank || '?';
    const card2Rank = this.card2?.rank || '?';
    return `Swapped ${player1?.name}[${this.position1}:${card1Rank}] ↔ ${player2?.name}[${this.position2}:${card2Rank}]`;
  }
}

/**
 * Peek card command
 */
export class PeekCardCommand extends Command {
  private peekedCard: Card | null = null;

  constructor(
    private playerStore: PlayerStore,
    private playerId: string,
    private position: number,
    private isPermanent = false
  ) {
    super();
  }

  execute(): boolean {
    const player = this.playerStore.getPlayer(this.playerId);
    if (!player || this.position < 0 || this.position >= player.cards.length) {
      return false;
    }

    // Capture the actual card that was peeked
    this.peekedCard = player.cards[this.position];

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
      card: this.peekedCard
        ? {
            id: this.peekedCard.id,
            rank: this.peekedCard.rank,
            value: this.peekedCard.value,
            action: this.peekedCard.action,
            played: this.peekedCard.played,
          }
        : null,
    });
  }

  getDescription(): string {
    const player = this.playerStore.getPlayer(this.playerId);
    const card = this.peekedCard || player?.cards[this.position];
    return `${player?.name || this.playerId} peeked at ${card?.rank || '?'}[${
      this.position
    }]`;
  }
}

/**
 * Discard card command
 */
export class DiscardCardCommand extends Command {
  constructor(private deckStore: DeckStore, private card: Card) {
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
    private cardAnimationStore: CardAnimationStore,
    private playerId: string,
    private position: number,
    private newCard: Card
  ) {
    super();
  }

  execute(): boolean {
    // Get the old card BEFORE replacing so we can animate it
    const player = this.playerStore.getPlayer(this.playerId);
    const oldCardToDiscard = player?.cards[this.position];

    if (this.cardAnimationStore) {
      // Animation 1: Drawn card (newCard) moving from pending to player's hand
      this.cardAnimationStore.startSwapAnimation(
        this.newCard,
        this.playerId,
        -1, // Special position for pending/drawn card
        this.playerId,
        this.position
      );

      // Animation 2: Old card from hand moving to discard pile (if it exists)
      if (oldCardToDiscard) {
        this.cardAnimationStore.startDiscardAnimation(
          oldCardToDiscard,
          this.playerId,
          this.position
        );
      }
    }

    this.oldCard = this.playerStore.replaceCard(
      this.playerId,
      this.position,
      this.newCard
    );
    return this.oldCard !== null;
  }

  override undo(): boolean {
    if (!this.oldCard) return false;

    this.playerStore.replaceCard(this.playerId, this.position, this.oldCard);
    return true;
  }

  toData(): CommandData {
    return this.createCommandData('REPLACE_CARD', {
      playerId: this.playerId,
      position: this.position,
      oldCard: this.oldCard
        ? {
            id: this.oldCard.id,
            rank: this.oldCard.rank,
            value: this.oldCard.value,
          }
        : null,
      newCard: {
        id: this.newCard.id,
        rank: this.newCard.rank,
        value: this.newCard.value,
      },
    });
  }

  getDescription(): string {
    const player = this.playerStore.getPlayer(this.playerId);
    return `${player?.name || this.playerId} replaced ${
      this.oldCard?.rank || '?'
    } with ${this.newCard.rank}[${this.position}]`;
  }
}

/**
 * Advance turn command
 */
export class AdvanceTurnCommand extends Command {
  constructor(private playerStore: PlayerStore, private fromPlayerId: string) {
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
    return `Turn: ${fromPlayer?.name || this.fromPlayerId} → ${
      toPlayer?.name || 'Unknown'
    }`;
  }
}

/**
 * Declare King action command
 */
export class DeclareKingActionCommand extends Command {
  constructor(private actionStore: ActionStore, private rank: Rank) {
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
    return `King declared as ${this.rank} → ${
      this.actionStore.actionContext?.action || 'Unknown'
    }`;
  }
}

/**
 * Toss-in card command
 */
export class TossInCardCommand extends Command {
  private wasCorrect = false;

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
    return `${player?.name || this.playerId} tossed-in [${
      this.position
    }] ${status}`;
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

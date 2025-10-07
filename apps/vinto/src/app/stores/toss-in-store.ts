'use client';

import { injectable } from 'tsyringe';
import { makeAutoObservable } from 'mobx';
import { Card, Player } from '../shapes';
import { PlayerStore } from './player-store';
import { DeckStore } from './deck-store';
import { BotDecisionService } from '../services/mcts-bot-decision';

export interface TossInAction {
  playerId: string;
  card: Card;
}

export interface TossInStoreCallbacks {
  onComplete?: () => Promise<void>;
  onActionExecute?: (playerId: string, card: Card) => Promise<void>;
  onToastMessage?: (
    type: 'success' | 'error' | 'info',
    message: string
  ) => void;
  onPenaltyCard?: (playerId: string) => void;
}

export interface TossInStoreDependencies {
  playerStore: PlayerStore;
  deckStore: DeckStore;
  botDecisionService: BotDecisionService;
  createBotContext: (playerId: string) => any;
}

@injectable()
export class TossInStore {
  queue: TossInAction[] = [];
  isActive = false;
  currentQueueIndex = 0;
  originalCurrentPlayer = ''; // Track who's "real" turn it is

  // Players who have already tossed in this round (prevent multiple toss-ins)
  private playersWhoTossedIn = new Set<string>();

  private callbacks: TossInStoreCallbacks = {};
  private deps: TossInStoreDependencies | null = null;

  constructor() {
    makeAutoObservable(this);
  }

  get hasTossInActions(): boolean {
    return this.queue.length > 0;
  }

  get currentTossInAction(): TossInAction | null {
    return this.queue[this.currentQueueIndex] || null;
  }

  get isProcessingQueue(): boolean {
    return this.hasTossInActions && this.currentQueueIndex < this.queue.length;
  }

  get hasPlayerTossedIn(): (playerId: string) => boolean {
    return (playerId: string) => this.playersWhoTossedIn.has(playerId);
  }

  setCallbacks(callbacks: TossInStoreCallbacks) {
    this.callbacks = { ...this.callbacks, ...callbacks };
  }

  setDependencies(deps: TossInStoreDependencies) {
    this.deps = deps;
  }

  startTossInPeriod(currentPlayerId: string) {
    this.originalCurrentPlayer = currentPlayerId;
    this.isActive = true;
    this.queue = [];
    this.currentQueueIndex = 0;
    this.playersWhoTossedIn.clear();

    // Bots make immediate toss-in decisions
    this.handleBotTossInDecisions();
  }

  /**
   * Bots immediately decide whether to toss in when period starts
   */
  private handleBotTossInDecisions(): void {
    if (!this.deps) return;

    const { playerStore, deckStore, botDecisionService, createBotContext } =
      this.deps;
    const discardedRank = deckStore.peekTopDiscard()?.rank;
    if (!discardedRank) return;

    playerStore.botPlayers.forEach((player) => {
      if (!this.hasPlayerTossedIn(player.id)) {
        const context = createBotContext(player.id);

        if (
          botDecisionService.shouldParticipateInTossIn(discardedRank, context)
        ) {
          // Find ALL matching cards
          const matchingPositions = player.cards
            .map((card, index) => ({ card, position: index }))
            .filter(({ card }) => card.rank === discardedRank);

          if (matchingPositions.length > 0) {
            // Select BEST card to toss (prefer unknown cards, then lower value)
            const bestToToss = matchingPositions.reduce((best, curr) => {
              const bestKnown = player.knownCardPositions.has(best.position);
              const currKnown = player.knownCardPositions.has(curr.position);

              // Prefer unknown cards (don't toss cards we know unless necessary)
              if (!bestKnown && currKnown) return best;
              if (bestKnown && !currKnown) return curr;

              // If both known or both unknown, prefer lower value
              return best.card.value <= curr.card.value ? best : curr;
            });

            // Toss in ONLY ONE card
            this.tossInCard(player.id, bestToToss.position);
          }
        }
      }
    });
  }

  /**
   * Handles a player tossing in a card during the toss-in period.
   * This method:
   * 1. Validates the card can be tossed in
   * 2. Removes the card from the player
   * 3. Discards it to the pile
   * 4. Records the toss-in action if it has an action
   * 5. Handles penalties for incorrect toss-ins
   */
  tossInCard(playerId: string, position: number): boolean {
    if (!this.deps) {
      console.error('TossInStore dependencies not set');
      return false;
    }

    const { playerStore, deckStore } = this.deps;
    const player = playerStore.getPlayer(playerId);
    const topDiscard = deckStore.peekTopDiscard();

    if (
      !player ||
      !topDiscard ||
      !playerStore.isValidCardPosition(playerId, position)
    ) {
      return false;
    }

    const tossedCard = player.cards[position];
    const validation = this.canTossIn(playerId, tossedCard, topDiscard.rank);

    if (!validation.canToss) {
      // Reveal the incorrect card temporarily for human players
      if (player.isHuman) {
        playerStore.makeCardTemporarilyVisible(playerId, position);

        // Auto-hide after 3 seconds and apply penalty
        setTimeout(() => {
          playerStore.clearTemporaryCardVisibility();
          this.applyPenalty(playerId);
        }, 3000);
      } else {
        this.applyPenalty(playerId);
      }
      return false;
    }

    // Remove card from player
    const removedCard = playerStore.removeCardFromPlayer(playerId, position);
    if (!removedCard) return false;

    // Discard the card only if it has no action
    // Action cards will be discarded when their action is executed
    if (!removedCard.action) {
      deckStore.discardCard(removedCard);
    }

    // Record the successful toss-in
    return this.recordTossIn(player, removedCard);
  }

  /**
   * Apply penalty card to player for incorrect toss-in
   */
  private applyPenalty(playerId: string): void {
    if (!this.deps) return;

    const { playerStore, deckStore } = this.deps;
    if (deckStore.hasDrawCards) {
      const penaltyCard = deckStore.drawCard();
      if (penaltyCard) {
        playerStore.addCardToPlayer(playerId, penaltyCard);
      }
    }
    this.recordIncorrectTossIn(playerId);
  }

  /**
   * Manually finish the toss-in period (called when human clicks "Continue")
   */
  finishTossInPeriod() {
    this.isActive = false;

    if (this.hasTossInActions) {
      this.startProcessingQueue();
    } else {
      this.returnToNormalFlow();
    }
  }

  // Pure validation method - external systems provide the data
  canTossIn(
    playerId: string,
    card: Card,
    discardedRank: string
  ): {
    canToss: boolean;
    reason?: string;
  } {
    if (!this.isActive) {
      return { canToss: false, reason: 'Toss-in period is not active' };
    }

    if (card.rank !== discardedRank) {
      return {
        canToss: false,
        reason: 'Card rank does not match discarded card',
      };
    }

    return { canToss: true };
  }

  // Pure action method - external systems handle the consequences
  recordTossIn(player: Player, card: Card): boolean {
    if (card.action) {
      this.addToQueue(player.id, card);
    }

    return true;
  }

  // Handle incorrect toss-in attempts
  recordIncorrectTossIn(playerId: string): void {
    this.callbacks.onPenaltyCard?.(playerId);
    this.callbacks.onToastMessage?.(
      'error',
      'Toss-in failed - penalty card drawn'
    );
  }

  get waitingForTossIn() {
    return this.isActive;
  }

  private addToQueue(playerId: string, card: Card) {
    this.queue.push({ playerId, card });
  }

  private startProcessingQueue() {
    // Reset index to ensure we start from the beginning
    this.currentQueueIndex = 0;
    this.processNextAction();
  }

  async processNextAction(): Promise<boolean> {
    const currentAction = this.currentTossInAction;
    if (!currentAction) {
      this.finishQueueProcessing();
      return false;
    }

    const { playerId, card } = currentAction;

    // Notify external system to execute the action (await if async)
    await this.callbacks.onActionExecute?.(playerId, card);

    return true;
  }

  async skipCurrentAction(): Promise<boolean> {
    const currentAction = this.currentTossInAction;
    if (!currentAction) return false;

    this.callbacks.onToastMessage?.(
      'info',
      `Skipped ${currentAction.card.rank} action`
    );
    return await this.advanceQueue();
  }

  async completeCurrentAction(): Promise<boolean> {
    return await this.advanceQueue();
  }

  private async advanceQueue(): Promise<boolean> {
    if (this.currentQueueIndex >= this.queue.length - 1) {
      await this.finishQueueProcessing();
      return false;
    }

    this.currentQueueIndex++;
    return await this.processNextAction();
  }

  private async finishQueueProcessing() {
    this.clearQueue();
    this.returnToNormalFlow();
  }

  private async returnToNormalFlow() {
    // Notify external system that processing is complete
    await this.callbacks.onComplete?.();
  }

  clearQueue() {
    this.queue = [];
    this.currentQueueIndex = 0;
    this.originalCurrentPlayer = '';
    this.playersWhoTossedIn.clear();
  }

  reset() {
    this.clearQueue();
    this.isActive = false;
  }

  // Readonly access to internal state for debugging/testing
  getState() {
    return {
      queue: [...this.queue],
      isActive: this.isActive,
      currentQueueIndex: this.currentQueueIndex,
      originalCurrentPlayer: this.originalCurrentPlayer,
      playersWhoTossedIn: new Set(this.playersWhoTossedIn),
    };
  }
}

'use client';

import { makeAutoObservable } from 'mobx';
import { Card, TossInTime } from '../shapes';
import { PlayerStore } from './player-store';
import { DeckStore } from './deck-store';
import { GamePhaseStore } from './game-phase-store';
import { CardActionHandler } from './card-action-handler';
import { GameToastService } from '../lib/toast-service';

export interface TossInAction {
  playerId: string;
  card: Card;
}

export class TossInStore {
  queue: TossInAction[] = [];
  timer = 0;
  timeConfig: TossInTime = 7;
  isActive = false;
  currentQueueIndex = 0;
  originalCurrentPlayer = ''; // Track who's "real" turn it is

  private interval: NodeJS.Timeout | null = null;
  private onCompleteCallback: (() => void) | null = null;

  constructor(
    private playerStore: PlayerStore,
    private deckStore: DeckStore,
    private phaseStore: GamePhaseStore,
    private cardActionHandler: CardActionHandler
  ) {
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

  setTimeConfig(time: TossInTime) {
    this.timeConfig = time;
  }

  setCompleteCallback(callback: () => void) {
    this.onCompleteCallback = callback;
  }

  startTossInPeriod(currentPlayerId: string) {
    this.originalCurrentPlayer = currentPlayerId;
    this.isActive = true;
    this.timer = this.timeConfig;
    this.queue = [];
    this.currentQueueIndex = 0;

    this.phaseStore.startTossQueueActive();

    if (this.interval) {
      clearInterval(this.interval);
    }

    this.interval = setInterval(() => {
      if (!this.isActive || this.timer <= 0) {
        this.stopTimer();

        if (this.timer <= 0) {
          this.finishTossInPeriod();
        }
        return;
      }

      this.timer--;

      // Bot participation logic
      this.handleBotParticipation();
    }, 1000);
  }

  private handleBotParticipation() {
    const discardedRank = this.deckStore.peekTopDiscard()?.rank;
    if (!discardedRank) return;

    this.playerStore.botPlayers.forEach((player) => {
      if (Math.random() < 0.3) {
        // 30% chance bot tosses in
        player.cards.forEach((card, position) => {
          if (card.rank === discardedRank && Math.random() < 0.5) {
            this.tossInCard(player.id, position);
          }
        });
      }
    });
  }

  tossInCard(playerId: string, position: number): boolean {
    if (!this.isActive) return false;

    const player = this.playerStore.getPlayer(playerId);
    const topDiscard = this.deckStore.peekTopDiscard();

    if (
      !player ||
      !topDiscard ||
      !this.playerStore.isValidCardPosition(playerId, position)
    ) {
      return false;
    }

    const tossedCard = player.cards[position];
    if (!this.deckStore.canTossIn(tossedCard)) {
      // Incorrect toss-in: penalty card
      this.handleIncorrectTossIn(playerId, player.name);
      return false;
    }

    // Correct toss-in
    const removedCard = this.playerStore.removeCardFromPlayer(
      playerId,
      position
    );
    if (!removedCard) return false;

    this.deckStore.discardCard(removedCard);

    if (removedCard.action) {
      this.addToQueue(playerId, removedCard);
    }

    GameToastService.success(`${player.name} tossed in ${removedCard.rank}!`);
    return true;
  }

  private handleIncorrectTossIn(playerId: string, playerName: string) {
    if (this.deckStore.hasDrawCards) {
      const penaltyCard = this.deckStore.drawCard();
      if (penaltyCard) {
        this.playerStore.addCardToPlayer(playerId, penaltyCard);
        GameToastService.error(
          `${playerName}'s toss-in failed - penalty card drawn`
        );
      }
    }
  }

  private addToQueue(playerId: string, card: Card) {
    this.queue.push({ playerId, card });
  }

  private finishTossInPeriod() {
    this.isActive = false;
    this.timer = 0;

    if (this.hasTossInActions) {
      this.startProcessingQueue();
    } else {
      this.returnToNormalFlow();
    }
  }

  private startProcessingQueue() {
    this.phaseStore.startTossQueueProcessing();
    this.processNextAction();
  }

  processNextAction() {
    const currentAction = this.currentTossInAction;
    if (!currentAction) {
      this.finishQueueProcessing();
      return;
    }

    const { playerId, card } = currentAction;
    const player = this.playerStore.getPlayer(playerId);

    if (!player) {
      this.skipCurrentAction();
      return;
    }

    if (player.isHuman) {
      GameToastService.info(
        `You can execute ${card.rank} action (${card.action})`
      );
    }

    // For AI players, decide whether to use the action
    if (!player.isHuman && Math.random() < 0.3) {
      this.skipCurrentAction();
      return;
    }

    this.cardActionHandler.executeCardAction(card, playerId);
  }

  skipCurrentAction() {
    const currentAction = this.currentTossInAction;
    if (!currentAction) return;

    const player = this.playerStore.getPlayer(currentAction.playerId);
    if (player?.isHuman) {
      GameToastService.info(`You skipped ${currentAction.card.rank} action`);
    }

    this.currentQueueIndex++;
    this.processNextAction();
  }

  completeCurrentAction() {
    this.currentQueueIndex++;
    this.processNextAction();
  }

  private finishQueueProcessing() {
    this.clearQueue();
    this.returnToNormalFlow();
  }

  private returnToNormalFlow() {
    this.phaseStore.returnToIdle();
    // Notify GameStore that toss-in processing is complete
    if (this.onCompleteCallback) {
      this.onCompleteCallback();
    }
  }

  private stopTimer() {
    if (this.interval) {
      clearInterval(this.interval);
      this.interval = null;
    }
  }

  clearQueue() {
    this.queue = [];
    this.currentQueueIndex = 0;
    this.originalCurrentPlayer = '';
  }

  reset() {
    this.stopTimer();
    this.clearQueue();
    this.isActive = false;
    this.timer = 0;
  }

  dispose() {
    this.stopTimer();
  }
}

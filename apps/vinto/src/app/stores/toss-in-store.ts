'use client';

import { makeAutoObservable } from 'mobx';
import { Card, TossInTime } from '../shapes';

export interface TossInAction {
  playerId: string;
  card: Card;
}

export interface TossInStoreCallbacks {
  onTimerTick?: () => void;
  onComplete?: () => void;
  onActionExecute?: (playerId: string, card: Card) => void;
  onToastMessage?: (type: 'success' | 'error' | 'info', message: string) => void;
  onPenaltyCard?: (playerId: string) => void;
}

export class TossInStore {
  queue: TossInAction[] = [];
  timer = 0;
  timeConfig: TossInTime = 7;
  isActive = false;
  currentQueueIndex = 0;
  originalCurrentPlayer = ''; // Track who's "real" turn it is

  // Players who have already tossed in this round (prevent multiple toss-ins)
  private playersWhoTossedIn = new Set<string>();

  private callbacks: TossInStoreCallbacks = {};

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

  setTimeConfig(time: TossInTime) {
    this.timeConfig = time;
  }

  setCallbacks(callbacks: TossInStoreCallbacks) {
    this.callbacks = { ...this.callbacks, ...callbacks };
  }

  startTossInPeriod(currentPlayerId: string) {
    this.originalCurrentPlayer = currentPlayerId;
    this.isActive = true;
    this.timer = this.timeConfig;
    this.queue = [];
    this.currentQueueIndex = 0;
    this.playersWhoTossedIn.clear();
  }

  // External timer control - to be called by GameStore or timer service
  tick(): void {
    if (!this.isActive || this.timer <= 0) {
      return;
    }

    this.timer--;

    // Notify external systems about timer tick
    this.callbacks.onTimerTick?.();

    if (this.timer <= 0) {
      this.finishTossInPeriod();
    }
  }

  forceFinish(): void {
    this.timer = 0;
    this.finishTossInPeriod();
  }

  // Pure validation method - external systems provide the data
  canTossIn(playerId: string, card: Card, discardedRank: string): {
    canToss: boolean;
    reason?: string
  } {
    if (!this.isActive) {
      return { canToss: false, reason: 'Toss-in period is not active' };
    }

    if (this.playersWhoTossedIn.has(playerId)) {
      return { canToss: false, reason: 'Player has already tossed in this round' };
    }

    if (playerId === this.originalCurrentPlayer) {
      return { canToss: false, reason: 'Cannot toss in during your own turn' };
    }

    if (card.rank !== discardedRank) {
      return { canToss: false, reason: 'Card rank does not match discarded card' };
    }

    return { canToss: true };
  }

  // Pure action method - external systems handle the consequences
  recordTossIn(playerId: string, card: Card): boolean {
    if (this.playersWhoTossedIn.has(playerId)) {
      return false;
    }

    this.playersWhoTossedIn.add(playerId);

    if (card.action) {
      this.addToQueue(playerId, card);
    }

    this.callbacks.onToastMessage?.('success', `Player tossed in ${card.rank}!`);
    return true;
  }

  // Handle incorrect toss-in attempts
  recordIncorrectTossIn(playerId: string): void {
    this.playersWhoTossedIn.add(playerId);
    this.callbacks.onPenaltyCard?.(playerId);
    this.callbacks.onToastMessage?.('error', 'Toss-in failed - penalty card drawn');
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
    // Reset index to ensure we start from the beginning
    this.currentQueueIndex = 0;
    this.processNextAction();
  }

  processNextAction(): boolean {
    const currentAction = this.currentTossInAction;
    if (!currentAction) {
      this.finishQueueProcessing();
      return false;
    }

    const { playerId, card } = currentAction;

    // Notify external system to execute the action
    this.callbacks.onActionExecute?.(playerId, card);
    this.callbacks.onToastMessage?.('info', `${card.rank} action available`);

    return true;
  }

  skipCurrentAction(): boolean {
    const currentAction = this.currentTossInAction;
    if (!currentAction) return false;

    this.callbacks.onToastMessage?.('info', `Skipped ${currentAction.card.rank} action`);
    return this.advanceQueue();
  }

  completeCurrentAction(): boolean {
    return this.advanceQueue();
  }

  private advanceQueue(): boolean {
    if (this.currentQueueIndex >= this.queue.length - 1) {
      this.finishQueueProcessing();
      return false;
    }

    this.currentQueueIndex++;
    return this.processNextAction();
  }

  private finishQueueProcessing() {
    this.clearQueue();
    this.returnToNormalFlow();
  }

  private returnToNormalFlow() {
    // Notify external system that processing is complete
    this.callbacks.onComplete?.();
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
    this.timer = 0;
  }

  // Readonly access to internal state for debugging/testing
  getState() {
    return {
      queue: [...this.queue],
      timer: this.timer,
      isActive: this.isActive,
      currentQueueIndex: this.currentQueueIndex,
      originalCurrentPlayer: this.originalCurrentPlayer,
      playersWhoTossedIn: new Set(this.playersWhoTossedIn)
    };
  }
}

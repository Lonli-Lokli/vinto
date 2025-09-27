'use client';

import { makeAutoObservable } from 'mobx';
import { Card, Rank } from '../shapes';

export type TargetType =
  | 'own-card'
  | 'opponent-card'
  | 'swap-cards'
  | 'peek-then-swap'
  | 'declare-action'
  | 'force-draw';

export interface ActionContext {
  action: string;
  playerId: string;
  targetType?: TargetType;
  declaredCard?: Rank;
}

export interface SwapTarget {
  playerId: string;
  position: number;
}

export interface PeekTarget {
  playerId: string;
  position: number;
  card?: Card;
}

export interface TossInItem {
  playerId: string;
  card: Card;
}

export class ActionStore {
  // Current action being executed
  actionContext: ActionContext | null = null;
  pendingCard: Card | null = null;

  // Swap related state
  selectedSwapPosition: number | null = null;
  swapTargets: SwapTarget[] = [];
  swapPosition: number | null = null;

  // Peek related state
  peekTargets: PeekTarget[] = [];

  // Toss-in related state
  tossInQueue: TossInItem[] = [];
  tossInTimer = 0;

  constructor() {
    makeAutoObservable(this);
  }

  // Action context management
  startAction(card: Card, playerId: string) {
    this.actionContext = {
      action: card.action || '',
      playerId,
      targetType: this.getTargetType(card.rank),
    };
    this.pendingCard = card;
  }

  clearAction() {
    this.actionContext = null;
    this.pendingCard = null;
    this.selectedSwapPosition = null;
    this.swapTargets = [];
    this.peekTargets = [];
    this.swapPosition = null;
  }

  updateActionTargetType(targetType: TargetType) {
    if (this.actionContext) {
      this.actionContext.targetType = targetType;
    }
  }

  declareKingAction(rank: Rank) {
    if (this.actionContext) {
      this.actionContext.declaredCard = rank;
      this.actionContext.targetType = this.getTargetType(rank);
      this.actionContext.action = this.getActionDescription(rank);
    }
  }

  // Swap management
  setSwapPosition(position: number | null) {
    this.swapPosition = position;
  }

  setSelectedSwapPosition(position: number) {
    this.selectedSwapPosition = position;
  }

  addSwapTarget(playerId: string, position: number): boolean {
    // Check if already selected (for deselection)
    const existingIndex = this.swapTargets.findIndex(
      target => target.playerId === playerId && target.position === position
    );

    if (existingIndex !== -1) {
      this.swapTargets.splice(existingIndex, 1);
      return false; // Deselected
    }

    // Don't allow more than 2 selections
    if (this.swapTargets.length >= 2) {
      return false;
    }

    // Check if trying to select second card from same player
    if (this.swapTargets.length === 1 && this.swapTargets[0].playerId === playerId) {
      return false;
    }

    this.swapTargets.push({ playerId, position });
    return true; // Selected
  }

  clearSwapTargets() {
    this.swapTargets = [];
  }

  get hasCompleteSwapSelection(): boolean {
    return this.swapTargets.length === 2;
  }

  // Peek management
  addPeekTarget(playerId: string, position: number, card?: Card): boolean {
    // Check if already selected (for deselection)
    const existingIndex = this.peekTargets.findIndex(
      target => target.playerId === playerId && target.position === position
    );

    if (existingIndex !== -1) {
      this.peekTargets.splice(existingIndex, 1);
      return false; // Deselected
    }

    // Don't allow more than 2 selections
    if (this.peekTargets.length >= 2) {
      return false;
    }

    // Check if trying to select second card from same player
    if (this.peekTargets.length === 1 && this.peekTargets[0].playerId === playerId) {
      return false;
    }

    this.peekTargets.push({ playerId, position, card });
    return true; // Selected
  }

  clearPeekTargets() {
    this.peekTargets = [];
  }

  get hasCompletePeekSelection(): boolean {
    return this.peekTargets.length === 2;
  }

  // Toss-in management
  addToTossInQueue(playerId: string, card: Card) {
    this.tossInQueue.push({ playerId, card });
  }

  removeFromTossInQueue(): TossInItem | null {
    return this.tossInQueue.shift() || null;
  }

  clearTossInQueue() {
    this.tossInQueue = [];
  }

  get hasTossInActions(): boolean {
    return this.tossInQueue.length > 0;
  }

  get currentTossInAction(): TossInItem | null {
    return this.tossInQueue[0] || null;
  }

  setTossInTimer(time: number) {
    this.tossInTimer = time;
  }

  decrementTossInTimer() {
    this.tossInTimer = Math.max(0, this.tossInTimer - 1);
  }

  // Helper methods
  private getTargetType(rank: Rank): TargetType {
    switch (rank) {
      case '7':
      case '8':
        return 'own-card';
      case '9':
      case '10':
        return 'opponent-card';
      case 'J':
        return 'swap-cards';
      case 'Q':
        return 'peek-then-swap';
      case 'K':
        return 'declare-action';
      case 'A':
        return 'force-draw';
      default:
        return 'own-card';
    }
  }

  private getActionDescription(rank: Rank): string {
    switch (rank) {
      case '7':
      case '8':
        return 'Peek 1 of your cards';
      case '9':
      case '10':
        return 'Peek 1 opponent card';
      case 'J':
        return 'Swap any two facedown cards on the table';
      case 'Q':
        return 'Peek any two cards, then optionally swap them';
      case 'K':
        return 'Declare any card action and execute it';
      case 'A':
        return 'Force opponent to draw';
      default:
        return 'Unknown action';
    }
  }

  // State queries
  get isExecutingAction(): boolean {
    return this.actionContext !== null;
  }

  get needsTargetSelection(): boolean {
    return this.actionContext !== null &&
           this.actionContext.targetType !== undefined &&
           !this.hasRequiredTargets();
  }

  private hasRequiredTargets(): boolean {
    if (!this.actionContext?.targetType) return false;

    switch (this.actionContext.targetType) {
      case 'own-card':
      case 'opponent-card':
      case 'force-draw':
        return false; // These are handled immediately when target is selected
      case 'swap-cards':
        return this.hasCompleteSwapSelection;
      case 'peek-then-swap':
        return this.hasCompletePeekSelection;
      case 'declare-action':
        return this.actionContext.declaredCard !== undefined;
      default:
        return false;
    }
  }

  get currentActionRequirement(): string {
    if (!this.actionContext?.targetType) return '';

    switch (this.actionContext.targetType) {
      case 'own-card':
        return 'Select one of your cards to peek';
      case 'opponent-card':
        return 'Select an opponent card to peek';
      case 'swap-cards':
        return this.swapTargets.length === 0
          ? 'Select first card to swap'
          : 'Select second card to swap';
      case 'peek-then-swap':
        return this.peekTargets.length === 0
          ? 'Select first card to peek'
          : this.peekTargets.length === 1
          ? 'Select second card to peek'
          : 'Choose to swap or skip';
      case 'declare-action':
        return 'Declare which card action to execute';
      case 'force-draw':
        return 'Select opponent to force draw';
      default:
        return '';
    }
  }

  reset() {
    this.actionContext = null;
    this.pendingCard = null;
    this.selectedSwapPosition = null;
    this.swapTargets = [];
    this.swapPosition = null;
    this.peekTargets = [];
    this.tossInQueue = [];
    this.tossInTimer = 0;
  }
}
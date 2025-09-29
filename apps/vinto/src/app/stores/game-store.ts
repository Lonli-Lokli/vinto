'use client';

import { makeAutoObservable, reaction, computed } from 'mobx';
import { GamePhaseStore } from './game-phase-store';
import { PlayerStore } from './player-store';
import { DeckStore } from './deck-store';
import { ActionStore } from './action-store';
import { CardActionHandler } from './card-action-handler';
import { TossInStore, TossInStoreCallbacks } from './toss-in-store';
import { timerService } from '../services/timer-service';
import { OracleVintoClient } from '../lib/oracle-client';
import { GameToastService } from '../lib/toast-service';
import {
  GameState,
  AIMove,
  Difficulty,
  TossInTime,
  Card,
  Rank,
} from '../shapes';

export class GameStore implements GameState {
  // Individual stores
  phaseStore: GamePhaseStore;
  playerStore: PlayerStore;
  deckStore: DeckStore;
  actionStore: ActionStore;
  actionHandler: CardActionHandler;
  tossInStore: TossInStore;

  // Game state properties from original interface
  gameId = '';
  roundNumber = 1;

  // AI and session state
  oracle: OracleVintoClient;
  aiThinking = false;
  currentMove: AIMove | null = null;
  sessionActive = false;

  // Configuration
  difficulty: Difficulty = 'moderate';
  tossInTimeConfig: TossInTime = 7;

  // Vinto call availability
  canCallVintoAfterHumanTurn = false;

  // Reactions
  private aiTurnReaction: (() => void) | null = null;

  constructor() {
    // Initialize stores
    this.phaseStore = new GamePhaseStore();
    this.playerStore = new PlayerStore();
    this.deckStore = new DeckStore();
    this.actionStore = new ActionStore();

    // Initialize action handler with store dependencies
    this.actionHandler = new CardActionHandler(
      this.playerStore,
      this.actionStore,
      this.deckStore,
      this.phaseStore
    );

    // Initialize toss-in store
    this.tossInStore = new TossInStore();

    // Set up toss-in store callbacks
    const tossInCallbacks: TossInStoreCallbacks = {
      onTimerTick: () => this.handleTossInTimerTick(),
      onComplete: () => this.handleTossInComplete(),
      onActionExecute: (playerId, card) =>
        this.actionHandler.executeCardAction(card, playerId),
      onToastMessage: (type, message) => {
        switch (type) {
          case 'success':
            GameToastService.success(message);
            break;
          case 'error':
            GameToastService.error(message);
            break;
          case 'info':
            GameToastService.info(message);
            break;
        }
      },
      onPenaltyCard: (playerId) => this.handleTossInPenalty(playerId),
    };

    this.tossInStore.setCallbacks(tossInCallbacks);

    // Initialize Oracle client
    this.oracle = new OracleVintoClient();

    // Make this store observable
    makeAutoObservable(this);

    // Set up reactions
    this.setupReactions();
  }

  // Computed properties to maintain compatibility with original interface
  get players() {
    return this.playerStore.players;
  }

  get currentPlayerIndex() {
    return this.playerStore.currentPlayerIndex;
  }

  get drawPile() {
    return this.deckStore.drawPile;
  }

  get discardPile() {
    return this.deckStore.discardPile;
  }

  get phase() {
    return this.phaseStore.phase;
  }

  get turnCount() {
    return this.playerStore.turnCount;
  }

  get finalTurnTriggered() {
    return this.phaseStore.finalTurnTriggered;
  }

  get isCurrentPlayerWaiting() {
    return (
      !this.isChoosingCardAction &&
      !this.isAwaitingActionTarget &&
      !this.isDeclaringRank &&
      this.phase === 'playing' &&
      this.players.find((p) => p.isHuman)?.id !==
        this.players[this.currentPlayerIndex]?.id &&
      !this.canCallVintoAfterHumanTurn &&
      !this.isProcessingTossInQueue
    );
  }

  // Derived state based on new stores
  get pendingCard() {
    return this.actionStore.pendingCard;
  }

  get isSelectingSwapPosition() {
    return this.phaseStore.isSelectingSwapPosition;
  }

  get isChoosingCardAction() {
    return this.phaseStore.isChoosingCardAction;
  }

  get isAwaitingActionTarget() {
    return this.phaseStore.isAwaitingActionTarget;
  }

  get actionContext() {
    return this.actionStore.actionContext;
  }

  get selectedSwapPosition() {
    return this.actionStore.selectedSwapPosition;
  }

  get swapTargets() {
    return this.actionStore.swapTargets;
  }

  get peekTargets() {
    return this.actionStore.peekTargets;
  }

  get isDeclaringRank() {
    return this.phaseStore.isDeclaringRank;
  }

  get swapPosition() {
    return this.actionStore.swapPosition;
  }

  get setupPeeksRemaining() {
    return this.playerStore.setupPeeksRemaining;
  }

  get waitingForTossIn() {
    return this.tossInStore.isActive;
  }

  get tossInTimer() {
    return this.tossInStore.timer;
  }

  get tossInQueue() {
    return this.tossInStore.queue;
  }

  get isProcessingTossInQueue() {
    return this.tossInStore.isProcessingQueue;
  }

  // Computed values for Vinto call availability
  get canCallVinto() {
    return computed(() => {
      const humanPlayerIndex = this.playerStore.humanPlayerIndex;
      const prevIndex = this.playerStore.previousPlayerIndex;

      return (
        this.phaseStore.isGameActive &&
        !this.phaseStore.finalTurnTriggered &&
        !this.playerStore.isCurrentPlayerHuman &&
        !this.aiThinking &&
        this.phaseStore.canCallVinto && // Use phase store's Vinto restriction logic
        !this.phaseStore.isSelectingSwapPosition &&
        !this.phaseStore.isChoosingCardAction &&
        !this.phaseStore.isAwaitingActionTarget &&
        !this.phaseStore.isDeclaringRank &&
        prevIndex === humanPlayerIndex
      );
    }).get();
  }

  // Setup MobX reactions
  private setupReactions() {
    // React to player changes for AI turns and Vinto availability
    this.aiTurnReaction = reaction(
      () => ({
        currentPlayerIndex: this.playerStore.currentPlayerIndex,
        currentPlayer: this.playerStore.currentPlayer,
        sessionActive: this.sessionActive,
      }),
      ({ currentPlayer }) => {
        // Update vinto call availability
        this.updateVintoCallAvailability();

        // Clear temporary card visibility on turn changes
        this.playerStore.clearTemporaryCardVisibility();

        // Handle AI turns
        if (
          currentPlayer &&
          !currentPlayer.isHuman &&
          !this.aiThinking &&
          this.sessionActive &&
          this.phaseStore.isIdle
        ) {
          // Schedule AI move after delay
          setTimeout(() => {
            if (
              this.playerStore.currentPlayer === currentPlayer &&
              !this.aiThinking &&
              this.sessionActive
            ) {
              this.makeAIMove(this.difficulty);
            }
          }, this.tossInTimeConfig * 1000);
        }
      }
    );
  }

  // Game initialization
  async initGame() {
    try {
      const deck = this.deckStore.initializeDeck();

      // Initialize players with dealt cards
      this.playerStore.initializePlayers(deck, this.difficulty);
      this.deckStore.setDrawPileAfterDealing(deck);

      // Set initial game state
      this.gameId = `game-${Date.now()}`;
      this.roundNumber = 1;
      this.sessionActive = true;

      this.phaseStore.reset();
      this.actionStore.reset();
      this.tossInStore.reset();
    } catch (error) {
      console.error('Error initializing game:', error);
      GameToastService.error('Failed to start game. Please try again.');
    }
  }

  // Configuration updates
  updateDifficulty(diff: Difficulty) {
    this.difficulty = diff;
  }

  updateTossInTime(time: TossInTime) {
    this.tossInTimeConfig = time;
    this.tossInStore.setTimeConfig(time);
  }

  // Game phase transitions
  peekCard(playerId: string, position: number) {
    const card = this.playerStore.peekCard(playerId, position);
    if (card) {
      GameToastService.success(
        `Peeked at position ${position + 1}: ${card.rank} (value ${card.value})`
      );
    }
  }

  finishSetup() {
    if (this.playerStore.setupPeeksRemaining > 0) {
      GameToastService.warning(
        `You still have ${this.playerStore.setupPeeksRemaining} peeks remaining!`
      );
      return;
    }

    this.phaseStore.finishSetup();
    this.playerStore.clearTemporaryCardVisibility();
    this.updateVintoCallAvailability();

    GameToastService.success('Game started! Draw a card or take from discard.');
  }

  // Card actions
  drawCard() {
    if (!this.deckStore.hasDrawCards) {
      GameToastService.error('No cards left in draw pile!');
      return;
    }

    this.phaseStore.startDrawing();
    const drawnCard = this.deckStore.drawCard();
    if (drawnCard) {
      this.actionStore.pendingCard = drawnCard;
      this.phaseStore.startChoosingAction();
    }
  }

  takeFromDiscard() {
    const topCard = this.deckStore.peekTopDiscard();
    if (!topCard || topCard.played) return;

    const takenCard = this.deckStore.takeFromDiscard();
    if (!takenCard) return;

    const currentPlayer = this.playerStore.currentPlayer;
    if (currentPlayer?.isHuman && takenCard.action) {
      // Use action immediately for discard pile cards
      this.actionHandler.executeCardAction(takenCard, currentPlayer.id);
    }
  }

  chooseSwap() {
    this.phaseStore.startSelectingPosition();
  }

  choosePlayCard() {
    const currentPlayer = this.playerStore.currentPlayer;
    const pendingCard = this.actionStore.pendingCard;

    if (!currentPlayer || !pendingCard) return;

    if (pendingCard.action) {
      this.actionHandler.executeCardAction(pendingCard, currentPlayer.id);
    } else {
      // Discard non-action cards directly
      this.deckStore.discardCard(pendingCard);
      GameToastService.info(`Discarded ${pendingCard.rank}`);

      this.actionStore.pendingCard = null;
      this.phaseStore.returnToIdle();
      this.startTossInPeriod();
    }
  }

  swapCard(position: number) {
    this.actionStore.setSwapPosition(position);
    this.phaseStore.startDeclaringRank();

    GameToastService.info(
      'Choose to declare the card rank or skip declaration'
    );
  }

  // Action execution
  executeCardAction(card: Card, playerId: string) {
    return this.actionHandler.executeCardAction(card, playerId);
  }

  selectActionTarget(playerId: string, position: number) {
    return this.actionHandler.selectActionTarget(playerId, position);
  }

  executeQueenSwap() {
    return this.actionHandler.executeQueenSwap();
  }

  skipQueenSwap() {
    return this.actionHandler.skipQueenSwap();
  }

  declareKingAction(rank: Rank) {
    return this.actionHandler.declareKingAction(rank);
  }

  // Rank declaration during swap
  declareRank(rank: Rank) {
    const currentPlayer = this.playerStore.currentPlayer;
    const pendingCard = this.actionStore.pendingCard;
    const swapPosition = this.actionStore.swapPosition;

    if (!currentPlayer || !pendingCard || swapPosition === null) return;

    const actualCard = currentPlayer.cards[swapPosition];
    const isCorrectDeclaration = actualCard?.rank === rank;

    if (isCorrectDeclaration) {
      GameToastService.success(
        `Correct declaration! ${rank} matches the card. You can use its action.`
      );

      // Perform the swap
      const replacedCard = this.playerStore.replaceCard(
        currentPlayer.id,
        swapPosition,
        pendingCard
      );
      if (replacedCard) {
        this.deckStore.discardCard(replacedCard);

        // Execute action if available
        if (replacedCard.action) {
          this.actionHandler.executeCardAction(replacedCard, currentPlayer.id);
        } else {
          this.startTossInPeriod();
        }
      }
    } else {
      GameToastService.error(
        `Wrong declaration! ${rank} doesn't match the card. Drawing penalty card.`
      );

      // Perform swap and add penalty
      const replacedCard = this.playerStore.replaceCard(
        currentPlayer.id,
        swapPosition,
        pendingCard
      );
      if (replacedCard) {
        this.deckStore.discardCard(replacedCard);
      }

      // Add penalty card
      if (this.deckStore.hasDrawCards) {
        const penaltyCard = this.deckStore.drawCard();
        if (penaltyCard) {
          this.playerStore.addCardToPlayer(currentPlayer.id, penaltyCard);
        }
      }

      this.startTossInPeriod();
    }

    // Clean up
    this.actionStore.pendingCard = null;
    this.actionStore.setSwapPosition(null);
    this.phaseStore.returnToIdle();
  }

  skipDeclaration() {
    const currentPlayer = this.playerStore.currentPlayer;
    const pendingCard = this.actionStore.pendingCard;
    const swapPosition = this.actionStore.swapPosition;

    if (!currentPlayer || !pendingCard || swapPosition === null) return;

    GameToastService.info('Skipped declaration. Card swapped without action.');

    const replacedCard = this.playerStore.replaceCard(
      currentPlayer.id,
      swapPosition,
      pendingCard
    );
    if (replacedCard) {
      this.deckStore.discardCard(replacedCard);
    }

    // Clean up
    this.actionStore.pendingCard = null;
    this.actionStore.setSwapPosition(null);
    this.phaseStore.returnToIdle();

    this.startTossInPeriod();
  }

  discardCard() {
    if (this.actionStore.pendingCard) {
      this.deckStore.discardCard(this.actionStore.pendingCard);
      this.actionStore.clearAction();
      this.phaseStore.returnToIdle();
      this.startTossInPeriod();
    }
  }

  // Toss-in mechanics
  startTossInPeriod() {
    const currentPlayer = this.playerStore.currentPlayer;
    if (currentPlayer) {
      this.phaseStore.startTossQueueActive();
      this.tossInStore.startTossInPeriod(currentPlayer.id);

      // Start external timer
      timerService.startTimer(
        'toss-in',
        () => {
          this.tossInStore.tick();
        },
        1000
      );
    }
  }

  tossInCard(playerId: string, position: number) {
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
    const validation = this.tossInStore.canTossIn(
      playerId,
      tossedCard,
      topDiscard.rank
    );

    if (!validation.canToss) {
      this.tossInStore.recordIncorrectTossIn(playerId);
      return false;
    }

    // Remove card from player and discard it
    const removedCard = this.playerStore.removeCardFromPlayer(
      playerId,
      position
    );
    if (!removedCard) return false;

    this.deckStore.discardCard(removedCard);

    // Record the successful toss-in
    return this.tossInStore.recordTossIn(playerId, removedCard);
  }

  processTossInQueue() {
    // This method is now handled internally by TossInStore
    // No longer need to manually call advanceTurn as it's handled by callback
  }

  processNextTossInAction() {
    this.tossInStore.processNextAction();
  }

  skipCurrentTossInAction() {
    this.tossInStore.skipCurrentAction();
  }

  finishTossInQueueProcessing() {
    this.tossInStore.completeCurrentAction();
  }

  // Turn management
  private advanceTurn() {
    if (this.aiThinking || this.tossInStore.hasTossInActions) return;

    this.playerStore.advancePlayer();
    this.updateVintoCallAvailability();

    // Check for game end
    if (
      this.phaseStore.finalTurnTriggered &&
      this.playerStore.currentPlayerIndex === 0
    ) {
      this.phaseStore.startScoring();
    }
  }

  // MobX actions for AI state
  setAIThinking(thinking: boolean) {
    this.aiThinking = thinking;
  }

  // AI moves
  async makeAIMove(difficulty: string) {
    try {
      const currentPlayer = this.playerStore.currentPlayer;
      if (!currentPlayer || currentPlayer.isHuman || this.aiThinking) return;

      this.setAIThinking(true);
      this.phaseStore.startAIThinking();
      this.updateVintoCallAvailability();

      try {
        const move = await this.oracle.requestAIMove(
          this,
          currentPlayer.id,
          difficulty as Difficulty
        );
        this.currentMove = move;

        // Execute AI move logic
        if (this.deckStore.hasDrawCards) {
          const drawnCard = this.deckStore.drawCard();
          if (drawnCard) {
            const worstCard = this.playerStore.getCurrentPlayerWorstKnownCard();

            if (
              worstCard &&
              drawnCard.value < worstCard.value &&
              worstCard.value > 3
            ) {
              const replacedCard = this.playerStore.replaceCard(
                currentPlayer.id,
                worstCard.position,
                drawnCard
              );
              if (replacedCard) {
                this.deckStore.discardCard(replacedCard);
              }
            } else {
              this.deckStore.discardCard(drawnCard);
            }
          }
        }

        this.startTossInPeriod();
      } catch {
        this.advanceTurn();
      } finally {
        this.setAIThinking(false);
        this.phaseStore.returnToIdle();
      }
    } catch (error) {
      console.error('Error in makeAIMove:', error);
      this.setAIThinking(false);
      this.phaseStore.returnToIdle();
    }
  }

  // Vinto and scoring
  callVinto() {
    this.phaseStore.triggerFinalTurn();
    GameToastService.success('VINTO called! Final round begins.');
  }

  calculateFinalScores() {
    return this.playerStore.calculatePlayerScores();
  }

  // Toss-in callback handlers
  private handleTossInTimerTick() {
    // Handle bot participation
    this.handleBotTossInParticipation();
  }

  private handleTossInComplete() {
    timerService.stopTimer('toss-in');
    this.phaseStore.returnToIdle();
    this.advanceTurn();
  }

  private handleTossInPenalty(playerId: string) {
    if (this.deckStore.hasDrawCards) {
      const penaltyCard = this.deckStore.drawCard();
      if (penaltyCard) {
        this.playerStore.addCardToPlayer(playerId, penaltyCard);
      }
    }
  }

  private handleBotTossInParticipation() {
    const discardedRank = this.deckStore.peekTopDiscard()?.rank;
    if (!discardedRank) return;

    this.playerStore.botPlayers.forEach((player) => {
      if (
        Math.random() < 0.3 &&
        !this.tossInStore.hasPlayerTossedIn(player.id)
      ) {
        // 30% chance bot tosses in
        player.cards.forEach((card, position) => {
          if (card.rank === discardedRank && Math.random() < 0.5) {
            this.tossInCard(player.id, position);
          }
        });
      }
    });
  }

  // Helper methods
  updateVintoCallAvailability() {
    this.canCallVintoAfterHumanTurn = this.canCallVinto;
  }

  // Cleanup
  dispose() {
    if (this.aiTurnReaction) {
      this.aiTurnReaction();
      this.aiTurnReaction = null;
    }
    timerService.stopAllTimers();
    this.tossInStore.reset();
  }
}

// Create and export the store instance
export const gameStore = new GameStore();

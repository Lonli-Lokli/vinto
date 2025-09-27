'use client';

import { makeAutoObservable, reaction, computed } from 'mobx';
import { GamePhaseStore } from './game-phase-store';
import { PlayerStore } from './player-store';
import { DeckStore } from './deck-store';
import { ActionStore } from './action-store';
import { CardActionHandler } from './card-action-handler';
import { OracleVintoClient } from '../lib/oracle-client';
import { GameToastService } from '../lib/toast-service';
import { GameState, AIMove, Difficulty, TossInTime, Card, Rank } from '../shapes';

export class GameStore implements GameState {
  // Individual stores
  phaseStore: GamePhaseStore;
  playerStore: PlayerStore;
  deckStore: DeckStore;
  actionStore: ActionStore;
  actionHandler: CardActionHandler;

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

  // Intervals and reactions
  private tossInInterval: NodeJS.Timeout | null = null;
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
    return this.phaseStore.isWaitingForTossIn;
  }

  get tossInTimer() {
    return this.actionStore.tossInTimer;
  }

  get tossInQueue() {
    return this.actionStore.tossInQueue;
  }

  get isProcessingTossInQueue() {
    return this.phaseStore.isProcessingTossInQueue;
  }

  // Computed values for Vinto call availability
  get canCallVinto() {
    return computed(() => {
      const humanPlayerIndex = this.playerStore.humanPlayerIndex;
      const prevIndex = this.playerStore.previousPlayerIndex;

      return this.phaseStore.isGameActive &&
             !this.phaseStore.finalTurnTriggered &&
             !this.playerStore.isCurrentPlayerHuman &&
             !this.aiThinking &&
             !this.phaseStore.isWaitingForTossIn &&
             !this.phaseStore.isSelectingSwapPosition &&
             !this.phaseStore.isChoosingCardAction &&
             !this.phaseStore.isAwaitingActionTarget &&
             !this.phaseStore.isDeclaringRank &&
             prevIndex === humanPlayerIndex;
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
        if (currentPlayer &&
            !currentPlayer.isHuman &&
            !this.aiThinking &&
            this.sessionActive &&
            this.phaseStore.isIdle) {
          // Schedule AI move after delay
          setTimeout(() => {
            if (this.playerStore.currentPlayer === currentPlayer &&
                !this.aiThinking &&
                this.sessionActive) {
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

      // Set initial game state
      this.gameId = `game-${Date.now()}`;
      this.roundNumber = 1;
      this.sessionActive = true;

      this.phaseStore.reset();
      this.actionStore.reset();

      GameToastService.success('New game started! Memorize 2 of your cards.');
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

    GameToastService.info('Choose to declare the card rank or skip declaration');
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
      const replacedCard = this.playerStore.replaceCard(currentPlayer.id, swapPosition, pendingCard);
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
      const replacedCard = this.playerStore.replaceCard(currentPlayer.id, swapPosition, pendingCard);
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

    const replacedCard = this.playerStore.replaceCard(currentPlayer.id, swapPosition, pendingCard);
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
    this.phaseStore.startTossIn();
    this.actionStore.setTossInTimer(this.tossInTimeConfig);

    if (this.tossInInterval) {
      clearInterval(this.tossInInterval);
    }

    this.tossInInterval = setInterval(() => {
      if (!this.phaseStore.isWaitingForTossIn || this.actionStore.tossInTimer <= 0) {
        if (this.tossInInterval) {
          clearInterval(this.tossInInterval);
          this.tossInInterval = null;
        }

        if (this.actionStore.tossInTimer <= 0) {
          this.phaseStore.returnToIdle();
          this.actionStore.setTossInTimer(0);

          if (this.actionStore.hasTossInActions) {
            this.processTossInQueue();
          } else {
            this.advanceTurn();
          }
        }
        return;
      }

      this.actionStore.decrementTossInTimer();

      // Bot participation logic
      const discardedRank = this.deckStore.peekTopDiscard()?.rank;
      if (discardedRank) {
        this.playerStore.botPlayers.forEach(player => {
          if (Math.random() < 0.3) { // 30% chance bot tosses in
            player.cards.forEach((card, position) => {
              if (card.rank === discardedRank && Math.random() < 0.5) {
                this.tossInCard(player.id, position);
              }
            });
          }
        });
      }
    }, 1000);
  }

  tossInCard(playerId: string, position: number) {
    if (!this.phaseStore.isWaitingForTossIn) return;

    const player = this.playerStore.getPlayer(playerId);
    const topDiscard = this.deckStore.peekTopDiscard();

    if (!player || !topDiscard || !this.playerStore.isValidCardPosition(playerId, position)) return;

    const tossedCard = player.cards[position];
    if (!this.deckStore.canTossIn(tossedCard)) {
      // Incorrect toss-in: penalty card
      if (this.deckStore.hasDrawCards) {
        const penaltyCard = this.deckStore.drawCard();
        if (penaltyCard) {
          this.playerStore.addCardToPlayer(playerId, penaltyCard);
          GameToastService.error(`${player.name}'s toss-in failed - penalty card drawn`);
        }
      }
      return;
    }

    // Correct toss-in
    const removedCard = this.playerStore.removeCardFromPlayer(playerId, position);
    if (removedCard) {
      this.deckStore.discardCard(removedCard);

      if (removedCard.action) {
        this.actionStore.addToTossInQueue(playerId, removedCard);
      }

      GameToastService.success(`${player.name} tossed in ${removedCard.rank}!`);
    }
  }

  processTossInQueue() {
    if (!this.actionStore.hasTossInActions) {
      this.advanceTurn();
      return;
    }

    this.phaseStore.startProcessingTossIn();
    this.processNextTossInAction();
  }

  processNextTossInAction() {
    const currentAction = this.actionStore.currentTossInAction;
    if (!currentAction) {
      this.finishTossInQueueProcessing();
      return;
    }

    const { playerId, card } = currentAction;
    const player = this.playerStore.getPlayer(playerId);

    if (!player) {
      this.actionStore.removeFromTossInQueue();
      this.processNextTossInAction();
      return;
    }

    if (player.isHuman) {
      GameToastService.info(`You can execute ${card.rank} action (${card.action})`);
    }

    // For AI players, decide whether to use the action
    if (!player.isHuman && Math.random() < 0.3) {
      this.skipCurrentTossInAction();
      return;
    }

    this.actionHandler.executeCardAction(card, playerId);
  }

  skipCurrentTossInAction() {
    const currentAction = this.actionStore.currentTossInAction;
    if (!currentAction) return;

    const player = this.playerStore.getPlayer(currentAction.playerId);
    if (player?.isHuman) {
      GameToastService.info(`You skipped ${currentAction.card.rank} action`);
    }

    this.actionStore.removeFromTossInQueue();
    this.processNextTossInAction();
  }

  finishTossInQueueProcessing() {
    this.phaseStore.returnToIdle();
    this.actionStore.clearTossInQueue();
    this.actionStore.clearAction();
    this.advanceTurn();
  }

  // Turn management
  private advanceTurn() {
    if (this.aiThinking || this.actionStore.hasTossInActions) return;

    this.playerStore.advancePlayer();
    this.updateVintoCallAvailability();

    // Check for game end
    if (this.phaseStore.finalTurnTriggered && this.playerStore.currentPlayerIndex === 0) {
      this.phaseStore.startScoring();
    }
  }

  // AI moves
  async makeAIMove(difficulty: string) {
    try {
      const currentPlayer = this.playerStore.currentPlayer;
      if (!currentPlayer || currentPlayer.isHuman || this.aiThinking) return;

      this.aiThinking = true;
      this.phaseStore.startAIThinking();
      this.updateVintoCallAvailability();

      try {
        const move = await this.oracle.requestAIMove(this, currentPlayer.id, difficulty as Difficulty);
        this.currentMove = move;

        // Execute AI move logic
        if (this.deckStore.hasDrawCards) {
          const drawnCard = this.deckStore.drawCard();
          if (drawnCard) {
            const worstCard = this.playerStore.getCurrentPlayerWorstKnownCard();

            if (worstCard && drawnCard.value < worstCard.value && worstCard.value > 3) {
              const replacedCard = this.playerStore.replaceCard(
                currentPlayer.id, worstCard.position, drawnCard
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
        this.aiThinking = false;
        this.phaseStore.returnToIdle();
      }
    } catch (error) {
      console.error('Error in makeAIMove:', error);
      this.aiThinking = false;
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
    if (this.tossInInterval) {
      clearInterval(this.tossInInterval);
      this.tossInInterval = null;
    }
  }
}

// Create and export the store instance
export const gameStore = new GameStore();
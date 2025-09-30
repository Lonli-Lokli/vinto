'use client';

import { makeAutoObservable, reaction, computed } from 'mobx';
import { GamePhaseStore, getGamePhaseStore } from './game-phase-store';
import { getPlayerStore, PlayerStore } from './player-store';
import { DeckStore, getDeckStore } from './deck-store';
import { ActionStore, getActionStore } from './action-store';
import { ActionCoordinator } from './action-coordinator';
import { getTossInStore, TossInStore, TossInStoreCallbacks } from './toss-in-store';
import { timerService } from '../services/timer-service';
import { GameToastService } from '../lib/toast-service';
import { BotDecisionService, BotDecisionServiceFactory, BotDecisionContext } from '../services/bot-decision';
import {
  Difficulty,
  TossInTime,
  Card,
  Rank,
  TempState,
} from '../shapes';

class GameStore implements TempState {
  // Individual stores
  phaseStore: GamePhaseStore;
  playerStore: PlayerStore;
  deckStore: DeckStore;
  actionStore: ActionStore;
  actionCoordinator: ActionCoordinator;
  tossInStore: TossInStore;

  // Game state properties from original interface
  gameId = '';
  roundNumber = 1;

  // AI and session state
  botDecisionService: BotDecisionService;
  aiThinking = false;
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
    this.phaseStore = getGamePhaseStore();
    this.playerStore = getPlayerStore();
    this.deckStore = getDeckStore();
    this.actionStore = getActionStore();

    // Initialize bot decision service
    this.botDecisionService = BotDecisionServiceFactory.create(this.difficulty);

    // Initialize action coordinator with store dependencies
    this.actionCoordinator = new ActionCoordinator(
      this.playerStore,
      this.actionStore,
      this.deckStore,
      this.phaseStore,
      this.botDecisionService
    );

    // Set action complete callback for handling toss-in queue continuation
    this.actionCoordinator.setActionCompleteCallback(() => {
      this.handleActionComplete();
    });

    // Initialize toss-in store
    this.tossInStore = getTossInStore();

    // Set up toss-in store callbacks
    const tossInCallbacks: TossInStoreCallbacks = {
      onTimerTick: () => this.handleTossInTimerTick(),
      onTimerStop: () => timerService.stopTimer('toss-in'),
      onComplete: () => this.handleTossInComplete(),
      onActionExecute: async (playerId, card) => {
        await this.actionCoordinator.executeCardAction(card, playerId);
      },
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

    // Set up toss-in store dependencies
    this.tossInStore.setDependencies({
      playerStore: this.playerStore,
      deckStore: this.deckStore,
      botDecisionService: this.botDecisionService,
      createBotContext: (playerId: string) => this.createBotDecisionContext(playerId),
    });

    // Make this store observable
    makeAutoObservable(this);

    // Set up reactions
    this.setupReactions();
  }

  get isCurrentPlayerWaiting() {
    return (
      !this.phaseStore.isChoosingCardAction &&
      !this.phaseStore.isAwaitingActionTarget &&
      !this.phaseStore.isDeclaringRank &&
      this.phaseStore.phase === 'playing' &&
      this.playerStore.currentPlayer !== this.playerStore.humanPlayer &&
      !this.canCallVintoAfterHumanTurn &&
      !this.tossInStore.hasPlayerTossedIn(this.playerStore.currentPlayer?.id || '')
    );
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

        // Handle AI turns (but not during toss-in queue processing)
        if (
          currentPlayer &&
          currentPlayer.isBot &&
          !this.aiThinking &&
          this.sessionActive &&
          this.phaseStore.isIdle &&
          !this.tossInStore.isProcessingQueue
        ) {
          // Trigger AI move immediately
          this.makeAIMove(this.difficulty);
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
      const player = this.playerStore.getPlayer(playerId);
      // Only show toast for bots - humans can see the card on screen
      if (player && player.isBot) {
        GameToastService.success(
          `${player.name} peeked at position ${position + 1}`
        );
      }
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
      this.actionCoordinator.executeCardAction(takenCard, currentPlayer.id);
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
      this.actionCoordinator.executeCardAction(pendingCard, currentPlayer.id);
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
  }

  // Action execution - delegates to coordinator
  executeCardAction(card: Card, playerId: string) {
    return this.actionCoordinator.executeCardAction(card, playerId);
  }

  selectActionTarget(playerId: string, position: number) {
    return this.actionCoordinator.selectActionTarget(playerId, position);
  }

  confirmPeekCompletion() {
    return this.actionCoordinator.confirmPeekCompletion();
  }

  executeQueenSwap() {
    return this.actionCoordinator.executeQueenSwap();
  }

  skipQueenSwap() {
    return this.actionCoordinator.skipQueenSwap();
  }

  declareKingAction(rank: Rank) {
    return this.actionCoordinator.declareKingAction(rank);
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
          this.actionCoordinator.executeCardAction(replacedCard, currentPlayer.id);
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
    // Don't start a new toss-in period if we're already in one
    if (this.tossInStore.isActive || this.phaseStore.isTossQueueActive) {
      return;
    }

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
    // Delegate to TossInStore
    return this.tossInStore.tossInCard(playerId, position);
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
        // Create bot decision context
        const context = this.createBotDecisionContext(currentPlayer.id);

        // Let bot decide turn action
        const turnDecision = this.botDecisionService.decideTurnAction(context);

        if (turnDecision.action === 'take-discard') {
          this.takeFromDiscard();
          return;
        }

        // Draw new card and make decision
        if (this.deckStore.hasDrawCards) {
          const drawnCard = this.deckStore.drawCard();
          if (drawnCard) {
            await this.executeBotCardDecision(drawnCard, currentPlayer);
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

  private createBotDecisionContext(botId: string): BotDecisionContext {
    const botPlayer = this.playerStore.getPlayer(botId);
    if (!botPlayer) throw new Error(`Bot player ${botId} not found`);

    return {
      botId,
      difficulty: this.difficulty,
      botPlayer,
      allPlayers: this.playerStore.players,
      gameState: {
        players: this.playerStore.players,
        currentPlayerIndex: this.playerStore.currentPlayerIndex,
        drawPile: this.deckStore.drawPile,
        discardPile: this.deckStore.discardPile,
        phase: this.phaseStore.phase,
        gameId: this.gameId,
        roundNumber: this.roundNumber,
        turnCount: this.playerStore.turnCount,
        finalTurnTriggered: this.phaseStore.finalTurnTriggered,
      },
      discardTop: this.deckStore.peekTopDiscard() || undefined,
      pendingCard: this.actionStore.pendingCard || undefined,
      currentAction: this.actionStore.actionContext && this.actionStore.pendingCard ? {
        targetType: this.actionStore.actionContext.targetType || 'unknown',
        card: this.actionStore.pendingCard,
        peekTargets: this.actionStore.peekTargets.map(pt => ({
          playerId: pt.playerId,
          position: pt.position,
          card: pt.card
        }))
      } : undefined
    };
  }

  private async executeBotCardDecision(drawnCard: Card, currentPlayer: any) {
    this.actionStore.pendingCard = drawnCard;
    this.phaseStore.startChoosingAction();

    const context = this.createBotDecisionContext(currentPlayer.id);

    if (drawnCard.action) {
      // Use bot service to decide whether to use action
      const shouldUseAction = this.botDecisionService.shouldUseAction(drawnCard, context);

      if (shouldUseAction) {
        await this.executeBotAction(drawnCard, currentPlayer.id);
        return;
      }
    }

    // If not using action, decide between swap and discard using bot service
    const swapPosition = this.botDecisionService.selectBestSwapPosition(drawnCard, context);

    if (swapPosition !== null) {
      // Swap with selected position
      this.phaseStore.startSelectingPosition();
      this.swapCard(swapPosition);
    } else {
      // Discard the drawn card
      this.discardCard();
    }
  }

  private async executeBotAction(drawnCard: Card, playerId: string) {
    // Execute the action - the coordinator will route to bot handler
    this.actionCoordinator.executeCardAction(drawnCard, playerId);
  }

  // Vinto and scoring
  callVinto() {
    this.phaseStore.triggerFinalTurn();
    GameToastService.success('VINTO called! Final round begins.');
  }

  calculateFinalScores() {
    return this.playerStore.calculatePlayerScores();
  }

  // Action completion handler
  private handleActionComplete() {
    // Clear pending card if it exists (from regular turn)
    if (this.actionStore.pendingCard) {
      this.actionStore.pendingCard = null;
    }

    // Check if we're processing toss-in queue or in regular turn
    if (this.tossInStore.isProcessingQueue) {
      // Continue processing toss-in queue
      this.tossInStore.completeCurrentAction();
    } else {
      // After completing action during regular turn, start toss-in period
      this.startTossInPeriod();
    }
  }

  // Toss-in callback handlers
  private handleTossInTimerTick() {
    // Bot participation is now handled in TossInStore.tick()
    // This callback is kept for future extensibility
  }

  private handleTossInComplete() {
    // Don't stop timer here - it should have already completed naturally
    // Stopping it here can cause race conditions if a new toss-in period has started
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

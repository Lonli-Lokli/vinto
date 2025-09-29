'use client';

import { makeAutoObservable, reaction, computed } from 'mobx';
import { GamePhaseStore, getGamePhaseStore } from './game-phase-store';
import { getPlayerStore, PlayerStore } from './player-store';
import { DeckStore, getDeckStore } from './deck-store';
import { ActionStore, getActionStore } from './action-store';
import { CardActionHandler } from './card-action-handler';
import { getTossInStore, TossInStore, TossInStoreCallbacks } from './toss-in-store';
import { timerService } from '../services/timer-service';
import { OracleVintoClient } from '../lib/oracle-client';
import { GameToastService } from '../lib/toast-service';
import { BotDecisionService, BotDecisionServiceFactory, BotDecisionContext } from '../services/bot-decision';
import {
  AIMove,
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
  actionHandler: CardActionHandler;
  tossInStore: TossInStore;

  // Game state properties from original interface
  gameId = '';
  roundNumber = 1;

  // AI and session state
  oracle: OracleVintoClient;
  botDecisionService: BotDecisionService;
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
    this.phaseStore = getGamePhaseStore();
    this.playerStore = getPlayerStore();
    this.deckStore = getDeckStore();
    this.actionStore = getActionStore();

    // Initialize action handler with store dependencies
    this.actionHandler = new CardActionHandler(
      this.playerStore,
      this.actionStore,
      this.deckStore,
      this.phaseStore
    );

    // Initialize toss-in store
    this.tossInStore = getTossInStore();

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
    this.botDecisionService = BotDecisionServiceFactory.create(this.difficulty);

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

        // Handle AI turns
        if (
          currentPlayer &&
          !currentPlayer.isHuman &&
          !this.aiThinking &&
          this.sessionActive &&
          this.phaseStore.isIdle
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
      if (player && !player.isHuman) {
        GameToastService.success(
          `${player.name} peeked at position ${position + 1}: ${card.rank} (value ${card.value})`
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

  confirmPeekCompletion() {
    const result = this.actionHandler.confirmPeekCompletion();
    if (result) {
      // After completing peek action, start toss-in period
      this.startTossInPeriod();
    }
    return result;
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
      // Reveal the incorrect card temporarily for human players
      if (player.isHuman) {
        this.playerStore.makeCardTemporarilyVisible(playerId, position);

        // Auto-hide after 3 seconds and apply penalty
        setTimeout(() => {
          this.playerStore.clearTemporaryCardVisibility();
          this.tossInStore.recordIncorrectTossIn(playerId);
        }, 3000);
      } else {
        this.tossInStore.recordIncorrectTossIn(playerId);
      }
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
          {
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
          currentPlayer.id,
          difficulty as Difficulty
        );
        this.currentMove = move;

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

    // Short delay to simulate decision making
    await new Promise(resolve => setTimeout(resolve, 800));

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
      await new Promise(resolve => setTimeout(resolve, 500));
      this.swapCard(swapPosition);
    } else {
      // Discard the drawn card
      this.discardCard();
    }
  }

  private async executeBotAction(drawnCard: Card, playerId: string) {
    // Execute the action and handle target selection automatically
    this.actionHandler.executeCardAction(drawnCard, playerId);

    // Wait a bit and then handle target selection if needed
    await new Promise(resolve => setTimeout(resolve, 500));

    if (this.phaseStore.isAwaitingActionTarget) {
      await this.handleBotActionTargets(playerId);
    }
  }

  private async handleBotActionTargets(playerId: string) {
    const botContext = this.createBotDecisionContext(playerId);
    const decision = this.botDecisionService.selectActionTargets(botContext);

    if (!decision.targets || decision.targets.length === 0) {
      console.warn('Bot decision service returned no targets');
      return;
    }

    // Apply the decision targets with timing delays
    for (let i = 0; i < decision.targets.length; i++) {
      const target = decision.targets[i];

      // Add delay between target selections
      if (i > 0) {
        await new Promise(resolve => setTimeout(resolve, 300));
      }

      this.actionHandler.selectActionTarget(target.playerId, target.position);
    }

    // Handle special decision types
    if (decision.declaredRank) {
      await new Promise(resolve => setTimeout(resolve, 600));
      this.actionHandler.handleKingDeclaration(decision.declaredRank);
    }

    // Handle peek-then-swap decisions
    if (botContext.currentAction?.targetType === 'peek-then-swap' &&
        this.actionStore.hasCompletePeekSelection) {
      await new Promise(resolve => setTimeout(resolve, 500));

      const shouldSwap = this.botDecisionService.shouldSwapAfterPeek(
        this.actionStore.peekTargets.map(t => t.card!),
        botContext
      );

      if (shouldSwap) {
        this.actionHandler.executeQueenSwap();
      } else {
        this.actionHandler.skipQueenSwap();
      }
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
      if (!this.tossInStore.hasPlayerTossedIn(player.id)) {
        const context = this.createBotDecisionContext(player.id);

        if (this.botDecisionService.shouldParticipateInTossIn(discardedRank, context)) {
          // Find matching cards and toss one in
          player.cards.forEach((card, position) => {
            if (card.rank === discardedRank && Math.random() < 0.5) {
              this.tossInCard(player.id, position);
            }
          });
        }
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

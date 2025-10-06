'use client';

import { injectable, inject } from 'tsyringe';
import { makeAutoObservable, reaction, computed } from 'mobx';
import { GamePhaseStore } from './game-phase-store';
import { PlayerStore } from './player-store';
import { DeckStore } from './deck-store';
import { ActionStore } from './action-store';
import { ActionCoordinator } from './action-coordinator';
import { TossInStore, TossInStoreCallbacks } from './toss-in-store';
import { ReplayStore } from './replay-store';
import { CardAnimationStore } from './card-animation-store';
import { GameToastService } from '../services/toast-service';
import {
  BotDecisionService,
  BotDecisionServiceFactory,
  BotDecisionContext,
} from '../services/bot-decision';
import { CommandFactory } from '../commands/command-factory';
import { CommandHistory } from '../commands/command-history';
import { Difficulty, Card, Rank, TempState } from '../shapes';
import { GameStateManager } from '../commands';

@injectable()
export class GameStore implements TempState {
  // Individual stores (injected)
  phaseStore: GamePhaseStore;
  playerStore: PlayerStore;
  deckStore: DeckStore;
  actionStore: ActionStore;
  actionCoordinator: ActionCoordinator;
  tossInStore: TossInStore;
  replayStore: ReplayStore;
  cardAnimationStore: CardAnimationStore;
  gameStateManager: GameStateManager;

  // Command Pattern infrastructure
  private commandFactory: CommandFactory;
  private commandHistory: CommandHistory;

  // Game state properties from original interface
  gameId = '';
  roundNumber = 1;

  // AI and session state
  botDecisionService: BotDecisionService;
  aiThinking = false;
  sessionActive = false;

  // Configuration
  difficulty: Difficulty = 'moderate';

  // Vinto call availability
  canCallVintoAfterHumanTurn = false;

  // Reactions
  private aiTurnReaction: (() => void) | null = null;

  constructor(
    @inject(GamePhaseStore) phaseStore: GamePhaseStore,
    @inject(PlayerStore) playerStore: PlayerStore,
    @inject(DeckStore) deckStore: DeckStore,
    @inject(ActionStore) actionStore: ActionStore,
    @inject(TossInStore) tossInStore: TossInStore,
    @inject(ReplayStore) replayStore: ReplayStore,
    @inject(CardAnimationStore) cardAnimationStore: CardAnimationStore,
    @inject(CommandFactory) commandFactory: CommandFactory,
    @inject(CommandHistory) commandHistory: CommandHistory,
    @inject(ActionCoordinator) actionCoordinator: ActionCoordinator,
    @inject(GameStateManager) gameStateManager: GameStateManager
  ) {
    // Assign injected dependencies
    this.phaseStore = phaseStore;
    this.playerStore = playerStore;
    this.deckStore = deckStore;
    this.actionStore = actionStore;
    this.tossInStore = tossInStore;
    this.replayStore = replayStore;
    this.cardAnimationStore = cardAnimationStore;
    this.commandFactory = commandFactory;
    this.commandHistory = commandHistory;
    this.actionCoordinator = actionCoordinator;
    this.gameStateManager = gameStateManager;

    // Initialize bot decision service
    this.botDecisionService = BotDecisionServiceFactory.create(this.difficulty);

    // Set action complete callback for handling toss-in queue continuation
    this.actionCoordinator.setActionCompleteCallback(() => {
      this.handleActionComplete();
    });

    // Set up toss-in store callbacks
    const tossInCallbacks: TossInStoreCallbacks = {
      onComplete: async () => await this.handleTossInComplete(),
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
      createBotContext: (playerId: string) =>
        this.createBotDecisionContext(playerId),
    });

    // Make this store observable
    makeAutoObservable(this);

    // Set up reactions
    this.setupReactions();
  }

  // Check if current player's turn should be controlled by coalition leader
  get isCoalitionLeaderPlaying(): boolean {
    const currentPlayer = this.playerStore.currentPlayer;
    const leader = this.playerStore.coalitionLeader;

    if (!currentPlayer || !leader) return false;

    // If current player is in coalition and is NOT the Vinto caller
    // AND there is a coalition leader, the leader plays
    return (
      currentPlayer.coalitionWith.size > 0 &&
      !currentPlayer.isVintoCaller &&
      leader.id !== currentPlayer.id
    );
  }

  // Get the effective player (who actually plays - might be leader instead of current)
  get effectivePlayer() {
    if (this.isCoalitionLeaderPlaying) {
      return this.playerStore.coalitionLeader;
    }
    return this.playerStore.currentPlayer;
  }

  get isCurrentPlayerWaiting() {
    return (
      !this.phaseStore.isChoosingCardAction &&
      !this.phaseStore.isAwaitingActionTarget &&
      !this.phaseStore.isDeclaringRank &&
      this.phaseStore.phase === 'playing' &&
      this.playerStore.currentPlayer !== this.playerStore.humanPlayer &&
      !this.canCallVintoAfterHumanTurn &&
      !this.tossInStore.hasPlayerTossedIn(
        this.playerStore.currentPlayer?.id || ''
      )
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

        // Clear temporary card visibility and highlights on turn changes
        this.playerStore.clearTemporaryCardVisibility();
        this.playerStore.clearHighlightedCards();

        // Handle AI turns (but not during toss-in queue processing or replay mode)
        if (
          currentPlayer &&
          currentPlayer.isBot &&
          !this.aiThinking &&
          this.sessionActive &&
          this.phaseStore.isIdle &&
          !this.tossInStore.isProcessingQueue &&
          !this.replayStore.isReplayMode
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

      await this.gameStateManager.initializeGame(this.difficulty);
    } catch (error) {
      console.error('Error initializing game:', error);
      GameToastService.error('Failed to start game. Please try again.');
    }
  }

  // Configuration updates
  updateDifficulty(diff: Difficulty) {
    this.difficulty = diff;
  }

  // Game phase transitions
  peekCard(playerId: string, position: number) {
    this.playerStore.peekCard(playerId, position);
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
  async drawCard() {
    if (!this.deckStore.hasDrawCards) {
      GameToastService.error('No cards left in draw pile!');
      return;
    }

    const currentPlayer = this.playerStore.currentPlayer;
    if (!currentPlayer) return;

    this.phaseStore.startDrawing();

    // Store the top card before drawing and set as pending BEFORE animation
    // This ensures the drawn area exists as an animation target for the command
    // Use peekTopCard() which looks at index 0 (matches drawCard which uses shift())
    const drawnCard = this.deckStore.peekTopCard();
    if (drawnCard) {
      this.actionStore.setPendingCard(drawnCard);
    }

    // Execute draw command (removes card from deck and handles animation)
    const command = this.commandFactory.drawCard(currentPlayer.id);
    const result = await this.commandHistory.executeCommand(command);

    // Transition to choosing action phase
    if (result.success) {
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

  async choosePlayCard() {
    const currentPlayer = this.playerStore.currentPlayer;
    const pendingCard = this.actionStore.pendingCard;

    if (!currentPlayer || !pendingCard) return;

    if (pendingCard.action) {
      this.actionCoordinator.executeCardAction(pendingCard, currentPlayer.id);
    } else {
      // Discard non-action cards using command
      const command = this.commandFactory.discardCard(pendingCard);
      await this.commandHistory.executeCommand(command);

      this.actionStore.setPendingCard(null);
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
  async declareRank(rank: Rank) {
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

      // Perform the swap using command
      const replaceCommand = this.commandFactory.replaceCard(
        currentPlayer.id,
        swapPosition,
        pendingCard
      );
      const replaceResult = await this.commandHistory.executeCommand(
        replaceCommand
      );

      if (replaceResult.success) {
        const replacedCard = replaceResult.command.toData().payload.oldCard;

        if (replacedCard) {
          // Execute action if available
          if (replacedCard.action) {
            // executeCardAction will discard the card, so don't discard it here
            this.actionCoordinator.executeCardAction(
              replacedCard,
              currentPlayer.id
            );
          } else {
            // Discard non-action cards using command
            const discardCommand =
              this.commandFactory.discardCard(replacedCard);
            await this.commandHistory.executeCommand(discardCommand);
            this.startTossInPeriod();
          }
        }
      }
    } else {
      GameToastService.error(
        `Wrong declaration! ${rank} doesn't match the card. Drawing penalty card.`
      );

      // Perform swap using command
      const replaceCommand = this.commandFactory.replaceCard(
        currentPlayer.id,
        swapPosition,
        pendingCard
      );
      const replaceResult = await this.commandHistory.executeCommand(
        replaceCommand
      );

      if (replaceResult.success) {
        const replacedCard = replaceResult.command.toData().payload.oldCard;

        if (replacedCard) {
          // Discard old card using command
          const discardCommand = this.commandFactory.discardCard(replacedCard);
          await this.commandHistory.executeCommand(discardCommand);
        }
      }

      // Add penalty card using command
      if (this.deckStore.hasDrawCards) {
        const penaltyCommand = this.commandFactory.addPenaltyCard(
          currentPlayer.id
        );
        await this.commandHistory.executeCommand(penaltyCommand);
      }

      this.startTossInPeriod();
    }

    // Wait for any active animations to complete before cleaning up
    await this.cardAnimationStore.waitForAllAnimations();

    // Clean up
    this.actionStore.setPendingCard(null);
    this.actionStore.setSwapPosition(null);
    this.phaseStore.returnToIdle();
  }

  async skipDeclaration() {
    const currentPlayer = this.playerStore.currentPlayer;
    const pendingCard = this.actionStore.pendingCard;
    const swapPosition = this.actionStore.swapPosition;

    if (!currentPlayer || !pendingCard || swapPosition === null) return;

    // Perform swap using command
    const replaceCommand = this.commandFactory.replaceCard(
      currentPlayer.id,
      swapPosition,
      pendingCard
    );
    const replaceResult = await this.commandHistory.executeCommand(
      replaceCommand
    );

    // Clear pending card immediately so it disappears from DRAWN area
    // The animation has already captured the card data and will continue
    this.actionStore.setPendingCard(null);
    this.actionStore.setSwapPosition(null);

    // Wait for animations to complete BEFORE discarding
    // This ensures the discard pile doesn't update until the animation finishes
    await this.cardAnimationStore.waitForAllAnimations();

    if (replaceResult.success) {
      const replacedCard = replaceResult.command.toData().payload.oldCard;

      if (replacedCard) {
        // Discard old card using command (skip animation - already completed)
        const discardCommand = this.commandFactory.discardCard(
          replacedCard,
          true
        );
        await this.commandHistory.executeCommand(discardCommand);
      }
    }

    // Return to idle and start toss-in
    this.phaseStore.returnToIdle();
    this.startTossInPeriod();
  }

  async discardCard() {
    if (this.actionStore.pendingCard) {
      // Discard using command
      const command = this.commandFactory.discardCard(
        this.actionStore.pendingCard
      );
      await this.commandHistory.executeCommand(command);

      this.actionStore.clearAction();
      this.phaseStore.returnToIdle();
      this.startTossInPeriod();
    }
  }

  // Toss-in mechanics
  startTossInPeriod() {
    // Don't start a new toss-in period if we're already in one or in replay mode
    if (
      this.tossInStore.isActive ||
      this.phaseStore.isTossQueueActive ||
      this.replayStore.isReplayMode
    ) {
      return;
    }

    // Ensure we're in idle state before transitioning to toss_queue_active
    if (!this.phaseStore.isIdle) {
      this.phaseStore.returnToIdle();
    }

    const currentPlayer = this.playerStore.currentPlayer;
    if (currentPlayer) {
      this.phaseStore.startTossQueueActive();
      this.tossInStore.startTossInPeriod(currentPlayer.id);
      // No timer - human clicks "Continue" button to finish
    }
  }

  // Manual finish toss-in (called when human clicks "Continue")
  finishTossInPeriod() {
    this.tossInStore.finishTossInPeriod();
  }

  tossInCard(playerId: string, position: number) {
    // Delegate to TossInStore
    return this.tossInStore.tossInCard(playerId, position);
  }

  // Turn management
  private async advanceTurn() {
    // Don't advance turn if AI is thinking, toss-in is active, there are queued toss-in actions, or in replay mode
    if (
      this.aiThinking ||
      this.tossInStore.waitingForTossIn ||
      this.tossInStore.hasTossInActions ||
      this.replayStore.isReplayMode
    )
      return;

    const currentPlayer = this.playerStore.currentPlayer;
    if (!currentPlayer) return;

    // Clear action state before advancing turn to ensure clean slate for next player
    this.actionStore.clearAction();

    // Execute advance turn command
    const command = this.commandFactory.advanceTurn(currentPlayer.id);
    await this.commandHistory.executeCommand(command);

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

        // Draw new card and make decision using command system
        if (this.deckStore.hasDrawCards) {
          // Peek at the top card before drawing
          // Use peekTopCard() which looks at index 0 (matches drawCard which uses shift())
          const drawnCard = this.deckStore.peekTopCard();

          if (drawnCard) {
            // Set pending card so drawn area exists for animation target
            this.actionStore.setPendingCard(drawnCard);
          }

          // Execute draw command (removes card from deck and handles animation)
          const command = this.commandFactory.drawCard(currentPlayer.id);
          const result = await this.commandHistory.executeCommand(command);

          if (result.success && drawnCard) {
            // Wait 4 seconds to allow human to see the drawn card
            await new Promise((resolve) => setTimeout(resolve, 4000));
            await this.executeBotCardDecision(drawnCard, currentPlayer);
          }

          // Clear pending card after bot makes decision
          this.actionStore.setPendingCard(null);
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

    // Extract opponent knowledge from bot's opponentKnowledge map
    const opponentKnowledge = new Map<string, Map<number, Card>>();
    botPlayer.opponentKnowledge.forEach((knowledge, opponentId) => {
      opponentKnowledge.set(opponentId, new Map(knowledge.knownCards));
    });

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
      currentAction:
        this.actionStore.actionContext && this.actionStore.pendingCard
          ? {
              targetType:
                this.actionStore.actionContext.targetType || 'unknown',
              card: this.actionStore.pendingCard,
              peekTargets: this.actionStore.peekTargets.map((pt) => ({
                playerId: pt.playerId,
                position: pt.position,
                card: pt.card,
              })),
            }
          : undefined,
      opponentKnowledge,
    };
  }

  private async executeBotCardDecision(drawnCard: Card, currentPlayer: any) {
    this.actionStore.setPendingCard(drawnCard);
    this.phaseStore.startChoosingAction();

    const context = this.createBotDecisionContext(currentPlayer.id);

    if (drawnCard.action) {
      // Use bot service to decide whether to use action
      const shouldUseAction = this.botDecisionService.shouldUseAction(
        drawnCard,
        context
      );

      if (shouldUseAction) {
        await this.executeBotAction(drawnCard, currentPlayer.id);
        return;
      }
    }

    // If not using action, decide between swap and discard using bot service
    const swapPosition = this.botDecisionService.selectBestSwapPosition(
      drawnCard,
      context
    );

    if (swapPosition !== null) {
      // Swap with selected position
      this.phaseStore.startSelectingPosition();
      this.actionStore.setSwapPosition(swapPosition);

      // Bot knows the drawn card and where it's being placed
      // Record this knowledge BEFORE swapping
      this.playerStore.addKnownCardPosition(currentPlayer.id, swapPosition);

      // Bots perform swap immediately without declaring rank
      await this.skipDeclaration();
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
  async callVinto() {
    // Close the confirmation modal
    this.phaseStore.closeVintoConfirmation();

    // Get the current player (who called Vinto)
    const vintoPlayer = this.playerStore.currentPlayer;
    if (!vintoPlayer) return;

    // Show a 2-second animation/delay for psychological weight
    GameToastService.warning(`${vintoPlayer.name} is calling Vinto...`);

    await new Promise((resolve) => setTimeout(resolve, 2000));

    // Mark this player as the Vinto caller
    this.playerStore.setVintoCaller(vintoPlayer.id);

    // Form automatic coalition: All players EXCEPT the Vinto caller form a team
    const otherPlayers = this.playerStore.players.filter(
      (p) => p.id !== vintoPlayer.id
    );

    // Break any existing coalitions first
    this.playerStore.players.forEach((p1) => {
      this.playerStore.players.forEach((p2) => {
        if (p1.id !== p2.id) {
          this.playerStore.breakCoalition(p1.id, p2.id);
        }
      });
    });

    // Form coalition between all non-Vinto players
    for (let i = 0; i < otherPlayers.length; i++) {
      for (let j = i + 1; j < otherPlayers.length; j++) {
        this.playerStore.formCoalition(otherPlayers[i].id, otherPlayers[j].id);
      }
    }

    // If human is in coalition, set them as default leader (can be changed in UI)
    const humanInCoalition = otherPlayers.find((p) => p.isHuman);
    if (humanInCoalition) {
      this.playerStore.setCoalitionLeader(humanInCoalition.id);
    } else {
      // If human is Vinto caller, automatically select first bot as leader
      this.playerStore.setCoalitionLeader(otherPlayers[0].id);
    }

    // Trigger the final turn
    this.phaseStore.triggerFinalTurn();

    // Announce coalition formation
    const coalitionNames = otherPlayers.map((p) => p.name).join(', ');
    GameToastService.success(
      `${vintoPlayer.name} called VINTO! ${coalitionNames} form a coalition!`
    );
  }

  // Set coalition leader - called from UI
  setCoalitionLeader(playerId: string) {
    this.playerStore.setCoalitionLeader(playerId);
    this.phaseStore.closeCoalitionLeaderSelection();
    GameToastService.success(
      `${
        this.playerStore.getPlayer(playerId)?.name
      } is now the coalition leader!`
    );
  }

  calculateFinalScores() {
    return this.playerStore.calculatePlayerScores();
  }

  // Action completion handler
  private handleActionComplete() {
    // Clear pending card if it exists (from regular turn)
    if (this.actionStore.pendingCard) {
      this.actionStore.setPendingCard(null);
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
  private async handleTossInComplete() {
    this.phaseStore.returnToIdle();

    // Check if a bot should call Vinto before advancing turn
    const currentPlayer = this.playerStore.currentPlayer;
    if (
      currentPlayer &&
      currentPlayer.isBot &&
      !this.phaseStore.finalTurnTriggered &&
      this.phaseStore.isGameActive
    ) {
      // Create bot decision context
      const context = this.createBotDecisionContext(currentPlayer.id);

      // Check if bot should call Vinto
      if (this.botDecisionService.shouldCallVinto(context)) {
        // Bot calls Vinto!
        GameToastService.warning(`${currentPlayer.name} is calling Vinto!`);

        // Wait 2 seconds for dramatic effect
        await new Promise((resolve) => setTimeout(resolve, 2000));

        // Mark this player as the Vinto caller
        this.playerStore.setVintoCaller(currentPlayer.id);

        // Form automatic coalition: All players EXCEPT the Vinto caller form a team
        const otherPlayers = this.playerStore.players.filter(
          (p) => p.id !== currentPlayer.id
        );

        // Break any existing coalitions first
        this.playerStore.players.forEach((p1) => {
          this.playerStore.players.forEach((p2) => {
            if (p1.id !== p2.id) {
              this.playerStore.breakCoalition(p1.id, p2.id);
            }
          });
        });

        // Form coalition between all non-Vinto players
        for (let i = 0; i < otherPlayers.length; i++) {
          for (let j = i + 1; j < otherPlayers.length; j++) {
            this.playerStore.formCoalition(
              otherPlayers[i].id,
              otherPlayers[j].id
            );
          }
        }

        // Human must select coalition leader
        const humanInCoalition = otherPlayers.find((p) => p.isHuman);
        if (humanInCoalition) {
          // Default to human as leader, but show UI to let them choose
          this.playerStore.setCoalitionLeader(humanInCoalition.id);
          this.phaseStore.openCoalitionLeaderSelection();
        } else {
          // If human called Vinto, they must choose bot leader
          // Show leader selection UI
          this.phaseStore.openCoalitionLeaderSelection();
        }

        // Trigger final turn
        this.phaseStore.triggerFinalTurn();

        // Announce coalition formation
        const coalitionNames = otherPlayers.map((p) => p.name).join(', ');
        GameToastService.success(
          `${currentPlayer.name} called VINTO! ${coalitionNames} form a coalition!`
        );
        return; // Don't advance turn yet, let the final round begin
      }
    }

    this.advanceTurn();
  }

  private async handleTossInPenalty(playerId: string) {
    if (this.deckStore.hasDrawCards) {
      // Add penalty card using command
      const command = this.commandFactory.addPenaltyCard(playerId);
      await this.commandHistory.executeCommand(command);
    }
  }

  // Helper methods
  updateVintoCallAvailability() {
    this.canCallVintoAfterHumanTurn = this.canCallVinto;
  }

  // Command history debug methods
  get commandLog() {
    return this.commandHistory.getCommandLog();
  }

  get commandStats() {
    return this.commandHistory.getStats();
  }

  exportGameHistory() {
    return this.commandHistory.exportHistory();
  }

  debugRecentCommands(count = 20) {
    const log = this.commandHistory.getCommandLog();
    console.log('=== Recent Commands ===');
    console.log(log.slice(-count).join('\n'));
  }

  exportBugReport() {
    return {
      gameState: {
        phase: this.phaseStore.phase,
        turnCount: this.playerStore.turnCount,
        players: this.playerStore.players.map((p) => ({
          id: p.id,
          name: p.name,
          cardCount: p.cards.length,
        })),
      },
      commandHistory: this.commandHistory.exportHistory(),
      stats: this.commandHistory.getStats(),
      timestamp: Date.now(),
    };
  }

  // Cleanup
  dispose() {
    if (this.aiTurnReaction) {
      this.aiTurnReaction();
      this.aiTurnReaction = null;
    }
    this.tossInStore.reset();
  }
}

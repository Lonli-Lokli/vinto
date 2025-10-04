'use client';

import { injectable, inject } from 'tsyringe';
import { Card, Rank } from '../shapes';
import { PlayerStore } from './player-store';
import { ActionStore } from './action-store';
import { DeckStore } from './deck-store';
import { GamePhaseStore } from './game-phase-store';
import { GameToastService } from '../services/toast-service';
import { HumanActionHandler } from './human-action-handler';
import { BotActionHandler } from './bot-action-handler';
import type { BotDecisionService } from '../services/bot-decision';
import { CommandFactory, CommandHistory } from '../commands';

/**
 * ActionCoordinator - Routes actions to appropriate handlers (Human vs Bot).
 * Acts as a thin router/mediator between game logic and player-specific handlers.
 * Does NOT contain game logic itself.
 */
@injectable()
export class ActionCoordinator {
  private humanHandler: HumanActionHandler;
  private botHandler: BotActionHandler;
  private playerStore: PlayerStore;
  private actionStore: ActionStore;
  private deckStore: DeckStore;
  private phaseStore: GamePhaseStore;
  private commandFactory: CommandFactory;
  private commandHistory: CommandHistory;
  private onActionComplete?: () => void;

  constructor(
    @inject(PlayerStore) playerStore: PlayerStore,
    @inject(ActionStore) actionStore: ActionStore,
    @inject(DeckStore) deckStore: DeckStore,
    @inject(GamePhaseStore) phaseStore: GamePhaseStore,
    @inject('BotDecisionService') botDecisionService: BotDecisionService,
    @inject(CommandFactory) commandFactory: CommandFactory,
    @inject(CommandHistory) commandHistory: CommandHistory
  ) {
    this.playerStore = playerStore;
    this.actionStore = actionStore;
    this.deckStore = deckStore;
    this.phaseStore = phaseStore;
    this.commandFactory = commandFactory;
    this.commandHistory = commandHistory;

    // Initialize handlers
    this.humanHandler = new HumanActionHandler({
      playerStore,
      actionStore,
      commandFactory,
      commandHistory,
    });

    this.botHandler = new BotActionHandler({
      playerStore,
      actionStore,
      deckStore,
      botDecisionService,
      commandFactory,
      commandHistory,
    });
  }

  setActionCompleteCallback(callback: () => void) {
    this.onActionComplete = callback;
  }

  // Main entry point - routes to appropriate handler
  async executeCardAction(card: Card, playerId: string): Promise<boolean> {
    if (!card.action) return false;

    const player = this.playerStore.getPlayer(playerId);
    if (!player) return false;

    // Start the action
    this.actionStore.startAction(card, playerId);

    // Play action card using command - handles animation and discard
    // This is shared between human and bot players
    const playActionCommand = this.commandFactory.playActionCard(
      card,
      playerId
    );
    await this.commandHistory.executeCommand(playActionCommand);

    // Set appropriate phase
    this.phaseStore.startAwaitingAction();

    // Route based on card rank
    const executed = await this.routeActionByRank(
      card.rank,
      playerId,
      player.isHuman
    );

    if (!executed) {
      GameToastService.error(`Failed to execute ${card.rank} action`);
      this.cleanupAction();
      return false;
    }

    // For bot players, all actions complete immediately (no user confirmation needed)
    // Exception: King requires declaration first, which is handled separately
    if (player.isBot && card.rank !== 'K') {
      this.completeAction();
    }

    return executed;
  }

  // Route action to human or bot handler
  private async routeActionByRank(
    rank: Rank,
    playerId: string,
    isHuman: boolean
  ): Promise<boolean> {
    // For human players, return synchronously since they wait for user input
    if (isHuman) {
      const handlers: Record<string, () => boolean> = {
        '7': () => this.humanHandler.handlePeekOwnCard(playerId),
        '8': () => this.humanHandler.handlePeekOwnCard(playerId),
        '9': () => this.humanHandler.handlePeekOpponentCard(playerId),
        '10': () => this.humanHandler.handlePeekOpponentCard(playerId),
        J: () => this.humanHandler.handleSwapCards(playerId),
        Q: () => this.humanHandler.handlePeekAndSwap(playerId),
        K: () => this.humanHandler.handleDeclareAction(playerId),
        A: () => this.humanHandler.handleForceDraw(playerId),
      };
      const actionHandler = handlers[rank];
      return actionHandler ? actionHandler() : false;
    }

    // For bot players, await async execution
    const handlers: Record<string, () => Promise<boolean>> = {
      '7': () => this.botHandler.handlePeekOwnCard(playerId),
      '8': () => this.botHandler.handlePeekOwnCard(playerId),
      '9': () => this.botHandler.handlePeekOpponentCard(playerId),
      '10': () => this.botHandler.handlePeekOpponentCard(playerId),
      J: () => this.botHandler.handleSwapCards(playerId),
      Q: () => this.botHandler.handlePeekAndSwap(playerId),
      K: () => this.botHandler.handleDeclareAction(playerId),
      A: () => this.botHandler.handleForceDraw(playerId),
    };
    const actionHandler = handlers[rank];
    return actionHandler ? await actionHandler() : false;
  }

  // Target selection - route to appropriate handler
  async selectActionTarget(
    targetPlayerId: string,
    position: number
  ): Promise<boolean> {
    const context = this.actionStore.actionContext;
    if (!context) return false;

    const actionPlayer = this.playerStore.getPlayer(context.playerId);
    const targetPlayer = this.playerStore.getPlayer(targetPlayerId);

    if (!actionPlayer || !targetPlayer) return false;

    switch (context.targetType) {
      case 'own-card':
        return this.executeTargetPeekOwnCard(
          actionPlayer.isHuman,
          context.playerId,
          position
        );
      case 'opponent-card':
        return this.executeTargetPeekOpponentCard(
          actionPlayer.isHuman,
          context.playerId,
          targetPlayerId,
          position
        );
      case 'swap-cards':
        return await this.executeTargetSwapCards(
          actionPlayer.isHuman,
          context.playerId,
          targetPlayerId,
          position
        );
      case 'peek-then-swap':
        return this.executeTargetPeekAndSwap(
          actionPlayer.isHuman,
          context.playerId,
          targetPlayerId,
          position
        );
      case 'force-draw':
        return await this.executeTargetForceDraw(
          actionPlayer.isHuman,
          context.playerId,
          targetPlayerId
        );
      default:
        return false;
    }
  }

  // Execute target selection - route to handlers
  private executeTargetPeekOwnCard(
    isHuman: boolean,
    playerId: string,
    position: number
  ): boolean {
    const result = this.humanHandler.executePeekOwnCard(playerId, position);

    // Bots complete immediately, humans need confirmation
    if (!isHuman) {
      this.completeAction();
    }

    return result;
  }

  private executeTargetPeekOpponentCard(
    isHuman: boolean,
    actionPlayerId: string,
    targetPlayerId: string,
    position: number
  ): boolean {
    const result = this.humanHandler.executePeekOpponentCard(
      actionPlayerId,
      targetPlayerId,
      position
    );

    // Bots complete immediately, humans need confirmation
    if (!isHuman) {
      this.completeAction();
    }

    return result;
  }

  private async executeTargetSwapCards(
    isHuman: boolean,
    actionPlayerId: string,
    targetPlayerId: string,
    position: number
  ): Promise<boolean> {
    // Use human handler for swap target selection (bot uses different flow)
    const result = await this.humanHandler.handleSwapTargetSelection(
      actionPlayerId,
      targetPlayerId,
      position
    );

    if (this.actionStore.hasCompleteSwapSelection) {
      this.completeAction();
    }

    return result;
  }

  private executeTargetPeekAndSwap(
    isHuman: boolean,
    actionPlayerId: string,
    targetPlayerId: string,
    position: number
  ): boolean {
    // Use human handler for peek target selection
    return this.humanHandler.handlePeekTargetSelection(
      actionPlayerId,
      targetPlayerId,
      position
    );
  }

  private async executeTargetForceDraw(
    isHuman: boolean,
    actionPlayerId: string,
    targetPlayerId: string
  ): Promise<boolean> {
    // Execute force draw through deck store
    if (!this.deckStore.hasDrawCards) {
      this.deckStore.ensureDrawCards();
    }

    // Add penalty card using command
    const penaltyCommand = this.commandFactory.addPenaltyCard(targetPlayerId);
    const result = await this.commandHistory.executeCommand(penaltyCommand);

    if (!result.success) return false;

    // Show appropriate message based on player type
    if (isHuman) {
      this.humanHandler.executeForceDraw(actionPlayerId, targetPlayerId);
    }

    this.completeAction();
    return true;
  }

  // Queen-specific methods
  async executeQueenSwap(): Promise<boolean> {
    const context = this.actionStore.actionContext;
    if (!context) return false;

    const actionPlayer = this.playerStore.getPlayer(context.playerId);
    if (!actionPlayer) return false;

    // Use appropriate handler
    const result = actionPlayer.isHuman
      ? await this.humanHandler.executeQueenSwap()
      : await this.executeQueenSwapInternal();

    this.completeAction();
    return result;
  }

  private async executeQueenSwapInternal(): Promise<boolean> {
    const targets = this.actionStore.peekTargets;
    if (targets.length !== 2) return false;

    const [target1, target2] = targets;
    const context = this.actionStore.actionContext;
    const actionPlayer = context
      ? this.playerStore.getPlayer(context.playerId)
      : null;

    // Execute swap using command - only reveal if action player is human
    const swapCommand = this.commandFactory.swapCards(
      target1.playerId,
      target1.position,
      target2.playerId,
      target2.position,
      actionPlayer?.isHuman ?? false // Reveal only if human player
    );
    const result = await this.commandHistory.executeCommand(swapCommand);
    return result.success;
  }

  skipQueenSwap(): boolean {
    const context = this.actionStore.actionContext;
    if (!context) return false;

    const actionPlayer = this.playerStore.getPlayer(context.playerId);
    if (!actionPlayer) return false;

    // Use appropriate handler
    const result = actionPlayer.isHuman
      ? this.humanHandler.skipQueenSwap()
      : this.skipQueenSwapInternal();

    this.completeAction();
    return result;
  }

  private skipQueenSwapInternal(): boolean {
    GameToastService.info('Queen action: Chose not to swap the peeked cards');
    return true;
  }

  // King action declaration
  async declareKingAction(rank: Rank): Promise<boolean> {
    const context = this.actionStore.actionContext;
    if (!context || context.targetType !== 'declare-action') return false;

    const actionPlayer = this.playerStore.getPlayer(context.playerId);
    if (!actionPlayer) return false;

    // Declare the action
    if (actionPlayer.isHuman) {
      this.humanHandler.declareKingAction(context.playerId, rank);
    } else {
      // For bots, just update the action store
      this.actionStore.declareKingAction(rank);
    }

    // Execute the declared action
    return await this.routeActionByRank(
      rank,
      context.playerId,
      actionPlayer.isHuman
    );
  }

  // Alias for consistency
  async handleKingDeclaration(rank: Rank): Promise<boolean> {
    return await this.declareKingAction(rank);
  }

  // Peek completion - human only
  confirmPeekCompletion(): boolean {
    const context = this.actionStore.actionContext;
    if (!context) return false;

    const actionPlayer = this.playerStore.getPlayer(context.playerId);
    if (actionPlayer?.isBot) return false;

    const result = this.humanHandler.confirmPeekCompletion(context.playerId);

    if (result) {
      this.completeAction();
    }

    return result;
  }

  // Action completion
  private completeAction() {
    this.playerStore.clearTemporaryCardVisibility();
    this.cleanupAction();
    // Notify GameStore that action is complete
    this.onActionComplete?.();
  }

  private cleanupAction() {
    this.actionStore.clearAction();
    this.phaseStore.returnToIdle();
  }
}

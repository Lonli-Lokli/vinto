'use client';

import { Card, Rank } from '../shapes';
import { PlayerStore } from './player-store';
import { ActionStore } from './action-store';
import { DeckStore } from './deck-store';
import { GameToastService } from '../services/toast-service';
import {
  BotDecisionService,
  BotDecisionContext,
  BotActionTarget,
} from '../services/bot-decision';
import { CommandFactory, CommandHistory } from '../commands';

export interface BotActionHandlerDependencies {
  playerStore: PlayerStore;
  actionStore: ActionStore;
  deckStore: DeckStore;
  botDecisionService: BotDecisionService;
  commandFactory: CommandFactory;
  commandHistory: CommandHistory;
}

/**
 * Handles all bot player action logic.
 * Responsible for:
 * - Making automated decisions for bots
 * - Executing bot actions with appropriate delays
 * - Managing bot AI decision context
 */
export class BotActionHandler {
  private playerStore: PlayerStore;
  private actionStore: ActionStore;
  private deckStore: DeckStore;
  private botDecisionService: BotDecisionService;
  private commandFactory: CommandFactory;
  private commandHistory: CommandHistory;

  constructor(deps: BotActionHandlerDependencies) {
    this.playerStore = deps.playerStore;
    this.actionStore = deps.actionStore;
    this.deckStore = deps.deckStore;
    this.botDecisionService = deps.botDecisionService;
    this.commandFactory = deps.commandFactory;
    this.commandHistory = deps.commandHistory;
  }

  // Action initiation - async execution with AI decision service
  async handlePeekOwnCard(playerId: string): Promise<boolean> {
    await this.simulateBotThinking();
    const context = this.createBotContext(playerId);
    const decision = this.botDecisionService.selectActionTargets(context);

    if (decision.targets.length > 0) {
      return this.executePeekOwnCard(playerId, decision.targets[0].position);
    }
    return false;
  }

  async handlePeekOpponentCard(playerId: string): Promise<boolean> {
    await this.simulateBotThinking();
    const context = this.createBotContext(playerId);
    const decision = this.botDecisionService.selectActionTargets(context);

    if (decision.targets.length > 0) {
      return this.executePeekOpponentCard(
        playerId,
        decision.targets[0].playerId,
        decision.targets[0].position
      );
    }
    return false;
  }

  async handleSwapCards(playerId: string): Promise<boolean> {
    await this.simulateBotThinking();
    const context = this.createBotContext(playerId);
    const decision = this.botDecisionService.selectActionTargets(context);

    if (decision.targets.length >= 2) {
      return await this.executeSwapCards(playerId, decision.targets);
    }
    return false;
  }

  async handlePeekAndSwap(playerId: string): Promise<boolean> {
    await this.simulateBotThinking();
    const context = this.createBotContext(playerId);
    const decision = this.botDecisionService.selectActionTargets(context);

    if (decision.targets.length >= 2) {
      return await this.executePeekAndSwap(playerId, decision.targets, context);
    }
    return false;
  }

  async handleDeclareAction(playerId: string): Promise<boolean> {
    await this.simulateBotThinking();
    const context = this.createBotContext(playerId);
    const decision = this.botDecisionService.selectActionTargets(context);

    if (decision.declaredRank) {
      return this.executeDeclareAction(playerId, decision.declaredRank);
    }
    return false;
  }

  async handleForceDraw(playerId: string): Promise<boolean> {
    await this.simulateBotThinking();
    const context = this.createBotContext(playerId);
    const decision = this.botDecisionService.selectActionTargets(context);

    if (decision.targets.length > 0) {
      return await this.executeForceDraw(playerId, decision.targets[0].playerId);
    }
    return false;
  }

  /**
   * Simulates bot thinking time proportional to decision complexity.
   * Higher difficulty = faster decisions (more confident).
   */
  private async simulateBotThinking(): Promise<void> {
    const player = this.playerStore.players.find(p => p.isBot && p.id);
    const difficulty = player ? 'moderate' : 'moderate'; // Get from context if needed

    const thinkingTime = {
      easy: 1500,     // 1.5 seconds (slower, "thinking")
      moderate: 1000, // 1 second
      hard: 500,      // 0.5 seconds (fast, confident)
    }[difficulty];

    return new Promise((resolve) => setTimeout(resolve, thinkingTime));
  }

  /**
   * Creates bot decision context for AI decisions
   */
  private createBotContext(playerId: string): BotDecisionContext {
    const botPlayer = this.playerStore.getPlayer(playerId);
    if (!botPlayer) throw new Error(`Bot player ${playerId} not found`);

    // Extract opponent knowledge
    const opponentKnowledge = new Map<string, Map<number, Card>>();
    botPlayer.opponentKnowledge.forEach((knowledge, opponentId) => {
      opponentKnowledge.set(opponentId, new Map(knowledge.knownCards));
    });

    // Get current action context if it exists
    const actionContext = this.actionStore.actionContext;
    const currentAction = actionContext
      ? {
          targetType: actionContext.targetType || '',
          card: this.actionStore.pendingCard!,
          peekTargets: this.actionStore.peekTargets.map(pt => ({
            playerId: pt.playerId,
            position: pt.position,
            card: pt.card || undefined,
          })),
        }
      : undefined;

    return {
      botId: playerId,
      difficulty: 'moderate', // Should be passed from game store
      botPlayer,
      allPlayers: this.playerStore.players,
      gameState: {
        players: this.playerStore.players,
        currentPlayerIndex: this.playerStore.players.findIndex(p => p.id === playerId),
        drawPile: this.deckStore.drawPile,
        discardPile: this.deckStore.discardPile,
        phase: 'playing',
        gameId: 'current',
        roundNumber: 1,
        turnCount: 0,
        finalTurnTriggered: false,
      },
      opponentKnowledge,
      currentAction,
    };
  }

  // Execution methods with bot decision logic
  private executePeekOwnCard(playerId: string, position: number): boolean {
    const player = this.playerStore.getPlayer(playerId);
    if (!player || player.isHuman) return false;

    if (position >= 0 && position < player.cards.length) {
      // Highlight the card being peeked
      this.playerStore.highlightCard(playerId, position);

      // For bots, permanently add to known cards for AI decision-making
      this.playerStore.addKnownCardPosition(playerId, position);
      return true;
    }
    return false;
  }

  private executePeekOpponentCard(
    playerId: string,
    opponentId: string,
    position: number
  ): boolean {
    const player = this.playerStore.getPlayer(playerId);
    const opponent = this.playerStore.getPlayer(opponentId);

    if (!player || !opponent || position < 0 || position >= opponent.cards.length) {
      return false;
    }

    // Highlight the card being peeked
    this.playerStore.highlightCard(opponentId, position);

    // Record opponent's card in bot's knowledge
    const peekedCard = opponent.cards[position];
    this.playerStore.recordOpponentCard(playerId, opponentId, position, peekedCard);

    GameToastService.success(
      `${player.name} peeked at ${opponent.name}'s position ${position + 1}`
    );

    return true;
  }

  private async executeSwapCards(
    playerId: string,
    targets: BotActionTarget[]
  ): Promise<boolean> {
    if (targets.length < 2) return false;

    const [target1, target2] = targets;
    const player1 = this.playerStore.getPlayer(target1.playerId);
    const player2 = this.playerStore.getPlayer(target2.playerId);
    const botPlayer = this.playerStore.getPlayer(playerId);

    if (!player1 || !player2 || !botPlayer) return false;

    // Highlight both cards being swapped
    this.playerStore.highlightCard(target1.playerId, target1.position);
    this.playerStore.highlightCard(target2.playerId, target2.position);

    // Execute swap using command
    const swapCommand = this.commandFactory.swapCards(
      target1.playerId,
      target1.position,
      target2.playerId,
      target2.position
    );
    const result = await this.commandHistory.executeCommand(swapCommand);

    if (result.success) {
      GameToastService.success(
        `${botPlayer.name} swapped ${player1.name}'s card ${
          target1.position + 1
        } with ${player2.name}'s card ${target2.position + 1}`
      );

      // Invalidate knowledge for swapped cards (blind swap - bot doesn't know what was swapped)
      this.invalidateSwapKnowledge(target1, target2);
    }

    return result.success;
  }

  /**
   * Invalidate opponent knowledge when cards are swapped blindly (Jack action)
   */
  private invalidateSwapKnowledge(
    target1: BotActionTarget,
    target2: BotActionTarget
  ): void {
    // All bots lose knowledge of the swapped positions
    this.playerStore.botPlayers.forEach((bot) => {
      // Clear knowledge for target1
      this.playerStore.clearOpponentCardKnowledge(
        bot.id,
        target1.playerId,
        target1.position
      );
      // Clear knowledge for target2
      this.playerStore.clearOpponentCardKnowledge(
        bot.id,
        target2.playerId,
        target2.position
      );
    });
  }

  private async executePeekAndSwap(
    playerId: string,
    targets: BotActionTarget[],
    context: BotDecisionContext
  ): Promise<boolean> {
    if (targets.length < 2) return false;

    const [target1, target2] = targets;
    const player1 = this.playerStore.getPlayer(target1.playerId);
    const player2 = this.playerStore.getPlayer(target2.playerId);
    const card1 = player1?.cards[target1.position];
    const card2 = player2?.cards[target2.position];

    if (!card1 || !card2) return false;

    const botPlayer = this.playerStore.getPlayer(playerId);
    if (!botPlayer) return false;

    // Record in bot's knowledge if peeking opponents
    if (target1.playerId !== playerId) {
      this.playerStore.recordOpponentCard(playerId, target1.playerId, target1.position, card1);
    }
    if (target2.playerId !== playerId) {
      this.playerStore.recordOpponentCard(playerId, target2.playerId, target2.position, card2);
    }

    // Highlight both peeked cards
    this.playerStore.highlightCard(target1.playerId, target1.position);
    this.playerStore.highlightCard(target2.playerId, target2.position);

    GameToastService.info(
      `${botPlayer.name} peeked at two cards: Player ${player1.name} (position ${
        target1.position + 1
      }) and Player ${player2.name} (position ${target2.position + 1})`
    );

    // AI decides whether to swap using decision service
    const shouldSwap = this.botDecisionService.shouldSwapAfterPeek([card1, card2], context);

    if (shouldSwap) {
      // Execute swap using command
      const swapCommand = this.commandFactory.swapCards(
        target1.playerId,
        target1.position,
        target2.playerId,
        target2.position
      );
      const result = await this.commandHistory.executeCommand(swapCommand);

      if (result.success) {
        GameToastService.success(
          `${botPlayer.name}: Queen action - Swapped ${card1.rank} with ${card2.rank}`
        );

        // Invalidate knowledge for swapped cards (for other bots who didn't see the swap)
        this.invalidateSwapKnowledge(target1, target2);
      }

      return result.success;
    } else {
      GameToastService.info(
        `${botPlayer.name}: Queen action - Chose not to swap the peeked cards`
      );
      return true;
    }
  }

  private executeDeclareAction(playerId: string, rank: Rank): boolean {
    const player = this.playerStore.getPlayer(playerId);
    if (!player) return false;

    this.actionStore.declareKingAction(rank);

    const declaredAction =
      this.actionStore.actionContext?.action || 'Unknown action';

    GameToastService.success(
      `${player.name} declared King as ${rank} - ${declaredAction}`
    );
    return true;
  }

  private async executeForceDraw(playerId: string, targetId: string): Promise<boolean> {
    const player = this.playerStore.getPlayer(playerId);
    const target = this.playerStore.getPlayer(targetId);

    if (!player || !target) return false;

    if (!this.deckStore.hasDrawCards) {
      this.deckStore.ensureDrawCards();
    }

    // Add penalty card using command
    const penaltyCommand = this.commandFactory.addPenaltyCard(targetId);
    const result = await this.commandHistory.executeCommand(penaltyCommand);

    if (result.success) {
      GameToastService.success(
        `${player.name} forced ${target.name} to draw a card. ${target.name} now has ${target.cards.length} cards.`
      );
      return true;
    }
    return false;
  }

  // Enhanced execution with bot decision service
  async executeWithDecisionService(
    card: Card,
    playerId: string,
    context: BotDecisionContext
  ): Promise<void> {
    const decision = this.botDecisionService.selectActionTargets(context);

    if (!decision.targets || decision.targets.length === 0) {
      console.warn('Bot decision service returned no targets');
      return;
    }

    // Apply the decision targets
    for (let i = 0; i < decision.targets.length; i++) {
      const target = decision.targets[i];

      // Execute target selection based on action type
      if (card.rank === 'J') {
        this.actionStore.addSwapTarget(target.playerId, target.position);
      } else if (card.rank === 'Q') {
        const targetPlayer = this.playerStore.getPlayer(target.playerId);
        const targetCard = targetPlayer?.cards[target.position];
        if (targetCard) {
          this.actionStore.addPeekTarget(
            target.playerId,
            target.position,
            targetCard
          );
        }
      }
    }

    // Handle special decision types
    if (decision.declaredRank && card.rank === 'K') {
      this.actionStore.declareKingAction(decision.declaredRank);
    }

    // Handle peek-then-swap decisions
    if (card.rank === 'Q' && this.actionStore.hasCompletePeekSelection) {
      const shouldSwap = this.botDecisionService.shouldSwapAfterPeek(
        this.actionStore.peekTargets.map((t) => t.card!),
        context
      );

      const botPlayer = this.playerStore.getPlayer(playerId);

      if (shouldSwap) {
        const targets = this.actionStore.peekTargets;
        const [peek1, peek2] = targets;

        // Execute swap using command
        const swapCommand = this.commandFactory.swapCards(
          peek1.playerId,
          peek1.position,
          peek2.playerId,
          peek2.position
        );
        const result = await this.commandHistory.executeCommand(swapCommand);

        if (result.success && botPlayer) {
          GameToastService.success(
            `${botPlayer.name}: Queen action - Swapped ${
              peek1.card!.rank
            } with ${peek2.card!.rank}`
          );
        }
      } else if (botPlayer) {
        GameToastService.info(
          `${botPlayer.name}: Queen action - Chose not to swap the peeked cards`
        );
      }
    }
  }
}

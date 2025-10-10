// client/adapters/botAIAdapter.ts
// Adapter to integrate existing MCTS Bot AI with new GameClient architecture

import { reaction } from 'mobx';

import { GameActions } from '@/engine';
import { Card } from '@/shared';
import {
  BotDecisionService,
  BotDecisionServiceFactory,
  BotTurnDecision,
  BotDecisionContext,
  BotActionDecision,
} from '../bot/mcts-bot-decision';
import { GameClient } from '../game-client';

/**
 * BotAIAdapter - Bridges MCTS Bot AI with GameClient
 *
 * This adapter allows the existing MCTS bot AI to work with the new
 * GameClient architecture with ZERO changes to the MCTS algorithm itself.
 *
 * Key approach:
 * 1. Convert engine state to bot context (what bot needs to decide)
 * 2. Use existing MCTS bot to make decision
 * 3. Convert bot decision to GameAction
 * 4. Dispatch action via GameClient
 * 5. React to state changes via MobX reactions (no setTimeout!)
 *
 * TODO: Unified Player Handler
 * This adapter is temporary. The plan is to create a unified PlayerActionHandler
 * that works for both bots and humans. The handler will:
 * - Accept player ID (bot or human)
 * - For bots: Use MCTS to make decision, auto-dispatch
 * - For humans: Wait for user input, then dispatch
 * - Both use same GameActions, same state flow
 * This will eliminate code duplication and simplify the architecture.
 */
export class BotAIAdapter {
  private botDecisionService: BotDecisionService;
  private disposeReaction?: () => void;

  constructor(
    private gameClient: GameClient
  ) {
    // Use existing bot decision service factory
    this.botDecisionService = BotDecisionServiceFactory.create(this.gameClient.state.difficulty);

    // Setup reactive bot turn execution
    this.setupBotReaction();
  }

  /**
   * Setup MobX reaction to automatically execute bot turns
   * This eliminates the need for setTimeout and ensures proper async handling
   */
  private setupBotReaction(): void {
    this.disposeReaction = reaction(
      // Watch for bot turn state
      () => ({
        isBot: this.gameClient.currentPlayer.isBot,
        subPhase: this.gameClient.state.subPhase,
        turnCount: this.gameClient.state.turnCount,
      }),
      // Execute bot turn when it's a bot's turn
      ({ isBot, subPhase }) => {
        if (isBot && subPhase !== 'idle' && subPhase !== 'ai_thinking') {
          // Use setTimeout with void to avoid Promise-in-callback error
          setTimeout(() => void this.executeBotTurn(), 500);
        }
      }
    );
  }

  /**
   * Cleanup reactions when adapter is destroyed
   */
  dispose(): void {
    this.disposeReaction?.();
  }

  /**
   * Make bot execute its turn
   *
   * This is the main entry point called when it's a bot's turn
   */
  async executeBotTurn(): Promise<void> {
    const currentPlayer = this.gameClient.currentPlayer;

    if (!currentPlayer.isBot) {
      console.warn('[BotAI] executeBotTurn called for non-bot player');
      return;
    }

    const botId = currentPlayer.id;
    console.log(`[BotAI] ${botId} starting turn`);

    // Execute turn in phases based on game subPhase
    const subPhase = this.gameClient.state.subPhase;

    try {
      switch (subPhase) {
        case 'ai_thinking':
        case 'idle':
          await this.executeTurnDecision(botId);
          break;

        case 'choosing':
          await this.executeSwapDecision(botId);
          break;

        case 'selecting':
          await this.executeActionDecision(botId);
          break;

        case 'awaiting_action':
          await this.executeTargetSelection(botId);
          break;

        default:
          console.warn(`[BotAI] Unknown subPhase: ${subPhase}`);
      }
    } catch (error) {
      console.error('[BotAI] Error executing bot turn:', error);
    }
  }

  /**
   * Phase 1: Draw or Take Discard
   */
  private async executeTurnDecision(botId: string): Promise<void> {
    await this.delay(800); // Simulate thinking time

    const context = this.createBotContext(botId);
    const decision: BotTurnDecision =
      this.botDecisionService.decideTurnAction(context);

    if (decision.action === 'take-discard') {
      this.gameClient.dispatch(GameActions.takeDiscard(botId));
      console.log(`[BotAI] ${botId} took from discard`);
    } else {
      this.gameClient.dispatch(GameActions.drawCard(botId));
      console.log(`[BotAI] ${botId} drew card`);
    }

    // State will update and MobX reaction will trigger next phase
  }

  /**
   * Phase 2: Swap Card into Hand
   */
  private async executeSwapDecision(botId: string): Promise<void> {
    await this.delay(1000);

    const context = this.createBotContext(botId);
    const drawnCard = this.gameClient.pendingCard;

    if (!drawnCard) {
      console.warn('[BotAI] No pending card to swap');
      return;
    }

    // Use MCTS to decide best swap position
    const swapPosition = this.botDecisionService.selectBestSwapPosition(
      drawnCard,
      context
    );

    if (swapPosition !== null) {
      this.gameClient.dispatch(GameActions.swapCard(botId, swapPosition));
      console.log(`[BotAI] ${botId} swapped at position ${swapPosition}`);
    }

    // State will update and MobX reaction will trigger next phase
  }

  /**
   * Phase 3: Use Action or Discard
   */
  private async executeActionDecision(botId: string): Promise<void> {
    await this.delay(1000);

    const context = this.createBotContext(botId);
    const cardInHand = this.gameClient.pendingCard;

    if (!cardInHand) {
      console.warn('[BotAI] No pending card for action decision');
      return;
    }

    // Use MCTS to decide: use action or discard?
    const shouldUseAction = this.botDecisionService.shouldUseAction(
      cardInHand,
      context
    );

    if (shouldUseAction && cardInHand.rank !== '2' && cardInHand.rank !== '3') {
      // Has action - use it
      this.gameClient.dispatch(GameActions.playCardAction(botId, cardInHand));
      console.log(`[BotAI] ${botId} using ${cardInHand.rank} action`);
      // State will update and MobX reaction will trigger target selection
    } else {
      // No action or chose not to use - discard
      this.gameClient.dispatch(GameActions.discardCard(botId));
      console.log(`[BotAI] ${botId} discarded ${cardInHand.rank}`);

      // Turn complete - advance turn after delay
      await this.delay(500);
      this.gameClient.dispatch(GameActions.advanceTurn());
    }
  }

  /**
   * Phase 4: Select Action Targets (for card actions)
   */
  private async executeTargetSelection(botId: string): Promise<void> {
    await this.delay(1200);

    const context = this.createBotContext(botId);
    const actionCard = this.gameClient.pendingCard;

    if (!actionCard) {
      console.warn('[BotAI] No action card for target selection');
      return;
    }

    // Special handling for different card types
    if (actionCard.rank === 'K') {
      // King: Declare a rank
      await this.executeKingDeclaration(botId, context);
    } else if (actionCard.rank === 'Q') {
      // Queen: Select 2 cards to peek/swap
      await this.executeQueenAction(botId, context);
    } else {
      // Other actions: Select target
      await this.executeStandardAction(botId, context);
    }
  }

  /**
   * King card: Declare a rank for toss-in
   */
  private async executeKingDeclaration(
    botId: string,
    context: BotDecisionContext
  ): Promise<void> {
    const declaredRank = this.botDecisionService.selectKingDeclaration(context);

    this.gameClient.dispatch(
      GameActions.declareKingAction(botId, declaredRank)
    );
    console.log(`[BotAI] ${botId} declared King action: ${declaredRank}`);

    // Turn complete - advance turn after delay
    await this.delay(500);
    this.gameClient.dispatch(GameActions.advanceTurn());
  }

  /**
   * Queen card: Peek at 2 cards, optionally swap them
   */
  private async executeQueenAction(
    botId: string,
    context: BotDecisionContext
  ): Promise<void> {
    const decision: BotActionDecision =
      this.botDecisionService.selectActionTargets(context);

    if (decision.targets.length >= 2) {
      // Select first target
      this.gameClient.dispatch(
        GameActions.selectActionTarget(
          botId,
          decision.targets[0].playerId,
          decision.targets[0].position
        )
      );

      // Delay before second target
      await this.delay(800);

      // Select second target
      this.gameClient.dispatch(
        GameActions.selectActionTarget(
          botId,
          decision.targets[1].playerId,
          decision.targets[1].position
        )
      );

      // Delay before swap decision
      await this.delay(800);

      // Decide whether to swap
      if (decision.shouldSwap) {
        this.gameClient.dispatch(GameActions.executeQueenSwap(botId));
        console.log(`[BotAI] ${botId} executed Queen swap`);
      } else {
        this.gameClient.dispatch(GameActions.skipQueenSwap(botId));
        console.log(`[BotAI] ${botId} skipped Queen swap`);
      }

      // Turn complete - advance turn after delay
      await this.delay(500);
      this.gameClient.dispatch(GameActions.advanceTurn());
    }
  }

  /**
   * Standard actions (7, 8, 9, 10, J, A): Select target and execute
   */
  private async executeStandardAction(
    botId: string,
    context: BotDecisionContext
  ): Promise<void> {
    const decision: BotActionDecision =
      this.botDecisionService.selectActionTargets(context);

    if (decision.targets.length > 0) {
      const target = decision.targets[0];

      this.gameClient.dispatch(
        GameActions.selectActionTarget(botId, target.playerId, target.position)
      );

      console.log(
        `[BotAI] ${botId} selected target: ${target.playerId} pos ${target.position}`
      );

      // For peek actions, confirm after seeing
      await this.delay(1000);
      this.gameClient.dispatch(GameActions.confirmPeek(botId));

      // Turn complete - advance turn after delay
      await this.delay(500);
      this.gameClient.dispatch(GameActions.advanceTurn());
    }
  }

  /**
   * Create bot decision context from current game state
   *
   * Uses the engine's GameState directly - no conversion needed
   */
  private createBotContext(botId: string): BotDecisionContext {
    const state = this.gameClient.state;

    const botPlayer = state.players.find((p) => p.id === botId);
    if (!botPlayer) {
      throw new Error(`Bot player ${botId} not found`);
    }

    // Extract opponent knowledge from bot player
    const opponentKnowledge = new Map<string, Map<number, Card>>();
    // TODO: This would be populated from bot memory in full implementation

    return {
      botId,      
      botPlayer,
      allPlayers: state.players,
      gameState: state, // Use engine GameState directly - it already has all required fields
      discardTop: this.gameClient.topDiscardCard,
      discardPile: state.discardPile,
      pendingCard: this.gameClient.pendingCard,
      opponentKnowledge,
    };
  }

  /**
   * Delay helper for bot "thinking" time
   */
  private delay(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  /**
   * Check if bot should call Vinto
   * Note: This is synchronous but returns Promise for API consistency
   */
  checkVintoCall(botId: string): boolean {
    const context = this.createBotContext(botId);
    return this.botDecisionService.shouldCallVinto(context);
  }

  /**
   * Execute Vinto call if bot decides to
   */
  maybeCallVinto(botId: string): void {
    const shouldCall = this.checkVintoCall(botId);

    if (shouldCall) {
      this.gameClient.dispatch(GameActions.callVinto(botId));
      console.log(`[BotAI] ${botId} called Vinto!`);
    }
  }
}

/**
 * Factory function to create bot AI adapter
 * Difficulty is read from gameClient.state.difficulty
 */
export function createBotAI(
  gameClient: GameClient
): BotAIAdapter {
  return new BotAIAdapter(gameClient);
}

// client/adapters/botAIAdapter.ts
// Adapter to integrate existing MCTS Bot AI with new GameClient architecture

import { reaction } from 'mobx';

import { GameActions } from '@/engine';
import { Card, logger } from '@/shared';
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
  private isHandlingTossIn = false;
  // Cache action plan when bot commits to take-discard with action card
  // This ensures consistency: bot won't change its mind about using the action
  private cachedActionDecision: BotActionDecision | null = null;

  constructor(private gameClient: GameClient) {
    // Use existing bot decision service factory
    this.botDecisionService = BotDecisionServiceFactory.create(
      this.gameClient.state.difficulty
    );

    // Setup reactive bot turn execution
    this.setupBotReaction();
  }

  /**
   * Setup MobX reaction to automatically execute bot turns
   * CRITICAL FIX: Eliminated setTimeout from core reaction logic
   *
   * The reaction now watches for the single, unambiguous state:
   * "isBot && subPhase === 'idle'" - meaning it's a bot's turn and the game is ready.
   *
   * After dispatching an action, methods simply return. The reaction will
   * automatically fire again when the game state transitions, creating a
   * robust, self-triggering loop that is perfectly synchronized with the game state.
   */
  private setupBotReaction(): void {
    this.disposeReaction = reaction(
      // Watch for bot turn state - EXPLICIT state watching only
      () => ({
        isBot: this.gameClient.currentPlayer.isBot,
        subPhase: this.gameClient.state.subPhase,
        turnCount: this.gameClient.state.turnCount,
        activeTossIn: this.gameClient.state.activeTossIn,
      }),
      // Execute bot turn when state is ready
      // Note: Using fire-and-forget pattern here is intentional.
      // Reactions should not await - they trigger async operations that will
      // naturally loop back through state changes. Error handling occurs within methods.
      ({ isBot, subPhase, activeTossIn }) => {
        console.log(
          `[BotAI] Reaction fired: subPhase=${subPhase}, isBot=${isBot}, activeTossIn=${!!activeTossIn}`
        );

        // Handle toss-in phase separately (all bot players participate)
        if (subPhase === 'toss_queue_active' && activeTossIn) {
          if (this.isHandlingTossIn) {
            // Already processing toss-in - avoid re-entrancy
            console.log('[BotAI] Already handling toss-in, skipping');
            return;
          }
          console.log('[BotAI] Triggering toss-in phase handling');
          // Fire-and-forget: Let the async operation run independently
          this.handleTossInPhase().catch((error) => {
            console.error('[BotAI] Error in toss-in phase:', error);
          });
          return;
        }

        // Only execute bot logic when it's a bot's turn
        if (!isBot) {
          return;
        }

        // Execute bot turn based on current subPhase
        // Fire-and-forget: Let the async operation run independently
        this.executeBotTurn().catch((error) => {
          console.error('[BotAI] Error executing bot turn:', error);
        });
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
   * CRITICAL FIX: Removed setTimeout and arbitrary delays from flow control.
   * Each method dispatches an action and returns immediately.
   * The MobX reaction will automatically fire again when state transitions.
   * This creates a self-triggering loop synchronized with the game state.
   */
  async executeBotTurn(): Promise<void> {
    const currentPlayer = this.gameClient.currentPlayer;

    if (!currentPlayer.isBot) {
      logger.warn('[BotAI] executeBotTurn called for non-bot player', {
        playerId: currentPlayer.id,
        playerName: currentPlayer.name,
      });
      return;
    }

    const botId = currentPlayer.id;
    const subPhase = this.gameClient.state.subPhase;

    console.log(`[BotAI] ${botId} executing phase: ${subPhase}`);

    // Add "thinking time" delay for better UX (optional, visual only)
    // Skip delay if we're continuing from taking discard (awaiting_action after play_discard)
    const skipDelay =
      subPhase === 'awaiting_action' &&
      this.gameClient.state.pendingAction?.actionPhase === 'selecting-target';

    if (!skipDelay) {
      await this.delay(3_000);
    }

    try {
      switch (subPhase) {
        case 'ai_thinking':
        case 'idle':
          // Phase 1: Draw or take discard
          this.executeTurnDecision(botId);
          // Returns immediately - reaction will fire when subPhase -> 'choosing'
          break;

        case 'choosing':
          // Phase 2: Swap card into hand
          this.executeSwapDecision(botId);
          // Returns immediately - reaction will fire when subPhase -> 'selecting'
          break;

        case 'selecting':
          // Phase 3: Use action or discard
          this.executeActionDecision(botId);
          // Returns immediately - reaction will fire based on action choice
          break;

        case 'awaiting_action':
          // Phase 4: Select action targets
          await this.executeTargetSelection(botId);
          // Returns after target selection complete
          break;

        case 'toss_queue_active':
          // Handled separately by handleTossInPhase
          break;

        default:
          logger.warn(`[BotAI] Unhandled subPhase: ${subPhase}`, {
            botId,
            subPhase,
            phase: this.gameClient.state.phase,
          });
      }
    } catch (error) {
      console.error('[BotAI] Error executing bot turn:', error);
    }
  }

  /**
   * Handle toss-in phase for all bot players
   * MAJOR FIX: Fully implemented toss-in logic using MCTS decision service
   *
   * This method now:
   * 1. Iterates through ALL bot players (not just current player)
   * 2. Creates BotDecisionContext for each bot
   * 3. Calls shouldParticipateInTossIn for intelligent decisions
   * 4. Dispatches tossInCard actions for matching cards
   * 5. Properly manages sequential toss-in for multiple bots
   */
  private async handleTossInPhase(): Promise<void> {
    const activeTossIn = this.gameClient.state.activeTossIn;
    if (!activeTossIn) {
      logger.warn('[BotAI] handleTossInPhase called but no active toss-in', {
        subPhase: this.gameClient.state.subPhase,
        phase: this.gameClient.state.phase,
      });
      return;
    }

    this.isHandlingTossIn = true;
    try {
      const tossInRank = activeTossIn.rank;
      console.log(`[BotAI] Toss-in phase for rank: ${tossInRank}`);

      // Give bots time to "think" about toss-in
      await this.delay(800);

      // Process each bot player
      const botPlayers = this.gameClient.state.players.filter((p) => p.isBot);
      console.log(
        `[BotAI] Found ${botPlayers.length} bot players to process for toss-in`
      );

      for (const botPlayer of botPlayers) {
        const botId = botPlayer.id;

        // Skip if bot has already finished toss-in
        if (activeTossIn.playersReadyForNextTurn.includes(botId)) {
          console.log(`[BotAI] ${botId} already marked ready`);
          continue;
        }

        console.log(
          `[BotAI] Processing toss-in for bot ${botId}, cards:`,
          botPlayer.cards.map((c) => c.rank)
        );

        // Create decision context for this bot
        const context = this.createBotContext(botId);

        // Ask MCTS if bot should participate in toss-in
        const shouldParticipate =
          this.botDecisionService.shouldParticipateInTossIn(
            tossInRank,
            context
          );

        if (shouldParticipate) {
          // Find all matching cards in bot's hand
          const matchingCards = botPlayer.cards.filter(
            (card) => card.rank === tossInRank
          );

          if (matchingCards.length > 0) {
            console.log(
              `[BotAI] ${botId} tossing in ${matchingCards.length} ${tossInRank}(s)`
            );

            // Toss in each matching card sequentially
            for (const card of matchingCards) {
              const cardPosition = botPlayer.cards.indexOf(card);
              if (cardPosition >= 0) {
                this.gameClient.dispatch(
                  GameActions.participateInTossIn(botId, cardPosition)
                );

                // Small delay between multiple toss-ins for visual clarity
                await this.delay(400);
              }
            }
          } else {
            console.log(
              `[BotAI] ${botId} wants to toss in but has no matching ${tossInRank}`
            );
          }
        } else {
          console.log(`[BotAI] ${botId} chose not to participate in toss-in`);
        }

        // Mark bot as ready for next turn
        this.gameClient.dispatch(GameActions.playerTossInFinished(botId));

        // Delay between bot confirmations for proper sequencing
        await this.delay(300);
      }

      console.log('[BotAI] All bots processed for toss-in phase');
    } finally {
      this.isHandlingTossIn = false;
    }
  }

  /**
   * Phase 1: Draw or Take Discard
   */
  private executeTurnDecision(botId: string): void {
    const context = this.createBotContext(botId);
    const decision: BotTurnDecision =
      this.botDecisionService.decideTurnAction(context);

    if (decision.action === 'take-discard') {
      // Cache the action plan if bot committed to using the action
      if (decision.actionDecision) {
        this.cachedActionDecision = decision.actionDecision;
        console.log(
          `[BotAI] ${botId} cached action plan from take-discard decision`
        );
      }

      this.gameClient.dispatch(GameActions.playDiscard(botId));
      console.log(`[BotAI] ${botId} took from discard`);
    } else {
      // Clear cache when drawing (fresh decision needed)
      this.cachedActionDecision = null;
      this.gameClient.dispatch(GameActions.drawCard(botId));
      console.log(`[BotAI] ${botId} drew card`);
    }

    // State will update and MobX reaction will trigger next phase
  }

  /**
   * Phase 2: Swap Card into Hand
   */
  private executeSwapDecision(botId: string): void {
    const context = this.createBotContext(botId);
    const drawnCard = this.gameClient.pendingCard;

    if (!drawnCard) {
      logger.warn('[BotAI] No pending card to swap', {
        botId,
        subPhase: this.gameClient.state.subPhase,
      });
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
  private executeActionDecision(botId: string): void {
    const context = this.createBotContext(botId);
    const cardInHand = this.gameClient.pendingCard;

    if (!cardInHand) {
      logger.warn('[BotAI] No pending card for action decision', {
        botId,
        subPhase: this.gameClient.state.subPhase,
      });
      return;
    }

    // Use MCTS to decide: use action or discard?
    const shouldUseAction = this.botDecisionService.shouldUseAction(
      cardInHand,
      context
    );

    if (shouldUseAction && cardInHand.actionText) {
      // Has action - use it
      this.gameClient.dispatch(GameActions.playCardAction(botId, cardInHand));
      console.log(`[BotAI] ${botId} using ${cardInHand.rank} action`);
      // State will update and MobX reaction will trigger target selection
    } else {
      // No action or chose not to use - discard
      this.gameClient.dispatch(GameActions.discardCard(botId));
      console.log(`[BotAI] ${botId} discarded ${cardInHand.rank}`);

      // Discard transitions to toss-in phase, no need to advance turn here
      // handleTossInPhase will handle turn advancement after toss-in completes
    }
  }

  /**
   * Phase 4: Select Action Targets (for card actions)
   */
  private async executeTargetSelection(botId: string): Promise<void> {
    const context = this.createBotContext(botId);
    const actionCard = this.gameClient.pendingCard;

    if (!actionCard) {
      logger.warn('[BotAI] No action card for target selection', {
        botId,
        subPhase: this.gameClient.state.subPhase,
      });
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
      this.executeStandardAction(botId, context);
    }
  }

  /**
   * King card: Two-step process
   * 1. Select a card from hand or opponent's hand
   * 2. Declare the rank of that card
   */
  private async executeKingDeclaration(
    botId: string,
    context: BotDecisionContext
  ): Promise<void> {
    const actionPhase = this.gameClient.state.pendingAction?.actionPhase;

    if (actionPhase === 'selecting-king-card') {
      // Step 1: Select a card target
      const decision: BotActionDecision =
        this.botDecisionService.selectActionTargets(context);

      if (decision.targets.length > 0) {
        const target = decision.targets[0];

        this.gameClient.dispatch(
          GameActions.selectKingCardTarget(
            botId,
            target.playerId,
            target.position
          )
        );

        console.log(
          `[BotAI] ${botId} selected card for King: ${target.playerId} pos ${target.position}`
        );

        // Small delay before declaring rank
        await this.delay(500);
      }

      // State will update and MobX reaction will trigger declaring-rank phase
    } else if (actionPhase === 'declaring-rank') {
      // Step 2: Declare the rank
      const declaredRank =
        this.botDecisionService.selectKingDeclaration(context);

      this.gameClient.dispatch(
        GameActions.declareKingAction(botId, declaredRank)
      );
      console.log(`[BotAI] ${botId} declared King action: ${declaredRank}`);

      // King action transitions to toss-in phase (if correct) or idle (if incorrect)
      // handleTossInPhase will handle turn advancement after toss-in completes (if toss-in triggered)
    }
  }

  /**
   * Queen card: Peek at 2 cards, optionally swap them
   */
  private async executeQueenAction(
    botId: string,
    context: BotDecisionContext
  ): Promise<void> {
    // Use cached action plan if available, otherwise run MCTS fresh
    const decision: BotActionDecision = this.cachedActionDecision
      ? this.cachedActionDecision
      : this.botDecisionService.selectActionTargets(context);

    // Clear cache after using
    this.cachedActionDecision = null;

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

      // Queen action transitions to toss-in phase
      // handleTossInPhase will handle turn advancement after toss-in completes
    }
  }

  /**
   * Standard actions (7, 8, 9, 10, J, A): Select target and execute
   */
  private executeStandardAction(
    botId: string,
    context: BotDecisionContext
  ): void {
    // Use cached action plan if available, otherwise run MCTS fresh
    const decision: BotActionDecision = this.cachedActionDecision
      ? this.cachedActionDecision
      : this.botDecisionService.selectActionTargets(context);

    // Clear cache after using
    this.cachedActionDecision = null;

    if (decision.targets.length > 0) {
      const target = decision.targets[0];

      this.gameClient.dispatch(
        GameActions.selectActionTarget(botId, target.playerId, target.position)
      );

      console.log(
        `[BotAI] ${botId} selected target: ${target.playerId} pos ${target.position}`
      );

      this.gameClient.dispatch(GameActions.confirmPeek(botId));

      // Card actions (J, A, 7-10) transition to toss-in phase
      // handleTossInPhase will handle turn advancement after toss-in completes
    }
  }

  /**
   * Create bot decision context from current game state
   *
   * MAJOR FIX: Properly populate opponentKnowledge from PlayerState
   *
   * The MCTS service relies heavily on imperfect information (what the bot
   * knows about opponents' cards) to make intelligent decisions. This method
   * now correctly reads the opponentKnowledge data from the GameState's
   * PlayerState and feeds it to the MCTS algorithm.
   */
  private createBotContext(botId: string): BotDecisionContext {
    const state = this.gameClient.state;

    const botPlayer = state.players.find((p) => p.id === botId);
    if (!botPlayer) {
      throw new Error(`Bot player ${botId} not found`);
    }

    // CRITICAL: Extract opponent knowledge from bot player state
    // This is the data pipeline that feeds the MCTS imperfect information algorithm
    const opponentKnowledge = new Map<string, Map<number, Card>>();

    if (botPlayer.opponentKnowledge) {
      // Convert serialized opponent knowledge to the format MCTS expects
      for (const [opponentId, serializedKnowledge] of Object.entries(
        botPlayer.opponentKnowledge
      )) {
        const knownCardsMap = new Map<number, Card>();

        // Convert Record<number, Card> to Map<number, Card>
        for (const [positionStr, card] of Object.entries(
          serializedKnowledge.knownCards
        )) {
          const position = parseInt(positionStr, 10);
          knownCardsMap.set(position, card);
        }

        if (knownCardsMap.size > 0) {
          opponentKnowledge.set(opponentId, knownCardsMap);
          console.log(
            `[BotAI] ${botId} knows ${knownCardsMap.size} cards for opponent ${opponentId}`
          );
        }
      }
    }

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
export function createBotAI(gameClient: GameClient): BotAIAdapter {
  return new BotAIAdapter(gameClient);
}

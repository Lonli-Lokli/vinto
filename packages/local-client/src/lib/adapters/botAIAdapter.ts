// client/adapters/botAIAdapter.ts
// Adapter to integrate existing MCTS Bot AI with new GameClient architecture

import { reaction } from 'mobx';

import { GameActions } from '@vinto/engine';
import { Card, logger, Rank } from '@vinto/shapes';
import {
  BotDecisionService,
  BotDecisionServiceFactory,
  BotTurnDecision,
  BotDecisionContext,
  BotActionDecision,
} from '@vinto/bot';
import { GameClient } from '../game-client';

type GameClientInstance = InstanceType<typeof GameClient>;
type GameClientState = GameClientInstance['state'];

type ReactionSnapshot = {
  isBot: boolean;
  subPhase: GameClientState['subPhase'];
  activeTossIn: GameClientState['activeTossIn'];
  vintoCallerId: GameClientState['vintoCallerId'];
  coalitionLeaderId: GameClientState['coalitionLeaderId'];
  phase: GameClientState['phase'];
  turnCount: GameClientState['turnNumber'];
  currentPlayerId: GameClientInstance['currentPlayer']['id'];
};

const NORMAL_DELAY = 1_000; // delay before running any MCST action
const LARGE_DELAY = 3_000; // larger delay for showing UI elements
const SMALL_DELAY = 300; // small delay to let UI draw
const FINAL_ROUND_DELAY = 2_000; // slower delay for final round actions (coalition vs Vinto)

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
  private botsProcessedRanks = new Map<string, Set<Rank>>(); // botId -> set of ranks processed
  private lastTossInPlayerIndex = -1;
  // Cache action plan when bot commits to take-discard with action card
  // This ensures consistency: bot won't change its mind about using the action
  private cachedActionDecision: BotActionDecision | null = null;
  private reactionQueue: Promise<void> = Promise.resolve();
  private isDisposed = false;

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
   * Asynchronous work is queued to guarantee sequential execution.
   */
  private setupBotReaction(): void {
    this.disposeReaction = reaction<ReactionSnapshot>(
      // Watch for bot turn state - EXPLICIT state watching only
      () => ({
        isBot: this.gameClient.currentPlayer.isBot,
        subPhase: this.gameClient.state.subPhase,
        turnCount: this.gameClient.state.turnNumber,
        activeTossIn: this.gameClient.state.activeTossIn,
        currentPlayerId: this.gameClient.currentPlayer.id,
        // Watch for Vinto being called to select coalition leader
        vintoCallerId: this.gameClient.state.vintoCallerId,
        coalitionLeaderId: this.gameClient.state.coalitionLeaderId,
        phase: this.gameClient.state.phase,
      }),
      // Execute bot turn when state is ready
      // Reactions stay synchronous; async work is queued for sequential handling.
      (snapshot) => {
        this.queueReactionTask(snapshot);
      }
    );
  }

  private queueReactionTask(snapshot: ReactionSnapshot): void {
    if (this.isDisposed) {
      return;
    }

    this.reactionQueue = this.reactionQueue
      .then(async () => {
        if (this.isDisposed) {
          return;
        }

        try {
          await this.runQueuedReaction(snapshot);
        } catch (error) {
          logger.error('[BotAI] Error processing queued reaction:', error);
        }
      })
      .catch((error) => {
        logger.error('[BotAI] Reaction queue failure:', error);
      });
  }

  private async runQueuedReaction(_snapshot: ReactionSnapshot): Promise<void> {
    const state = this.gameClient.state;
    const isBot = this.gameClient.currentPlayer.isBot;

    // CRITICAL: Handle coalition leader selection when Vinto is called
    if (
      state.phase === 'final' &&
      state.vintoCallerId &&
      !state.coalitionLeaderId &&
      this.allPlayersAreBots()
    ) {
      this.selectCoalitionLeaderForBots();
      return;
    }

    // Handle toss-in phase separately (all bot players participate)
    if (state.subPhase === 'toss_queue_active' && state.activeTossIn) {
      await this.handleTossInPhase();
      return;
    }

    // Only execute bot logic when it's a bot's turn
    if (!isBot) {
      return;
    }

    await this.executeBotTurn();
  }

  /**
   * Cleanup reactions when adapter is destroyed
   */
  dispose(): void {
    this.isDisposed = true;
    this.disposeReaction?.();
  }

  /**
   * Await resolution of currently queued bot reaction tasks (useful in tests)
   */
  async waitForIdle(): Promise<void> {
    await this.reactionQueue;
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
    const botId = currentPlayer.id;
    const subPhase = this.gameClient.state.subPhase;
    const pendingAction = this.gameClient.state.pendingAction;

    if (!currentPlayer.isBot) {
      logger.warn('[BotAI] executeBotTurn called for non-bot player', {
        playerId: currentPlayer.id,
        playerName: currentPlayer.name,
      });
      return;
    }

    if (pendingAction && pendingAction.playerId !== botId) {
      console.log(
        `[BotAI] ${botId} skipping turn execution - pending action is for another player`
      );
      return;
    }

    // Add "thinking time" delay for better UX (optional, visual only)
    // Skip delay if:
    // 1. Continuing from taking discard (awaiting_action after play_discard)
    // 2. Selecting action targets (Queen/Jack/etc) - cards should be revealed immediately
    const skipDelay =
      subPhase === 'awaiting_action' &&
      this.gameClient.state.pendingAction?.actionPhase === 'selecting-target';

    console.log(
      `[BotAI] ${botId} executing turn in subPhase: ${subPhase}, skipDelay: ${skipDelay}`
    );

    if (!skipDelay) {
      // Use longer delay in final round for better visibility of coalition actions
      const isFinalRound = this.gameClient.state.phase === 'final';
      await this.delay(isFinalRound ? FINAL_ROUND_DELAY : 3_000);
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
          // Phase 2: Decide whether to use action, swap, or discard
          await this.executeChoosingDecision(botId);
          // Returns immediately - reaction will fire based on choice
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
      logger.error('[BotAI] Error executing bot turn:', error);
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

    // CRITICAL FIX: Clear processed ranks when we enter a NEW turn's toss-in
    // (originalPlayerIndex changes means we've advanced to a new turn)
    if (this.lastTossInPlayerIndex !== activeTossIn.originalPlayerIndex) {
      console.log(
        `[BotAI] New toss-in turn detected (player ${this.lastTossInPlayerIndex} -> ${activeTossIn.originalPlayerIndex}), clearing processed ranks`
      );
      this.botsProcessedRanks.clear();
      this.lastTossInPlayerIndex = activeTossIn.originalPlayerIndex;
    }

    const tossInRanks = activeTossIn.ranks;
    console.log(`[BotAI] Toss-in phase for ranks: ${tossInRanks.join(',')}`);

    // Give bots time to "think" about toss-in
    await this.delay(NORMAL_DELAY);

    // Process each bot player
    const botPlayers = this.gameClient.state.players.filter((p) => p.isBot);
    console.log(
      `[BotAI] Found ${botPlayers.length} bot players to process for toss-in`
    );

    for (const botPlayer of botPlayers) {
      const botId = botPlayer.id;

      // Re-fetch the latest active toss-in state
      const currentActiveTossIn = this.gameClient.state.activeTossIn;
      if (!currentActiveTossIn) {
        console.log('[BotAI] Toss-in ended during processing, stopping');
        break;
      }

      // Skip if bot has already finished toss-in for this turn
      if (currentActiveTossIn.playersReadyForNextTurn.includes(botId)) {
        console.log(`[BotAI] ${botId} already marked ready`);
        continue;
      }

      // Get the set of ranks this bot has already processed
      if (!this.botsProcessedRanks.has(botId)) {
        this.botsProcessedRanks.set(botId, new Set());
      }
      const processedRanks = this.botsProcessedRanks.get(botId)!;

      // Find NEW ranks that bot hasn't processed yet
      const newRanks = tossInRanks.filter((rank) => !processedRanks.has(rank));

      if (newRanks.length === 0) {
        // Bot has processed all current ranks - safe to mark ready
        const latestActiveTossIn = this.gameClient.state.activeTossIn;
        const currentSubPhase = this.gameClient.state.subPhase;

        if (
          latestActiveTossIn &&
          !latestActiveTossIn.playersReadyForNextTurn.includes(botId) &&
          currentSubPhase === 'toss_queue_active'
        ) {
          console.log(`[BotAI] ${botId} processed all ranks, marking ready`);
          this.gameClient.dispatch(GameActions.playerTossInFinished(botId));
        }
        continue;
      }

      // Process each new rank
      for (const rank of newRanks) {
        console.log(`[BotAI] ${botId} processing new rank: ${rank}`);

        // Create decision context for this bot
        const context = this.createBotContext(botId);

        // Ask MCTS if bot should participate for this specific rank
        const shouldParticipate =
          this.botDecisionService.shouldParticipateInTossIn(
            [rank] as [Rank, ...Rank[]],
            context
          );

        if (shouldParticipate) {
          // Find matching cards for THIS rank only
          const matchingCards = botPlayer.cards
            .map((card, index) =>
              card.rank === rank && botPlayer.knownCardPositions.includes(index)
                ? { card: card, position: index }
                : null
            )
            .filter((item) => item !== null);

          if (matchingCards.length > 0) {
            console.log(
              `[BotAI] ${botId} tossing in ${matchingCards.length} cards for rank ${rank}`
            );

            const positions = matchingCards.map(({ position }) => position);
            this.gameClient.dispatch(
              GameActions.participateInTossIn(
                botId,
                positions as [number, ...number[]]
              )
            );

            await this.delay(SMALL_DELAY * 2);
          } else {
            console.log(
              `[BotAI] ${botId} wants to toss in but has no matching ${rank}`
            );
          }
        } else {
          console.log(
            `[BotAI] ${botId} chose not to participate for rank ${rank}`
          );
        }

        // Mark this rank as processed
        processedRanks.add(rank);
      }

      // After processing new ranks, check if bot should mark ready
      // Bot is ready when they've processed ALL current ranks
      const currentRanks = this.gameClient.state.activeTossIn?.ranks || [];
      const hasProcessedAll = currentRanks.every((rank) =>
        processedRanks.has(rank)
      );

      if (hasProcessedAll) {
        const latestActiveTossIn = this.gameClient.state.activeTossIn;
        const currentSubPhase = this.gameClient.state.subPhase;

        if (
          latestActiveTossIn &&
          !latestActiveTossIn.playersReadyForNextTurn.includes(botId) &&
          currentSubPhase === 'toss_queue_active'
        ) {
          // Check if this bot is the one who just finished their turn
          const isCurrentTurnPlayer = latestActiveTossIn.originalPlayerIndex === 
            this.gameClient.state.players.findIndex(p => p.id === botId);

          // If this is the current turn player, check if they should call Vinto
          if (isCurrentTurnPlayer && !this.gameClient.state.vintoCallerId) {
            const vintoContext = this.createBotContext(botId);
            const shouldCallVinto = this.botDecisionService.shouldCallVinto(vintoContext);

            if (shouldCallVinto) {
              console.log(
                `[BotAI] ${botId} calling Vinto instead of marking ready!`
              );
              this.gameClient.dispatch(GameActions.callVinto(botId));
              return; // Don't mark as ready, Vinto was called
            }
          }

          console.log(
            `[BotAI] ${botId} processed all ranks [${currentRanks.join(
              ','
            )}], marking ready`
          );
          this.gameClient.dispatch(GameActions.playerTossInFinished(botId));
        }
      } else {
        console.log(
          `[BotAI] ${botId} NOT marking ready - still has unprocessed ranks`
        );
      }

      await this.delay(SMALL_DELAY);
    }

    console.log('[BotAI] All bots processed for current toss-in ranks');
  }
  /**
   * Phase 1: Draw or Take Discard
   *
   * COALITION LOGIC:
   * - If this bot is a coalition member but NOT the leader, the leader makes the decision
   * - If this bot IS the coalition leader, it makes decisions for itself
   * - Leader uses its MCTS to evaluate what's best for the coalition as a whole
   */
  private executeTurnDecision(botId: string): void {
    const context = this.createBotContext(botId);

    // CRITICAL: Coalition leader makes decisions for all coalition members
    const effectiveDecisionMakerId = this.getEffectiveDecisionMaker(botId);
    const effectiveContext =
      effectiveDecisionMakerId === botId
        ? context
        : this.createBotContext(effectiveDecisionMakerId);

    if (effectiveDecisionMakerId !== botId) {
      console.log(
        `[BotAI] ${botId} is coalition member - leader ${effectiveDecisionMakerId} making decision`
      );
    }

    const decision: BotTurnDecision =
      this.botDecisionService.decideTurnAction(effectiveContext);

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
   * Phase 2: Decide whether to use action, swap, or discard drawn card
   * This is the 'choosing' phase where bot evaluates the drawn card
   *
   * COALITION LOGIC: Leader makes decision for coalition members
   */
  private async executeChoosingDecision(botId: string): Promise<void> {
    const context = this.createBotContext(botId);

    // Coalition leader decides for coalition members
    const effectiveDecisionMakerId = this.getEffectiveDecisionMaker(botId);
    const effectiveContext =
      effectiveDecisionMakerId === botId
        ? context
        : this.createBotContext(effectiveDecisionMakerId);

    const drawnCard = this.gameClient.pendingCard;

    if (!drawnCard) {
      logger.warn('[BotAI] No pending card in choosing phase', {
        botId,
        subPhase: this.gameClient.state.subPhase,
      });
      return;
    }

    // Add delay so everyone can see the drawn card before bot makes a decision
    // This gives time for the draw animation to complete and for players to see what was drawn
    // Use even longer delay in final round
    const isFinalRound = this.gameClient.state.phase === 'final';
    await this.delay(isFinalRound ? LARGE_DELAY + 1000 : LARGE_DELAY);

    // First, check if the card has an action and if we should use it
    if (drawnCard.actionText) {
      const shouldUseAction = this.botDecisionService.shouldUseAction(
        drawnCard,
        effectiveContext
      );

      if (shouldUseAction) {
        // Use the action immediately
        this.gameClient.dispatch(GameActions.playCardAction(botId));
        console.log(`[BotAI] ${botId} chose to use ${drawnCard.rank} action`);
        return; // State will transition to awaiting_action
      }
    }

    // Bot chose not to use action (or card has no action)
    // Now decide: swap or discard?
    const swapPosition = this.botDecisionService.selectBestSwapPosition(
      drawnCard,
      effectiveContext
    );

    if (swapPosition !== null) {
      // Look at current game state to see what card is at the swap position
      const cardAtPosition = context.botPlayer.cards[swapPosition];

      // If it's an action card and bot knows about it, declare its rank
      const shouldDeclare =
        cardAtPosition.actionText &&
        context.botPlayer.knownCardPositions.includes(swapPosition);
      const declaredRank = shouldDeclare ? cardAtPosition.rank : undefined;

      this.gameClient.dispatch(
        GameActions.swapCard(botId, swapPosition, declaredRank)
      );
      console.log(`[BotAI] ${botId} swapped at position ${swapPosition},
  declared: ${declaredRank || 'none'}`);
    } else {
      // Discard the drawn card
      this.gameClient.dispatch(GameActions.discardCard(botId));
      console.log(`[BotAI] ${botId} discarded ${drawnCard.rank}`);
    }

    // State will update and MobX reaction will trigger next phase
  }

  /**
   * Phase 3: Use Action or Discard
   *
   * COALITION LOGIC: Leader makes decision for coalition members
   */
  private executeActionDecision(botId: string): void {
    const context = this.createBotContext(botId);

    // Coalition leader decides for coalition members
    const effectiveDecisionMakerId = this.getEffectiveDecisionMaker(botId);
    const effectiveContext =
      effectiveDecisionMakerId === botId
        ? context
        : this.createBotContext(effectiveDecisionMakerId);

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
      effectiveContext
    );

    if (shouldUseAction && cardInHand.actionText) {
      // Has action - use it
      this.gameClient.dispatch(GameActions.playCardAction(botId));
      console.log(`[BotAI] ${botId} using ${cardInHand.rank} action`);
      // State will update and MobX reaction will trigger target selection
    } else {
      // No action or chose not to use - discard
      this.gameClient.dispatch(GameActions.confirmPeek(botId));
      console.log(`[BotAI] ${botId} discarded ${cardInHand.rank}`);

      // Discard transitions to toss-in phase, no need to advance turn here
      // handleTossInPhase will handle turn advancement after toss-in completes
    }
  }

  /**
   * Phase 4: Select Action Targets (for card actions)
   *
   * COALITION LOGIC: Leader makes decision for coalition members
   */
  private async executeTargetSelection(botId: string): Promise<void> {
    const context = this.createBotContext(botId);

    // Coalition leader decides for coalition members
    const effectiveDecisionMakerId = this.getEffectiveDecisionMaker(botId);
    const effectiveContext =
      effectiveDecisionMakerId === botId
        ? context
        : this.createBotContext(effectiveDecisionMakerId);

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
      await this.executeKingDeclaration(botId, effectiveContext);
    } else if (actionCard.rank === 'J') {
      // Jack: Select 2 cards to swap
      this.executeJackAction(botId, effectiveContext);
    } else if (actionCard.rank === 'Q') {
      // Queen: Select 2 cards to peek/swap
      this.executeQueenAction(botId, effectiveContext);
    } else {
      // Other actions: Select target (7, 8, 9, 10, A)
      this.executeStandardAction(botId, effectiveContext);
    }
  }

  /**
   * King card: Two-step process
   * 1. Select a card from hand or opponent's hand
   * 2. Declare the rank of that card
   *
   * IMPORTANT: MCTS now generates both target and declaredRank together,
   * so we cache the decision to ensure consistency between phases
   */
  private async executeKingDeclaration(
    botId: string,
    context: BotDecisionContext
  ): Promise<void> {
    const targets = this.gameClient.state.pendingAction?.targets || [];

    if (targets.length === 0) {
      // Step 1: Select a card target
      // Use cached action plan if available, otherwise run MCTS fresh
      const decision: BotActionDecision = this.cachedActionDecision
        ? this.cachedActionDecision
        : this.botDecisionService.selectActionTargets(context);

      if (decision.targets.length > 0) {
        const target = decision.targets[0];

        // Cache the decision for consistency
        this.cachedActionDecision = decision;

        this.gameClient.dispatch(
          GameActions.selectActionTarget(
            botId,
            target.playerId,
            target.position
          )
        );

        console.log(
          `[BotAI] ${botId} selected card for King: ${target.playerId} pos ${target.position}`
        );

        // Small delay before declaring rank
        await this.delay(NORMAL_DELAY);
      } else {
        // No valid moves available - discard the card instead
        console.log(
          `[BotAI] ${botId} cannot use King action (no valid moves), discarding`
        );
        this.gameClient.dispatch(GameActions.confirmPeek(botId));
        this.cachedActionDecision = null;
      }

      // State will update and MobX reaction will trigger step 2 (targets.length === 1)
    } else if (targets.length === 1) {
      // Step 2: Declare the rank by looking at CURRENT game state
      const target = targets[0];

      // Find the target player and look at what card is actually at that  position NOW
      const targetPlayer = context.allPlayers.find(
        (p) => p.id === target.playerId
      );
      if (!targetPlayer) {
        logger.error(`[BotAI] Target player ${target.playerId} not found`);
        return;
      }

      const cardAtPosition = targetPlayer.cards[target.position];
      if (!cardAtPosition) {
        logger.error(
          `[BotAI] No card at ${target.playerId}[${target.position}]`
        );
        return;
      }

      // Declare the rank of the card that's CURRENTLY at that position
      const declaredRank = this.cachedActionDecision?.declaredRank ?? cardAtPosition.rank;
      this.cachedActionDecision = null;

      this.gameClient.dispatch(
        GameActions.declareKingAction(botId, declaredRank)
      );
      console.log(
        `[BotAI] ${botId} declared King action: ${declaredRank} at ${target.playerId}[${target.position}]`
      );
    }
  }

  /**
   * Jack card: Optionally swap two cards
   */
  private executeJackAction(botId: string, context: BotDecisionContext): void {
    const pendingAction = this.gameClient.state.pendingAction;
    const targetsSelected = pendingAction?.targets?.length || 0;

    if (targetsSelected === 0) {
      // Step 1: Select first target
      // Use cached action plan if available, otherwise run MCTS fresh
      const decision: BotActionDecision = this.cachedActionDecision
        ? this.cachedActionDecision
        : this.botDecisionService.selectActionTargets(context);

      // Cache the decision for subsequent steps
      this.cachedActionDecision = decision;

      if (decision.targets.length >= 2) {
        this.gameClient.dispatch(
          GameActions.selectActionTarget(
            botId,
            decision.targets[0].playerId,
            decision.targets[0].position
          )
        );
        console.log(
          `[BotAI] ${botId} selected first Jack target: ${decision.targets[0].playerId} pos ${decision.targets[0].position}`
        );
      } else {
        // No valid moves available - discard the card instead
        console.log(
          `[BotAI] ${botId} cannot use Jack action (no valid moves), discarding`
        );
        this.gameClient.dispatch(GameActions.skipJackSwap(botId));
        this.cachedActionDecision = null;
      }
    } else if (targetsSelected === 1) {
      // Step 2: Select second target
      // Use cached decision from step 1
      const decision: BotActionDecision = this.cachedActionDecision
        ? this.cachedActionDecision
        : this.botDecisionService.selectActionTargets(context);

      if (decision.targets.length >= 2) {
        this.gameClient.dispatch(
          GameActions.selectActionTarget(
            botId,
            decision.targets[1].playerId,
            decision.targets[1].position
          )
        );
        console.log(
          `[BotAI] ${botId} selected second Jack target: ${decision.targets[1].playerId} pos ${decision.targets[1].position}`
        );
      }
    } else if (targetsSelected === 2) {
      // Step 3: Decide whether to swap
      // Use cached decision from step 1
      const decision: BotActionDecision = this.cachedActionDecision || {
        targets: [],
        shouldSwap: false,
      };

      // Clear cache after using
      this.cachedActionDecision = null;

      if (decision.shouldSwap) {
        this.gameClient.dispatch(GameActions.executeJackSwap(botId));
        console.log(`[BotAI] ${botId} executed Jack swap`);
      } else {
        this.gameClient.dispatch(GameActions.skipJackSwap(botId));
        console.log(`[BotAI] ${botId} skipped Jack swap`);
      }

      // Jack action transitions to toss-in phase
      // handleTossInPhase will handle turn advancement after toss-in completes
    }
  }

  /**
   * Queen card: Peek at 2 cards, optionally swap them
   */
  private executeQueenAction(botId: string, context: BotDecisionContext): void {
    const pendingAction = this.gameClient.state.pendingAction;
    const targetsSelected = pendingAction?.targets?.length || 0;

    if (targetsSelected === 0) {
      // Step 1: Select first target
      // Use cached action plan if available, otherwise run MCTS fresh
      const decision: BotActionDecision = this.cachedActionDecision
        ? this.cachedActionDecision
        : this.botDecisionService.selectActionTargets(context);

      // Cache the decision for subsequent steps
      this.cachedActionDecision = decision;

      if (decision.targets.length >= 2) {
        this.gameClient.dispatch(
          GameActions.selectActionTarget(
            botId,
            decision.targets[0].playerId,
            decision.targets[0].position
          )
        );
        console.log(
          `[BotAI] ${botId} selected first Queen target: ${decision.targets[0].playerId} pos ${decision.targets[0].position}`
        );
      } else {
        // No valid moves available - discard the card instead
        console.log(
          `[BotAI] ${botId} cannot use Queen action (no valid moves), discarding`
        );
        this.gameClient.dispatch(GameActions.skipQueenSwap(botId));
        this.cachedActionDecision = null;
      }
    } else if (targetsSelected === 1) {
      // Step 2: Select second target
      // Use cached decision from step 1
      const decision: BotActionDecision = this.cachedActionDecision
        ? this.cachedActionDecision
        : this.botDecisionService.selectActionTargets(context);

      if (decision.targets.length >= 2) {
        this.gameClient.dispatch(
          GameActions.selectActionTarget(
            botId,
            decision.targets[1].playerId,
            decision.targets[1].position
          )
        );
        console.log(
          `[BotAI] ${botId} selected second Queen target: ${decision.targets[1].playerId} pos ${decision.targets[1].position}`
        );
      }
    } else if (targetsSelected === 2) {
      // Step 3: Decide whether to swap
      // Use cached decision from step 1
      const decision: BotActionDecision = this.cachedActionDecision || {
        targets: [],
        shouldSwap: false,
      };

      // Clear cache after using
      this.cachedActionDecision = null;

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
   * Standard actions (7, 8, 9, 10, A): Select target and execute
   * These actions only require one target selection
   */
  private executeStandardAction(
    botId: string,
    context: BotDecisionContext
  ): void {
    const pendingAction = this.gameClient.state.pendingAction;
    const targetsSelected = pendingAction?.targets?.length || 0;

    if (targetsSelected === 0) {
      // Step 1: Select target
      // Use cached action plan if available, otherwise run MCTS fresh
      const decision: BotActionDecision = this.cachedActionDecision
        ? this.cachedActionDecision
        : this.botDecisionService.selectActionTargets(context);

      // Cache the decision for step 2
      this.cachedActionDecision = decision;

      if (decision.targets.length > 0) {
        const target = decision.targets[0];

        this.gameClient.dispatch(
          GameActions.selectActionTarget(
            botId,
            target.playerId,
            target.position
          )
        );

        console.log(
          `[BotAI] ${botId} selected target: ${target.playerId} pos ${target.position}`
        );
      } else {
        // No valid moves available - discard the card instead
        console.log(
          `[BotAI] ${botId} cannot use action (no valid moves), discarding`
        );
        this.gameClient.dispatch(GameActions.confirmPeek(botId));
        this.cachedActionDecision = null;
      }
    } else if (targetsSelected === 1) {
      // Step 2: Confirm the peek
      // Clear cache after using
      this.cachedActionDecision = null;

      this.gameClient.dispatch(GameActions.confirmPeek(botId));

      console.log(`[BotAI] ${botId} confirmed peek action`);

      // Card actions (A, 7-10) transition to toss-in phase
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
      // Coalition context for final round
      coalitionLeaderId: state.coalitionLeaderId,
      isCoalitionMember:
        state.phase === 'final' &&
        state.vintoCallerId !== botId &&
        state.vintoCallerId !== null,
    };
  }

  /**
   * Delay helper for bot "thinking" time
   */
  private delay(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  /**
   * Determine who should make the decision for this bot
   *
   * In coalition play:
   * - Coalition leader makes decisions for ALL coalition members
   * - This enables coordinated strategy where leader plans for the whole team
   *
   * Returns: the ID of the bot who should actually make the decision
   */
  private getEffectiveDecisionMaker(botId: string): string {
    const state = this.gameClient.state;

    // Not in final phase? Bot makes its own decisions
    if (state.phase !== 'final') {
      return botId;
    }

    // No coalition leader selected yet? Bot makes its own decisions
    if (!state.coalitionLeaderId) {
      return botId;
    }

    // Is this bot the Vinto caller? They always decide for themselves
    if (botId === state.vintoCallerId) {
      return botId;
    }

    // This bot is a coalition member - the leader decides for them
    return state.coalitionLeaderId;
  }

  /**
   * Check if all non-Vinto players are bots
   */
  private allPlayersAreBots(): boolean {
    const vintoCallerId = this.gameClient.state.vintoCallerId;
    const coalitionPlayers = this.gameClient.state.players.filter(
      (p) => p.id !== vintoCallerId
    );
    return coalitionPlayers.every((p) => p.isBot);
  }

  /**
   * Automatically select coalition leader for bot-only games
   * Simply selects the first coalition bot as leader
   *
   * The leader will make coordinated decisions for ALL coalition members
   * to maximize the chance that at least one member beats the Vinto caller
   */
  private selectCoalitionLeaderForBots(): void {
    console.log('[BotAI] Automatically selecting coalition leader for bots');

    const vintoCallerId = this.gameClient.state.vintoCallerId;
    const coalitionBots = this.gameClient.state.players.filter(
      (p) => p.id !== vintoCallerId && p.isBot
    );

    if (coalitionBots.length === 0) {
      console.warn('[BotAI] No coalition bots found');
      return;
    }

    // Simply select the first bot as coalition leader
    const leader = coalitionBots[0];

    console.log(
      `[BotAI] Selected ${leader.name} as coalition leader (will coordinate all coalition members)`
    );

    // Dispatch action to set coalition leader
    this.gameClient.dispatch(GameActions.setCoalitionLeader(leader.id));
  }
}

/**
 * Factory function to create bot AI adapter
 * Difficulty is read from gameClient.state.difficulty
 */
export function createBotAI(gameClient: GameClient): BotAIAdapter {
  return new BotAIAdapter(gameClient);
}

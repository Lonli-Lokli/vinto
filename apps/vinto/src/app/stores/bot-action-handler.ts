'use client';

import { Card, Rank } from '../shapes';
import { PlayerStore } from './player-store';
import { ActionStore } from './action-store';
import { DeckStore } from './deck-store';
import { GameToastService } from '../lib/toast-service';
import { BotDecisionService, BotDecisionContext } from '../services/bot-decision';

export interface BotActionHandlerDependencies {
  playerStore: PlayerStore;
  actionStore: ActionStore;
  deckStore: DeckStore;
  botDecisionService: BotDecisionService;
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

  constructor(deps: BotActionHandlerDependencies) {
    this.playerStore = deps.playerStore;
    this.actionStore = deps.actionStore;
    this.deckStore = deps.deckStore;
    this.botDecisionService = deps.botDecisionService;
  }

  // Action initiation - async execution (will be replaced with actual AI calculation)
  async handlePeekOwnCard(playerId: string): Promise<boolean> {
    // Mock delay - will be replaced with actual AI calculation
    await this.simulateBotThinking();
    return this.executePeekOwnCard(playerId);
  }

  async handlePeekOpponentCard(playerId: string): Promise<boolean> {
    // Mock delay - will be replaced with actual AI calculation
    await this.simulateBotThinking();
    return this.executePeekOpponentCard(playerId);
  }

  async handleSwapCards(playerId: string): Promise<boolean> {
    // Mock delay - will be replaced with actual AI calculation
    await this.simulateBotThinking();
    return this.executeSwapCards(playerId);
  }

  async handlePeekAndSwap(playerId: string): Promise<boolean> {
    // Mock delay - will be replaced with actual AI calculation
    await this.simulateBotThinking();
    return this.executePeekAndSwap(playerId);
  }

  async handleDeclareAction(playerId: string): Promise<boolean> {
    // Mock delay - will be replaced with actual AI calculation
    await this.simulateBotThinking();
    return this.executeDeclareAction(playerId);
  }

  async handleForceDraw(playerId: string): Promise<boolean> {
    // Mock delay - will be replaced with actual AI calculation
    await this.simulateBotThinking();
    return this.executeForceDraw(playerId);
  }

  /**
   * Simulates bot thinking time.
   * TODO: Replace this with actual AI calculation that returns a promise.
   */
  private async simulateBotThinking(): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, 1500));
  }

  // Execution methods with bot decision logic
  private executePeekOwnCard(playerId: string): boolean {
    const player = this.playerStore.getPlayer(playerId);
    if (!player || player.isHuman) return false;

    const randomPosition = Math.floor(Math.random() * player.cards.length);

    if (randomPosition >= 0 && randomPosition < player.cards.length) {
      // For bots, permanently add to known cards for AI decision-making
      this.playerStore.addKnownCardPosition(playerId, randomPosition);

      const card = player.cards[randomPosition];
      GameToastService.success(
        `${player.name} peeked at position ${randomPosition + 1}: ${card.rank} (value ${card.value})`
      );
      return true;
    }
    return false;
  }

  private executePeekOpponentCard(playerId: string): boolean {
    const opponents = this.playerStore.getOpponents(playerId);
    if (opponents.length === 0) return false;

    const player = this.playerStore.getPlayer(playerId);
    if (!player) return false;

    const randomOpponent =
      opponents[Math.floor(Math.random() * opponents.length)];
    const randomPosition = Math.floor(
      Math.random() * randomOpponent.cards.length
    );

    if (randomPosition >= 0 && randomPosition < randomOpponent.cards.length) {
      const peekedCard = randomOpponent.cards[randomPosition];

      GameToastService.success(
        `${player.name} peeked at ${randomOpponent.name}'s position ${
          randomPosition + 1
        }: ${peekedCard.rank} (value ${peekedCard.value})`
      );
      return true;
    }
    return false;
  }

  private executeSwapCards(playerId: string): boolean {
    const allPlayers = this.playerStore.players;
    const availableTargets: Array<{ playerId: string; position: number }> = [];

    allPlayers.forEach((p) => {
      for (let i = 0; i < p.cards.length; i++) {
        availableTargets.push({ playerId: p.id, position: i });
      }
    });

    if (availableTargets.length >= 2) {
      const shuffled = [...availableTargets].sort(() => Math.random() - 0.5);

      // Select first target
      this.actionStore.addSwapTarget(shuffled[0].playerId, shuffled[0].position);

      // Select second target
      this.actionStore.addSwapTarget(shuffled[1].playerId, shuffled[1].position);

      // Execute the swap
      if (this.actionStore.hasCompleteSwapSelection) {
        const targets = this.actionStore.swapTargets;
        const [target1, target2] = targets;
        const player1 = this.playerStore.getPlayer(target1.playerId);
        const player2 = this.playerStore.getPlayer(target2.playerId);
        const botPlayer = this.playerStore.getPlayer(playerId);

        if (player1 && player2 && botPlayer) {
          const success = this.playerStore.swapCards(
            target1.playerId,
            target1.position,
            target2.playerId,
            target2.position
          );

          if (success) {
            GameToastService.success(
              `${botPlayer.name} swapped ${player1.name}'s card ${
                target1.position + 1
              } with ${player2.name}'s card ${target2.position + 1}`
            );
            return true;
          }
        }
      }
    }
    return false;
  }

  private executePeekAndSwap(playerId: string): boolean {
    const allPlayers = this.playerStore.players;
    const availableTargets: Array<{ playerId: string; position: number }> = [];

    allPlayers.forEach((p) => {
      for (let i = 0; i < p.cards.length; i++) {
        availableTargets.push({ playerId: p.id, position: i });
      }
    });

    if (availableTargets.length >= 2) {
      const shuffled = [...availableTargets].sort(() => Math.random() - 0.5);

      // Peek first card
      const target1 = shuffled[0];
      const player1 = this.playerStore.getPlayer(target1.playerId);
      const card1 = player1?.cards[target1.position];

      if (card1) {
        this.actionStore.addPeekTarget(target1.playerId, target1.position, card1);
      }

      // Peek second card
      const target2 = shuffled[1];
      const player2 = this.playerStore.getPlayer(target2.playerId);
      const card2 = player2?.cards[target2.position];

      if (card2) {
        this.actionStore.addPeekTarget(target2.playerId, target2.position, card2);
      }

      const botPlayer = this.playerStore.getPlayer(playerId);
      if (botPlayer && card1 && card2) {
        GameToastService.info(
          `${botPlayer.name} peeked at two cards: ${card1.rank} and ${card2.rank}`
        );

        // AI decides whether to swap - for now use simple random, can be enhanced
        const shouldSwap = Math.random() < 0.5;

        if (shouldSwap && this.actionStore.hasCompletePeekSelection) {
          const targets = this.actionStore.peekTargets;
          const [peek1, peek2] = targets;

          const success = this.playerStore.swapCards(
            peek1.playerId,
            peek1.position,
            peek2.playerId,
            peek2.position
          );

          if (success) {
            GameToastService.success(
              `${botPlayer.name}: Queen action - Swapped ${peek1.card!.rank} with ${peek2.card!.rank}`
            );
            return true;
          }
        } else {
          GameToastService.info(
            `${botPlayer.name}: Queen action - Chose not to swap the peeked cards`
          );
          return true;
        }
      }
    }
    return false;
  }

  private executeDeclareAction(playerId: string): boolean {
    const ranks: Rank[] = ['7', '8', '9', '10', 'J', 'Q', 'A'];
    const randomRank = ranks[Math.floor(Math.random() * ranks.length)];

    const player = this.playerStore.getPlayer(playerId);
    if (!player) return false;

    this.actionStore.declareKingAction(randomRank);

    const declaredAction =
      this.actionStore.actionContext?.action || 'Unknown action';

    GameToastService.success(
      `${player.name} declared King as ${randomRank} - ${declaredAction}`
    );
    return true;
  }

  private executeForceDraw(playerId: string): boolean {
    const opponents = this.playerStore.getOpponents(playerId);
    if (opponents.length === 0) return false;

    const player = this.playerStore.getPlayer(playerId);
    if (!player) return false;

    const randomOpponent =
      opponents[Math.floor(Math.random() * opponents.length)];

    if (!this.deckStore.hasDrawCards) {
      this.deckStore.ensureDrawCards();
    }

    const drawnCard = this.deckStore.drawCard();
    if (drawnCard) {
      this.playerStore.addCardToPlayer(randomOpponent.id, drawnCard);

      GameToastService.success(
        `${player.name} forced ${randomOpponent.name} to draw a card. ${randomOpponent.name} now has ${randomOpponent.cards.length} cards.`
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
          this.actionStore.addPeekTarget(target.playerId, target.position, targetCard);
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
        this.actionStore.peekTargets.map(t => t.card!),
        context
      );

      const botPlayer = this.playerStore.getPlayer(playerId);

      if (shouldSwap) {
        const targets = this.actionStore.peekTargets;
        const [peek1, peek2] = targets;

        const success = this.playerStore.swapCards(
          peek1.playerId,
          peek1.position,
          peek2.playerId,
          peek2.position
        );

        if (success && botPlayer) {
          GameToastService.success(
            `${botPlayer.name}: Queen action - Swapped ${peek1.card!.rank} with ${peek2.card!.rank}`
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
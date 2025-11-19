/**
 * State Constructor
 * Responsible for converting BotDecisionContext into MCTSGameState
 */

import { getCardValue, getCardShortDescription } from '@vinto/shapes';
import { BotMemory } from './bot-memory';
import { BotDecisionContext } from './shapes';
import { MCTSGameState, MCTSPlayerState } from './mcts-types';

export class StateConstructor {
  /**
   * Construct MCTS game state from bot context
   */
  static constructGameState(
    context: BotDecisionContext,
    botMemory: BotMemory,
    botId: string
  ): MCTSGameState {
    const players: MCTSPlayerState[] = context.allPlayers.map((p) => {
      const playerMemory = botMemory.getPlayerMemory(p.id);
      console.log(
        `[GameState] Player ${p.id} memory: ${playerMemory.size} cards known`
      );
      return {
        id: p.id,
        cardCount: p.cards.length,
        knownCards: playerMemory,
        score: this.estimatePlayerScore(p.id, context, botMemory),
      };
    });

    // Determine if we're in toss-in phase based on game state
    const isTossInPhase =
      context.gameState.subPhase === 'toss_queue_active' &&
      !!context.gameState.activeTossIn;

    // Make discardPileTop aware of active toss-in
    let simulationDiscardTop = context.discardTop || null;

    if (isTossInPhase && context.gameState.activeTossIn) {
      const tossInRank = context.gameState.activeTossIn.ranks[0];
      simulationDiscardTop = {
        id: `tossin-state-${tossInRank}`,
        rank: tossInRank,
        value: getCardValue(tossInRank),
        actionText: getCardShortDescription(tossInRank),
        played: true,
      };
    }

    return {
      players,
      currentPlayerIndex: context.allPlayers.findIndex((p) => p.id === botId),
      botPlayerId: botId,
      discardPileTop: simulationDiscardTop,
      discardPile: context.discardPile,
      deckSize: 54, // Standard deck with 2 Jokers
      botMemory,
      hiddenCards: new Map(),
      pendingCard: context.activeActionCard || context.pendingCard || null,
      isTossInPhase,
      tossInRanks:
        isTossInPhase && context.gameState.activeTossIn
          ? context.gameState.activeTossIn.ranks
          : undefined,
      turnCount: context.gameState.turnNumber,
      finalTurnTriggered: context.gameState.finalTurnTriggered,
      vintoCallerId: context.gameState.vintoCallerId || null,
      coalitionLeaderId: context.coalitionLeaderId || null,
      isTerminal: false,
      winner: null,
    };
  }

  /**
   * Estimate player score from known and unknown cards
   * Uses probabilistic information from botMemory to make more accurate estimates
   */
  private static estimatePlayerScore(
    playerId: string,
    context: BotDecisionContext,
    botMemory: BotMemory
  ): number {
    const player = context.allPlayers.find((p) => p.id === playerId);
    if (!player) return 50;

    const knownCards = botMemory.getPlayerMemory(playerId);
    let knownScore = 0;
    let unknownCount = 0;

    for (let i = 0; i < player.cards.length; i++) {
      const memory = knownCards.get(i);
      if (memory && memory.confidence > 0.5) {
        knownScore += memory.card!.value;
      } else {
        unknownCount++;
      }
    }

    // Calculate expected value of remaining cards based on distribution
    const averageRemainingValue =
      this.calculateAverageRemainingCardValue(botMemory);

    // Estimate unknown cards using the calculated average from remaining cards
    const unknownScore = unknownCount * averageRemainingValue;

    return knownScore + unknownScore;
  }

  /**
   * Calculate the average value of remaining (unseen) cards
   * Uses the card distribution from botMemory to compute expected value
   */
  private static calculateAverageRemainingCardValue(
    botMemory: BotMemory
  ): number {
    const distribution = botMemory.getCardDistribution();

    let totalValue = 0;
    let totalCount = 0;

    for (const [rank, count] of distribution) {
      if (count > 0) {
        const value = getCardValue(rank);
        totalValue += value * count;
        totalCount += count;
      }
    }

    // If no cards remain in distribution, fall back to neutral average (6)
    if (totalCount === 0) {
      return 6;
    }

    return totalValue / totalCount;
  }
}

/**
 * Swap Position Selector
 * Handles logic for selecting best position to swap drawn card into hand
 */

import { Card, PlayerState } from '@vinto/shapes';
import { BotMemory } from './bot-memory';
import { BotDecisionContext, TurnOutcome } from './shapes';
import {
  SWAP_HAND_SIZE_WEIGHT,
  SWAP_KNOWLEDGE_WEIGHT,
  SWAP_SCORE_WEIGHT,
} from './constants';
import type { Rank } from '@vinto/shapes';

export class SwapPositionSelector {
  /**
   * Select best position to swap drawn card into, or null to discard
   */
  static selectBestSwapPosition(
    drawnCard: Card,
    context: BotDecisionContext,
    botId: string,
    difficulty: 'easy' | 'moderate' | 'hard'
  ): number | null {
    // Create temporary memory for accurate simulation
    const tempMemory = new BotMemory(botId, difficulty);

    // Copy current memory state
    context.botPlayer.cards.forEach((card, position) => {
      if (context.botPlayer.knownCardPositions.includes(position)) {
        tempMemory.observeCard(card, botId, position);
      }
    });

    // Add drawn card as temporary knowledge
    tempMemory.observeCard(drawnCard, botId, -1); // -1 indicates pending card

    let bestPosition = 0;
    let bestScore = -Infinity;
    let shouldDiscard = false;

    // First, evaluate the outcome of discarding the drawn card
    const discardOutcome = this.simulateDiscardOutcome(
      drawnCard,
      context,
      tempMemory
    );
    const discardScore = this.calculateOutcomeScore(discardOutcome);

    console.log(
      `[SwapSelector] Discard option: handSize=${discardOutcome.finalHandSize}, ` +
        `known=${discardOutcome.finalKnownCards}/${context.botPlayer.cards.length}, ` +
        `score=${discardOutcome.finalScore.toFixed(
          1
        )}, outcomeScore=${discardScore.toFixed(1)}`
    );

    bestScore = discardScore;
    shouldDiscard = true;

    // Evaluate each possible swap position
    for (
      let position = 0;
      position < context.botPlayer.cards.length;
      position++
    ) {
      const outcome = this.simulateTurnOutcome(
        drawnCard,
        position,
        context,
        tempMemory
      );

      const outcomeScore = this.calculateOutcomeScore(outcome);

      console.log(
        `[SwapSelector] Position ${position}: handSize=${outcome.finalHandSize}, ` +
          `known=${outcome.finalKnownCards}/${context.botPlayer.cards.length}, ` +
          `score=${outcome.finalScore.toFixed(
            1
          )}, outcomeScore=${outcomeScore.toFixed(1)}`
      );

      if (outcomeScore > bestScore) {
        bestScore = outcomeScore;
        bestPosition = position;
        shouldDiscard = false;
      }
    }

    if (shouldDiscard) {
      console.log(
        `[SwapSelector] Selected DISCARD with score ${bestScore.toFixed(1)}`
      );
      return null;
    }

    console.log(
      `[SwapSelector] Selected position ${bestPosition} with score ${bestScore.toFixed(
        1
      )}`
    );

    return bestPosition;
  }

  /**
   * Simulate the outcome of discarding the drawn card without swapping
   */
  private static simulateDiscardOutcome(
    drawnCard: Card,
    context: BotDecisionContext,
    tempMemory: BotMemory
  ): TurnOutcome {
    const botPlayer = context.botPlayer;

    // Discarding doesn't change hand size or score (card never enters hand)
    const currentHandSize = botPlayer.cards.length;
    const currentKnownCards = botPlayer.knownCardPositions.length;
    const currentScore = this.calculateHandScore(botPlayer.cards);

    // Simulate toss-in cascade from the discarded card
    const { handSize: postTossInHandSize, score: postTossInScore } =
      this.simulateTossInCascade(
        drawnCard.rank,
        currentHandSize,
        currentScore,
        context,
        tempMemory
      );

    // No knowledge gain from discarding (card never enters hand)
    return {
      finalHandSize: postTossInHandSize,
      finalKnownCards: currentKnownCards,
      finalScore: postTossInScore,
    };
  }

  /**
   * Simulate the full consequence of swapping the drawn card with a specific position
   */
  private static simulateTurnOutcome(
    drawnCard: Card,
    swapPosition: number,
    context: BotDecisionContext,
    tempMemory: BotMemory
  ): TurnOutcome {
    const botPlayer = context.botPlayer;

    // Stage 1: Swap Simulation
    const discardedCard = botPlayer.cards[swapPosition];

    const currentHandSize = botPlayer.cards.length;
    let currentKnownCards = botPlayer.knownCardPositions.length;
    let currentScore = this.calculateHandScore(botPlayer.cards);

    // After swap: we know the drawn card is now in our hand
    const isDiscardedPositionKnown =
      botPlayer.knownCardPositions.includes(swapPosition);

    if (!isDiscardedPositionKnown) {
      // We're replacing an unknown card with a known card
      currentKnownCards += 1;
    }

    // Update score: remove discarded card value, add drawn card value
    currentScore = currentScore - discardedCard.value + drawnCard.value;

    // Stage 2: Toss-In Cascade Simulation
    const { handSize: postTossInHandSize, score: postTossInScore } =
      this.simulateTossInCascade(
        discardedCard.rank,
        currentHandSize,
        currentScore,
        context,
        tempMemory
      );

    // Stage 3: Action & Knowledge Gain Simulation
    const knowledgeGain = this.simulateActionKnowledgeGain(
      discardedCard,
      context,
      tempMemory
    );

    return {
      finalHandSize: postTossInHandSize,
      finalKnownCards: currentKnownCards + knowledgeGain,
      finalScore: postTossInScore,
    };
  }

  /**
   * Stage 2: Simulate the toss-in cascade effect
   */
  private static simulateTossInCascade(
    discardedRank: Rank,
    currentHandSize: number,
    currentScore: number,
    context: BotDecisionContext,
    _tempMemory: BotMemory
  ): { handSize: number; score: number } {
    let tossInCount = 0;
    let scoreReduction = 0;

    // Check each known card in bot's hand for matching rank
    for (let pos = 0; pos < context.botPlayer.cards.length; pos++) {
      if (context.botPlayer.knownCardPositions.includes(pos)) {
        const card = context.botPlayer.cards[pos];

        if (card.rank === discardedRank) {
          tossInCount++;
          scoreReduction += card.value;
        }
      }
    }

    // Apply toss-in effect
    return {
      handSize: currentHandSize - tossInCount,
      score: currentScore - scoreReduction,
    };
  }

  /**
   * Stage 3: Simulate knowledge gain from card actions
   */
  private static simulateActionKnowledgeGain(
    discardedCard: Card,
    context: BotDecisionContext,
    _tempMemory: BotMemory
  ): number {
    const action = discardedCard.actionText;

    if (!action) {
      return 0; // No action = no knowledge gain
    }

    const rank = discardedCard.rank;
    const unknownCount = this.countUnknownCards(context.botPlayer);

    // Peek Actions: Reliable knowledge gain
    if (rank === '7' || rank === '8') {
      return unknownCount > 0 ? 3 : 0;
    }

    if (rank === 'Q') {
      const baseKnowledge = Math.min(2, unknownCount);
      return baseKnowledge > 0 ? baseKnowledge + 2 : 0;
    }

    // Swap Actions: Knowledge-gaining swap heuristic
    if (rank === 'J' || rank === '9' || rank === '10') {
      return this.simulateKnowledgeGainingSwap(context, _tempMemory);
    }

    // King: EXTREMELY valuable
    if (rank === 'K') {
      let kingValue = 4;

      // Bonus if bot has OTHER known Kings
      let otherKings = 0;
      for (let pos = 0; pos < context.botPlayer.cards.length; pos++) {
        if (!context.botPlayer.knownCardPositions.includes(pos)) continue;
        const card = context.botPlayer.cards[pos];
        if (card.rank === 'K') {
          otherKings++;
        }
      }
      kingValue += otherKings * 3;

      // Bonus if bot has known action cards to declare
      let knownActionCards = 0;
      for (let pos = 0; pos < context.botPlayer.cards.length; pos++) {
        if (!context.botPlayer.knownCardPositions.includes(pos)) continue;
        const card = context.botPlayer.cards[pos];
        if (card.actionText && card.rank !== 'K') {
          knownActionCards++;
        }
      }
      kingValue += knownActionCards * 2;

      return kingValue;
    }

    // Ace: Low value card, flexible
    if (rank === 'A') {
      return 1;
    }

    return 0;
  }

  /**
   * Heuristic: Check if a knowledge-gaining swap is possible
   */
  private static simulateKnowledgeGainingSwap(
    context: BotDecisionContext,
    _tempMemory: BotMemory
  ): number {
    const hasUnknownCards = this.countUnknownCards(context.botPlayer) > 0;

    if (!hasUnknownCards) {
      return 0;
    }

    // Check if we know any opponent cards
    for (const [_opponentId, knownCards] of context.opponentKnowledge) {
      if (knownCards.size > 0) {
        return 1;
      }
    }

    return 0;
  }

  /**
   * Count unknown cards
   */
  private static countUnknownCards(botPlayer: PlayerState): number {
    return botPlayer.cards.length - botPlayer.knownCardPositions.length;
  }

  /**
   * Calculate hand score
   */
  private static calculateHandScore(cards: Card[]): number {
    return cards.reduce((sum, card) => sum + card.value, 0);
  }

  /**
   * Calculate outcome score based on strategic priorities
   */
  private static calculateOutcomeScore(outcome: TurnOutcome): number {
    const knowledgeScore = outcome.finalKnownCards * SWAP_KNOWLEDGE_WEIGHT;
    const handSizeScore = -outcome.finalHandSize * SWAP_HAND_SIZE_WEIGHT;
    const scoreComponent = -outcome.finalScore * SWAP_SCORE_WEIGHT;

    return knowledgeScore + handSizeScore + scoreComponent;
  }
}

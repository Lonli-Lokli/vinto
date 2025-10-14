// services/vinto-round-solver.ts

import { Card, CardAction, getCardAction, getCardValue, Pile } from '@/shared';
import { BotMemory } from './bot-memory';

/**
 * Result of Vinto validation analysis
 */
export interface VintoValidationResult {
  shouldCallVinto: boolean;
  callerScore: number;
  worstCaseOpponentScore: number;
  confidence: number; // 0-1, how confident we are in the analysis
  reason: string;
}

/**
 * Represents a player's state for Vinto analysis
 */
interface PlayerAnalysisState {
  id: string;
  knownCards: Card[];
  unknownCardCount: number;
  hasActionCards: boolean;
  actionCardTypes: CardAction[];
}

/**
 * VintoRoundSolver - Performs worst-case analysis for Vinto calls
 *
 * This solver checks if calling Vinto is safe by simulating the best possible
 * moves opponents can make with their known action cards and hidden cards.
 *
 * The goal: Ensure that even if opponents play optimally to minimize their scores,
 * the bot's score is still lower than the best opponent's score.
 */
export class VintoRoundSolver {
  constructor(_botId: string, private botMemory: BotMemory) {}

  /**
   * Validate if calling Vinto is safe
   * Returns detailed analysis of whether the bot should call Vinto
   */
  validateVintoCall(
    botCards: Card[],
    opponents: Array<{
      id: string;
      cardCount: number;
    }>,
    discardPile: Pile
  ): VintoValidationResult {
    // Step 1: Calculate bot's actual score
    const callerScore = this.calculateScore(botCards);

    // Step 2: Build analysis state for each opponent
    const opponentStates = opponents.map((opp) =>
      this.buildOpponentState(opp.id, opp.cardCount)
    );

    // Step 3: Calculate worst-case scenario for each opponent
    // (best score they can achieve through optimal play)
    const opponentWorstCases = opponentStates.map((oppState) =>
      this.calculateWorstCaseScore(oppState, discardPile)
    );

    // Step 4: Find the best opponent (lowest possible score)
    const worstCaseOpponentScore = Math.min(...opponentWorstCases);

    // Step 5: Determine if Vinto call is safe
    // Bot wins if: botScore < worstCaseOpponentScore
    const shouldCallVinto = callerScore < worstCaseOpponentScore;

    // Step 6: Calculate confidence based on information quality
    const confidence = this.calculateConfidence(opponentStates);

    // Step 7: Generate reason for decision
    const reason = this.generateReason(
      shouldCallVinto,
      callerScore,
      worstCaseOpponentScore,
      opponentStates,
      confidence
    );

    return {
      shouldCallVinto,
      callerScore,
      worstCaseOpponentScore,
      confidence,
      reason,
    };
  }

  /**
   * Build analysis state for an opponent
   */
  private buildOpponentState(
    opponentId: string,
    cardCount: number
  ): PlayerAnalysisState {
    const knownCards: Card[] = [];
    const actionCardTypes: CardAction[] = [];
    let unknownCardCount = 0;

    const playerMemory = this.botMemory.getPlayerMemory(opponentId);

    for (let pos = 0; pos < cardCount; pos++) {
      const memory = playerMemory.get(pos);
      if (memory && memory.confidence > 0.5 && memory.card) {
        knownCards.push(memory.card);

        // Track action cards - get action from card rank config
        const action = getCardAction(memory.card.rank);
        if (action) {
          actionCardTypes.push(action);
        }
      } else {
        unknownCardCount++;
      }
    }

    return {
      id: opponentId,
      knownCards,
      unknownCardCount,
      hasActionCards: actionCardTypes.length > 0,
      actionCardTypes,
    };
  }

  /**
   * Calculate worst-case (best for opponent) score they can achieve
   *
   * This assumes opponents play optimally:
   * 1. Use action cards to peek/swap to minimize their score
   * 2. Unknown cards are assumed to be low-value cards (pessimistic for bot)
   * 3. Opponents will use swaps to exchange high cards with low cards
   */
  private calculateWorstCaseScore(
    opponent: PlayerAnalysisState,
    discardPile: Pile
  ): number {
    // Start with known cards score
    const knownScore = opponent.knownCards.reduce(
      (sum, card) => sum + card.value,
      0
    );

    // Calculate best possible value for unknown cards
    const bestPossibleUnknownValue =
      this.calculateBestPossibleUnknownValue(discardPile);

    // Assume unknown cards are the best possible (worst case for bot)
    const unknownScore = opponent.unknownCardCount * bestPossibleUnknownValue;

    // Apply action card benefits (opponent plays optimally)
    let actionBenefit = 0;

    // Peek actions: Allow opponent to gain perfect information
    // (doesn't directly reduce score, but enables better swaps)

    // Swap actions: Each swap can replace a high card with a low card
    const swapActions = opponent.actionCardTypes.filter(
      (action) => action === 'swap-cards' || action === 'peek-and-swap'
    ).length;

    // Conservative estimate: each swap action can reduce score by ~5 points
    // (swapping a 10 for a 2, or a 9 for a Joker, etc.)
    const swapBenefit = swapActions * 5;
    actionBenefit += swapBenefit;

    // King (declare-action): Can be used as any action card
    const kingActions = opponent.actionCardTypes.filter(
      (action) => action === 'declare-action'
    ).length;
    if (kingActions > 0) {
      // Assume King is used as a swap action for maximum benefit
      actionBenefit += kingActions * 5;
    }

    // Force-draw: Doesn't help opponent's score, skip

    // Calculate final worst-case score
    const worstCaseScore = Math.max(
      0,
      knownScore + unknownScore - actionBenefit
    );

    return worstCaseScore;
  }

  /**
   * Calculate the best possible value for unknown cards
   * (lowest possible, which is worst for bot calling Vinto)
   */
  private calculateBestPossibleUnknownValue(_discardPile: Pile): number {
    const distribution = this.botMemory.getCardDistribution();

    // Build list of remaining card values, weighted by count
    const remainingValues: number[] = [];
    for (const [rank, count] of distribution) {
      if (count > 0) {
        const value = getCardValue(rank);
        for (let i = 0; i < count; i++) {
          remainingValues.push(value);
        }
      }
    }

    if (remainingValues.length === 0) {
      // No information, assume neutral value
      return 6;
    }

    // Sort values (ascending) to find best possible cards
    remainingValues.sort((a, b) => a - b);

    // Calculate average of bottom 30% (pessimistic assumption)
    // Opponent likely has low-value cards in unknown positions
    const bottomPercentile = Math.ceil(remainingValues.length * 0.3);
    const bestValues = remainingValues.slice(0, Math.max(1, bottomPercentile));

    const avgBestValue =
      bestValues.reduce((sum, v) => sum + v, 0) / bestValues.length;

    return avgBestValue;
  }

  /**
   * Calculate confidence in the analysis
   * Based on how much information we have about opponents
   */
  private calculateConfidence(opponents: PlayerAnalysisState[]): number {
    let totalCards = 0;
    let knownCards = 0;

    for (const opp of opponents) {
      totalCards += opp.knownCards.length + opp.unknownCardCount;
      knownCards += opp.knownCards.length;
    }

    if (totalCards === 0) {
      return 0.3; // Low confidence with no information
    }

    // Confidence = percentage of opponent cards known
    const knowledgeRatio = knownCards / totalCards;

    // Scale: 30% base confidence, up to 95% with perfect knowledge
    return 0.3 + knowledgeRatio * 0.65;
  }

  /**
   * Calculate total score from cards
   */
  private calculateScore(cards: Card[]): number {
    return cards.reduce((sum, card) => sum + card.value, 0);
  }

  /**
   * Generate human-readable reason for the decision
   */
  private generateReason(
    shouldCall: boolean,
    callerScore: number,
    worstCaseScore: number,
    opponents: PlayerAnalysisState[],
    confidence: number
  ): string {
    const margin = Math.abs(callerScore - worstCaseScore);
    const knownOpponentCards = opponents.reduce(
      (sum, opp) => sum + opp.knownCards.length,
      0
    );
    const totalOpponentCards = opponents.reduce(
      (sum, opp) => sum + opp.knownCards.length + opp.unknownCardCount,
      0
    );

    if (shouldCall) {
      return `Safe to call Vinto. Score ${callerScore} < ${worstCaseScore} (margin: ${margin}). Confidence: ${(
        confidence * 100
      ).toFixed(
        0
      )}% (${knownOpponentCards}/${totalOpponentCards} opponent cards known).`;
    } else {
      const actionCardsCount = opponents.reduce(
        (sum, opp) => sum + opp.actionCardTypes.length,
        0
      );
      return `Risky to call Vinto. Score ${callerScore} >= ${worstCaseScore} (deficit: ${margin}). Opponents have ${actionCardsCount} known action cards. Confidence: ${(
        confidence * 100
      ).toFixed(0)}%.`;
    }
  }
}

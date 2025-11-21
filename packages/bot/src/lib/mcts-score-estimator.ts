// Pure functions for estimating player scores

import { PlayerState, getCardValue } from '@vinto/shapes';
import { BotMemory } from './bot-memory';

/**
 * Estimate player score from known and unknown cards
 */
export function estimatePlayerScore(
  player: PlayerState,
  botMemory: BotMemory,
  playerId: string
): number {
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

  // Calculate expected value of remaining cards
  const averageRemainingValue = calculateAverageRemainingCardValue(botMemory);

  // Estimate unknown cards using the calculated average
  const unknownScore = unknownCount * averageRemainingValue;

  return knownScore + unknownScore;
}

/**
 * Calculate the average value of remaining (unseen) cards
 */
export function calculateAverageRemainingCardValue(
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

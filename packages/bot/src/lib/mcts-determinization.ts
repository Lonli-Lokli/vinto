// Pure functions for MCTS determinization (sampling hidden information)

import copy from 'fast-copy';
import {
  Card,
  Rank,
  getCardValue,
  getCardShortDescription,
} from '@vinto/shapes';
import { MCTSGameState } from './mcts-types';

/**
 * Standard deck composition (52 cards + 2 Jokers)
 */
export const STANDARD_DECK_RANKS: Rank[] = [
  'A',
  'A',
  'A',
  'A',
  '2',
  '2',
  '2',
  '2',
  '3',
  '3',
  '3',
  '3',
  '4',
  '4',
  '4',
  '4',
  '5',
  '5',
  '5',
  '5',
  '6',
  '6',
  '6',
  '6',
  '7',
  '7',
  '7',
  '7',
  '8',
  '8',
  '8',
  '8',
  '9',
  '9',
  '9',
  '9',
  '10',
  '10',
  '10',
  '10',
  'J',
  'J',
  'J',
  'J',
  'Q',
  'Q',
  'Q',
  'Q',
  'K',
  'K',
  'K',
  'K',
  'Joker',
  'Joker',
];

/**
 * Build available ranks pool by removing known/discarded cards
 */
export function buildAvailableRanksPool(state: MCTSGameState): Rank[] {
  const availableRanks: Rank[] = [...STANDARD_DECK_RANKS];

  // Remove discarded cards from available pool
  for (const discardedCard of state.discardPile) {
    const idx = availableRanks.indexOf(discardedCard.rank);
    if (idx >= 0) {
      availableRanks.splice(idx, 1);
    }
  }

  // Remove cards we know about from available pool
  for (const player of state.players) {
    for (let pos = 0; pos < player.cardCount; pos++) {
      const memory = player.knownCards.get(pos);
      if (memory && memory.confidence > 0.5) {
        const knownRank = memory.card!.rank;
        const idx = availableRanks.indexOf(knownRank);
        if (idx >= 0) {
          availableRanks.splice(idx, 1);
        }
      }
    }
  }

  // Remove pending card if it exists (card currently being evaluated)
  if (state.pendingCard) {
    const idx = availableRanks.indexOf(state.pendingCard.rank);
    if (idx >= 0) {
      availableRanks.splice(idx, 1);
    }
  }

  return availableRanks;
}

/**
 * Get strategic probability weight for a card rank
 * Based on strategic card ranking: Joker > Q > J > K > 7 > 8 > A > 9 > 10 > 6 > 5 > 4 > 3 > 2
 * Action cards are more likely to be in opponent hands (they keep them)
 * Low value cards (2-6) are less likely (opponents swap them out)
 */
export function getStrategicProbabilityWeight(rank: Rank): number {
  switch (rank) {
    case 'Joker':
      return 2.0; // Best card: -1 point + never discarded
    case 'Q':
      return 1.8; // Peek 2 + optional swap
    case 'J':
      return 1.7; // Swap any 2 cards
    case 'K':
      return 1.6; // 0 points + declare any action
    case '7':
    case '8':
      return 1.4; // Peek own cards
    case 'A':
      return 1.3; // 1 point + force draw action
    case '9':
    case '10':
      return 1.1; // Peek opponent (but 9-10 points)
    case '6':
      return 0.7; // No action, 6 points
    case '5':
      return 0.6; // No action, 5 points
    case '2':
    case '3':
    case '4':
      return 0.5; // No action, low utility
    default:
      return 1.0; // Fallback
  }
}

/**
 * Build cumulative probabilities for weighted sampling
 */
function buildCumulativeProbabilities(availableRanks: Rank[]): number[] {
  const weights = availableRanks.map(getStrategicProbabilityWeight);
  const totalWeight = weights.reduce((sum, w) => sum + w, 0);

  const cumulative: number[] = [];
  let sum = 0;
  for (const weight of weights) {
    sum += weight / totalWeight;
    cumulative.push(sum);
  }

  return cumulative;
}

/**
 * Sample a card from available ranks pool using weighted probability
 * Optionally constrained by beliefs about the card
 */
export function sampleCardFromPool(
  availableRanks: Rank[],
  playerId: string,
  position: number,
  minValue?: number,
  maxValue?: number
): Card {
  if (availableRanks.length === 0) {
    throw new Error('Cannot sample from empty card pool');
  }

  // Apply belief constraints if provided
  let constrainedRanks = availableRanks;
  if (minValue !== undefined || maxValue !== undefined) {
    constrainedRanks = availableRanks.filter((rank) => {
      const value = getCardValue(rank);
      if (minValue !== undefined && value < minValue) return false;
      if (maxValue !== undefined && value > maxValue) return false;
      return true;
    });

    // Fallback to unconstrained if no cards match
    if (constrainedRanks.length === 0) {
      constrainedRanks = availableRanks;
    }
  }

  // Build cumulative probability distribution
  const cumulative = buildCumulativeProbabilities(constrainedRanks);

  // Sample using weighted probabilities
  const random = Math.random();
  let idx = 0;
  for (let i = 0; i < cumulative.length; i++) {
    if (random <= cumulative[i]) {
      idx = i;
      break;
    }
  }

  const sampledRank = constrainedRanks[idx];

  // Remove from original pool
  const originalIdx = availableRanks.indexOf(sampledRank);
  if (originalIdx >= 0) {
    availableRanks.splice(originalIdx, 1);
  }

  return {
    id: `${playerId}-${position}-sampled`,
    rank: sampledRank,
    value: getCardValue(sampledRank),
    actionText: getCardShortDescription(sampledRank),
    played: false,
  };
}

/**
 * Determinize hidden information by sampling
 * Creates a consistent possible world for simulation
 *
 * ENHANCED: Now uses OpponentModeler beliefs to intelligently constrain sampling
 */
export function determinize(state: MCTSGameState): MCTSGameState {
  // Use fast-copy for proper deep cloning
  const newState = copy(state);
  newState.hiddenCards = new Map();

  // Build available ranks pool
  const availableRanks = buildAvailableRanksPool(state);

  // Sample cards for each player position
  for (const player of newState.players) {
    for (let pos = 0; pos < player.cardCount; pos++) {
      const memory = player.knownCards.get(pos);

      if (!memory || memory.confidence < 0.5) {
        // Unknown card - sample from pool with belief constraints
        let sampledCard: Card;

        if (availableRanks.length > 0) {
          // Check if we have beliefs about this card
          const belief = state.opponentModeler?.getBelief(player.id, pos);

          if (belief) {
            // Use belief constraints for intelligent sampling
            sampledCard = sampleCardFromPool(
              availableRanks,
              player.id,
              pos,
              belief.minValue,
              belief.maxValue
            );
          } else {
            // No beliefs - use standard weighted sampling
            sampledCard = sampleCardFromPool(availableRanks, player.id, pos);
          }
        } else {
          // Fallback: use bot memory distribution
          const sampledRank =
            state.botMemory.sampleCardFromDistribution() || '6';
          sampledCard = {
            id: `${player.id}-${pos}-sampled`,
            rank: sampledRank,
            value: getCardValue(sampledRank),
            actionText: getCardShortDescription(sampledRank),
            played: false,
          };
        }

        newState.hiddenCards.set(`${player.id}-${pos}`, sampledCard);
      } else {
        // Known card - use from memory
        newState.hiddenCards.set(`${player.id}-${pos}`, memory.card!);
      }
    }
  }

  return newState;
}

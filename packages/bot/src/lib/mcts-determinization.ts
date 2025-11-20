// Pure functions for MCTS determinization (sampling hidden information)

import copy from 'fast-copy';
import { Card, Rank, getCardValue, getCardShortDescription } from '@vinto/shapes';
import { MCTSGameState } from './mcts-types';

/**
 * Standard deck composition (52 cards + 2 Jokers)
 */
export const STANDARD_DECK_RANKS: Rank[] = [
  'A', 'A', 'A', 'A',
  '2', '2', '2', '2',
  '3', '3', '3', '3',
  '4', '4', '4', '4',
  '5', '5', '5', '5',
  '6', '6', '6', '6',
  '7', '7', '7', '7',
  '8', '8', '8', '8',
  '9', '9', '9', '9',
  '10', '10', '10', '10',
  'J', 'J', 'J', 'J',
  'Q', 'Q', 'Q', 'Q',
  'K', 'K', 'K', 'K',
  'Joker', 'Joker',
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

  return availableRanks;
}

/**
 * Sample a card from available ranks pool
 */
export function sampleCardFromPool(
  availableRanks: Rank[],
  playerId: string,
  position: number
): Card {
  // Sample from available ranks and remove to prevent duplicates
  const idx = Math.floor(Math.random() * availableRanks.length);
  const sampledRank = availableRanks[idx];
  availableRanks.splice(idx, 1);

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
        // Unknown card - sample from pool
        let sampledCard: Card;

        if (availableRanks.length > 0) {
          sampledCard = sampleCardFromPool(availableRanks, player.id, pos);
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

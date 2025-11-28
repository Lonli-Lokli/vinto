// Pure functions for simulating turn outcomes

import { Card, PlayerState, Rank } from '@vinto/shapes';
import { BotMemory } from './bot-memory';
import { TurnOutcome, BotDecisionContext } from './shapes';
import { calculateHandScore, countUnknownCards } from './mcts-bot-heuristics';
import {
  SWAP_HAND_SIZE_WEIGHT,
  SWAP_KNOWLEDGE_WEIGHT,
  SWAP_SCORE_WEIGHT,
  JOKER_PROTECTION_MULTIPLIER,
  KING_PROTECTION_MULTIPLIER,
  JOKER_PENALTY_AMPLIFIER,
  KING_PENALTY_AMPLIFIER,
  GENERAL_SWAP_PENALTY_MULTIPLIER,
} from './constants';

/**
 * Simulate the outcome of discarding the drawn card without swapping
 */
export function simulateDiscardOutcome(
  drawnCard: Card,
  botPlayer: PlayerState,
  context: BotDecisionContext,
  tempMemory: BotMemory
): TurnOutcome {
  // Discarding doesn't change hand size or score (card never enters hand)
  const currentHandSize = botPlayer.cards.length;
  const currentKnownCards = botPlayer.knownCardPositions.length;
  const currentScore = calculateHandScore(botPlayer.cards);

  // Simulate toss-in cascade from the discarded card
  const { handSize: postTossInHandSize, score: postTossInScore } =
    simulateTossInCascade(
      drawnCard.rank,
      currentHandSize,
      currentScore,
      botPlayer,
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
export function simulateTurnOutcome(
  drawnCard: Card,
  swapPosition: number,
  botPlayer: PlayerState,
  context: BotDecisionContext,
  tempMemory: BotMemory
): TurnOutcome {
  // Stage 1: Swap Simulation
  const discardedCard = botPlayer.cards[swapPosition];

  // Calculate initial state after swap
  const currentHandSize = botPlayer.cards.length;
  let currentKnownCards = botPlayer.knownCardPositions.length;
  let currentScore = calculateHandScore(botPlayer.cards);

  // After swap: knowledge tracking
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
    simulateTossInCascade(
      discardedCard.rank,
      currentHandSize,
      currentScore,
      botPlayer,
      tempMemory
    );

  // Stage 3: Action & Knowledge Gain Simulation
  const knowledgeGain = simulateActionKnowledgeGain(
    discardedCard,
    botPlayer,
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
 * Simulate the toss-in cascade effect
 */
export function simulateTossInCascade(
  discardedRank: Rank,
  currentHandSize: number,
  currentScore: number,
  botPlayer: PlayerState,
  _tempMemory: BotMemory
): { handSize: number; score: number } {
  let tossInCount = 0;
  let scoreReduction = 0;

  // Check each known card in bot's hand for matching rank
  for (let pos = 0; pos < botPlayer.cards.length; pos++) {
    if (botPlayer.knownCardPositions.includes(pos)) {
      const card = botPlayer.cards[pos];

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
 * Simulate knowledge gain from card actions
 */
export function simulateActionKnowledgeGain(
  discardedCard: Card,
  botPlayer: PlayerState,
  context: BotDecisionContext,
  _tempMemory: BotMemory
): number {
  const action = discardedCard.actionText;

  if (!action) {
    return 0; // No action = no knowledge gain
  }

  const rank = discardedCard.rank;

  // Peek Actions: 7 and 8
  if (rank === '7' || rank === '8') {
    const unknownCount = countUnknownCards(botPlayer);
    // Return 3 instead of 1 to account for strategic advantages
    return unknownCount > 0 ? 3 : 0;
  }

  // Queen: Peek at 2 cards
  if (rank === 'Q') {
    const unknownCount = countUnknownCards(botPlayer);
    const baseKnowledge = Math.min(2, unknownCount);
    return baseKnowledge > 0 ? baseKnowledge + 2 : 0;
  }

  // Swap Actions: J, 9, 10
  if (rank === 'J' || rank === '9' || rank === '10') {
    return simulateKnowledgeGainingSwap(botPlayer, context);
  }

  // King: Extremely valuable
  if (rank === 'K') {
    return calculateKingActionValue(botPlayer);
  }

  // Ace: Low value card with situational action
  if (rank === 'A') {
    return 1;
  }

  return 0;
}

/**
 * Calculate King action value
 */
function calculateKingActionValue(botPlayer: PlayerState): number {
  let kingValue = 4;

  // Bonus if bot has OTHER known Kings
  let otherKings = 0;
  for (let pos = 0; pos < botPlayer.cards.length; pos++) {
    if (!botPlayer.knownCardPositions.includes(pos)) continue;
    const card = botPlayer.cards[pos];
    if (card.rank === 'K') {
      otherKings++;
    }
  }
  kingValue += otherKings * 3;

  // Bonus if bot has known action cards to declare
  let knownActionCards = 0;
  for (let pos = 0; pos < botPlayer.cards.length; pos++) {
    if (!botPlayer.knownCardPositions.includes(pos)) continue;
    const card = botPlayer.cards[pos];
    if (card.actionText && card.rank !== 'K') {
      knownActionCards++;
    }
  }
  kingValue += knownActionCards * 2;

  return kingValue;
}

/**
 * Check if a knowledge-gaining swap is possible
 */
function simulateKnowledgeGainingSwap(
  botPlayer: PlayerState,
  context: BotDecisionContext
): number {
  // Check if we have unknown cards in our hand
  const hasUnknownCards = countUnknownCards(botPlayer) > 0;

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
 * Calculate strategic outcome score with special penalties for bad swaps
 */
export function calculateStrategicOutcomeScore(
  outcome: TurnOutcome,
  drawnCard: Card,
  swappedOutCard: Card | null
): number {
  // Base outcome score
  const baseScore = calculateOutcomeScore(outcome);

  // Strategic penalties for bad swaps
  let strategicPenalty = 0;

  if (swappedOutCard) {
    // Calculate score delta: positive means swapping for worse card (bad), negative means swapping for better card (good)
    const scoreDelta = drawnCard.value - swappedOutCard.value;

    if (swappedOutCard.rank === 'Joker') {
      // MASSIVE PENALTY: Swapping out Joker (best card: -1 point)
      // Example: Swapping Joker (-1) for 6 → scoreDelta = 7, penalty = 7 × 15 × 3.0 × 100 = 31,500
      strategicPenalty +=
        scoreDelta *
        SWAP_SCORE_WEIGHT *
        JOKER_PROTECTION_MULTIPLIER *
        JOKER_PENALTY_AMPLIFIER;
    } else if (swappedOutCard.rank === 'K') {
      // LARGE PENALTY: Swapping out King (0 points + powerful action)
      // Example: Swapping King (0) for 6 → scoreDelta = 6, penalty = 6 × 15 × 2.5 × 100 = 22,500
      strategicPenalty +=
        scoreDelta *
        SWAP_SCORE_WEIGHT *
        KING_PROTECTION_MULTIPLIER *
        KING_PENALTY_AMPLIFIER;
    } else if (scoreDelta > 0) {
      // General strategic penalty: only apply when swapping for WORSE card (positive scoreDelta)
      // Example: Swapping 2 for 6 → scoreDelta = 4, penalty = 4 × 15 × 10 = 600
      // Example: Swapping 6 for 2 → scoreDelta = -4, NO penalty (this is a good swap!)
      strategicPenalty +=
        scoreDelta * SWAP_SCORE_WEIGHT * GENERAL_SWAP_PENALTY_MULTIPLIER;
    }
  }

  return baseScore - strategicPenalty;
}

/**
 * Calculate a comparable score for a turn outcome
 */
export function calculateOutcomeScore(outcome: TurnOutcome): number {
  // Knowledge: more is better
  const knowledgeScore = outcome.finalKnownCards * SWAP_KNOWLEDGE_WEIGHT;

  // Hand size: fewer is better
  const handSizeScore = -outcome.finalHandSize * SWAP_HAND_SIZE_WEIGHT;

  // Score: lower is better
  const scoreComponent = -outcome.finalScore * SWAP_SCORE_WEIGHT;

  return knowledgeScore + handSizeScore + scoreComponent;
}

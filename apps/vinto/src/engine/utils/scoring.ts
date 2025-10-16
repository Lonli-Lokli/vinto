// engine/utils/scoring.ts
// Pure functions for calculating final scores

import { Card, PlayerState } from '@/shared';

/**
 * Calculate final scores for all players
 * Returns a map of playerId -> score for comparison
 *
 * In coalition mode:
 * - Vinto caller gets their individual score
 * - Coalition members all get the BEST (lowest) score among coalition members
 *
 * In normal mode:
 * - Each player gets their individual score
 */
export function calculateFinalScores(
  players: PlayerState[],
  vintoCallerId: string | null
): Record<string, number> {
  const scores: Record<string, number> = {};

  if (!vintoCallerId) {
    // Normal scoring: each player gets their individual score
    players.forEach((player) => {
      scores[player.id] = calculateCardTotal(player.cards);
    });
    return scores;
  }

  // Coalition scoring
  const vintoCaller = players.find((p) => p.id === vintoCallerId);
  if (!vintoCaller) {
    // Fallback to normal scoring if caller not found
    players.forEach((player) => {
      scores[player.id] = calculateCardTotal(player.cards);
    });
    return scores;
  }

  // Calculate Vinto caller's score
  scores[vintoCaller.id] = calculateCardTotal(vintoCaller.cards);

  // Find coalition members (players who are in coalition with the caller)
  const coalitionMembers = players.filter(
    (p) => p.id !== vintoCallerId && p.coalitionWith.includes(vintoCallerId)
  );

  // Calculate best (lowest) coalition score
  let bestCoalitionScore = Infinity;
  coalitionMembers.forEach((member) => {
    const memberScore = calculateCardTotal(member.cards);
    bestCoalitionScore = Math.min(bestCoalitionScore, memberScore);
  });

  // Assign best coalition score to all coalition members
  coalitionMembers.forEach((member) => {
    scores[member.id] = bestCoalitionScore;
  });

  // For players not in the coalition (didn't join), use their individual scores
  players.forEach((player) => {
    if (
      player.id !== vintoCallerId &&
      !coalitionMembers.find((m) => m.id === player.id)
    ) {
      scores[player.id] = calculateCardTotal(player.cards);
    }
  });

  return scores;
}

/**
 * Calculate the total value of a hand of cards
 */
function calculateCardTotal(cards: Card[]): number {
  return cards.reduce((sum, card) => sum + card.value, 0);
}

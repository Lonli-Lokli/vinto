// Pure functions for coalition evaluation

import { MCTSGameState, MCTSPlayerState } from './mcts-types';
import {
  evaluateTossInPotential,
  evaluateActionCardValue,
} from './evaluation-helpers';

/**
 * Evaluate state from coalition's perspective
 * Goal: Ensure at least ONE coalition member beats Vinto caller
 */
export function evaluateCoalitionState(
  state: MCTSGameState,
  botPlayerId: string
): number {
  const vintoCallerId = state.vintoCallerId;
  if (!vintoCallerId) return 0;

  const vintoPlayer = state.players.find((p) => p.id === vintoCallerId);
  if (!vintoPlayer) return 0;

  // Find coalition champion (lowest score member)
  const champion = findCoalitionChampion(state, vintoCallerId);
  if (!champion) return 0;

  console.log(
    `[Coalition Eval] Champion: ${champion.id} (score: ${champion.score}), ` +
      `Vinto: ${vintoCallerId} (score: ${vintoPlayer.score})`
  );

  // Evaluate components
  const scoreDifference = vintoPlayer.score - champion.score;
  const cardDifference = vintoPlayer.cardCount - champion.cardCount;

  // Component 1: Score advantage (40%)
  const scoreAdvantage = Math.max(
    0,
    Math.min(1, (scoreDifference + 10) / 30)
  );

  // Component 2: Card count advantage (30%)
  const cardAdvantage = Math.max(0, Math.min(1, (cardDifference + 2) / 5));

  // Component 3: Champion's toss-in potential (20%)
  const championTossInScore = evaluateTossInPotential(state, champion);

  // Component 4: Vinto caller's threat level (10%)
  const vintoThreatScore = 1.0 - evaluateActionCardValue(state, vintoPlayer);

  const coalitionScore =
    scoreAdvantage * 0.4 +
    cardAdvantage * 0.3 +
    championTossInScore * 0.2 +
    vintoThreatScore * 0.1;

  console.log(
    `[Coalition Eval] Final score: ${coalitionScore.toFixed(3)} ` +
      `(scoreAdv: ${scoreAdvantage.toFixed(
        2
      )}, cardAdv: ${cardAdvantage.toFixed(2)})`
  );

  return Math.max(0, Math.min(1, coalitionScore));
}

/**
 * Find the coalition champion (member with best chance to win)
 */
export function findCoalitionChampion(
  state: MCTSGameState,
  vintoCallerId: string
): MCTSPlayerState | null {
  // Find all coalition members (everyone except Vinto caller)
  const coalitionMembers = state.players.filter(
    (p) => p.id !== vintoCallerId
  );

  if (coalitionMembers.length === 0) return null;

  // Find member with lowest score
  let champion = coalitionMembers[0];
  let championScore = champion.score;

  for (const member of coalitionMembers) {
    if (member.score < championScore) {
      championScore = member.score;
      champion = member;
    }
  }

  return champion;
}

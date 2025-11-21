// Pure functions for coalition evaluation

import { MCTSGameState, MCTSPlayerState } from './mcts-types';
import {
  evaluateTossInPotential,
  evaluateActionCardValue,
} from './evaluation-helpers';

/**
 * Evaluate state from coalition's perspective (DECENTRALIZED STRATEGY)
 * Goal: Ensure at least ONE coalition member beats Vinto caller
 *
 * DYNAMIC APPROACH:
 * - Primary focus on champion (60% weight) - member with best chance to win
 * - Secondary focus on other members (40% weight) - backup plan
 * - This allows flexibility: sometimes help champion, sometimes help others
 */
export function evaluateCoalitionState(
  state: MCTSGameState,
  _botPlayerId: string
): number {
  const vintoCallerId = state.vintoCallerId;
  if (!vintoCallerId) return 0;

  const vintoPlayer = state.players.find((p) => p.id === vintoCallerId);
  if (!vintoPlayer) return 0;

  // Get all coalition members
  const coalitionMembers = state.players.filter((p) => p.id !== vintoCallerId);
  if (coalitionMembers.length === 0) return 0;

  // Find coalition champion (lowest score member)
  const champion = findCoalitionChampion(state, vintoCallerId);
  if (!champion) return 0;

  console.log(
    `[Coalition Eval] Champion: ${champion.id} (score: ${champion.score}), ` +
      `Vinto: ${vintoCallerId} (score: ${vintoPlayer.score})`
  );

  // CHAMPION EVALUATION (60% weight)
  const championEval = evaluateCoalitionMember(state, champion, vintoPlayer);

  // SECONDARY MEMBERS EVALUATION (40% weight distributed)
  const secondaryMembers = coalitionMembers.filter((m) => m.id !== champion.id);
  let secondaryEval = 0;
  if (secondaryMembers.length > 0) {
    // Evaluate all secondary members and take the average
    const secondaryScores = secondaryMembers.map((member) =>
      evaluateCoalitionMember(state, member, vintoPlayer)
    );
    secondaryEval =
      secondaryScores.reduce((sum, score) => sum + score, 0) /
      secondaryMembers.length;
  }

  // DYNAMIC WEIGHTING: 60% champion, 40% secondary
  const coalitionScore = championEval * 0.6 + secondaryEval * 0.4;

  console.log(
    `[Coalition Eval] Dynamic score: ${coalitionScore.toFixed(3)} ` +
      `(champion: ${championEval.toFixed(2)} * 0.6, secondary: ${secondaryEval.toFixed(2)} * 0.4)`
  );

  return Math.max(0, Math.min(1, coalitionScore));
}

/**
 * Evaluate a single coalition member's position vs Vinto caller
 */
function evaluateCoalitionMember(
  state: MCTSGameState,
  member: MCTSPlayerState,
  vintoPlayer: MCTSPlayerState
): number {
  // Evaluate components for this member
  const scoreDifference = vintoPlayer.score - member.score;
  const cardDifference = vintoPlayer.cardCount - member.cardCount;

  // Component 1: Score advantage (40%)
  const scoreAdvantage = Math.max(0, Math.min(1, (scoreDifference + 10) / 30));

  // Component 2: Card count advantage (30%)
  const cardAdvantage = Math.max(0, Math.min(1, (cardDifference + 2) / 5));

  // Component 3: Member's toss-in potential (20%)
  const memberTossInScore = evaluateTossInPotential(state, member);

  // Component 4: Vinto caller's threat level (10%)
  const vintoThreatScore = 1.0 - evaluateActionCardValue(state, vintoPlayer);

  const memberScore =
    scoreAdvantage * 0.4 +
    cardAdvantage * 0.3 +
    memberTossInScore * 0.2 +
    vintoThreatScore * 0.1;

  return Math.max(0, Math.min(1, memberScore));
}

/**
 * Find the coalition champion (member with best chance to win)
 *
 * UPDATED: Uses minimum total card value (score) as criteria
 * Champion = player who will have the lowest total value after their turn
 * This is recalculated each turn as players' hands change
 */
export function findCoalitionChampion(
  state: MCTSGameState,
  vintoCallerId: string
): MCTSPlayerState | null {
  // Find all coalition members (everyone except Vinto caller)
  const coalitionMembers = state.players.filter((p) => p.id !== vintoCallerId);

  if (coalitionMembers.length === 0) return null;

  // Find member with minimum total card value (score)
  // This is the member with best chance to beat Vinto caller
  let champion = coalitionMembers[0];

  for (const member of coalitionMembers) {
    // Use score (total card value) as primary criteria
    if (member.score < champion.score) {
      champion = member;
    }
  }

  return champion;
}

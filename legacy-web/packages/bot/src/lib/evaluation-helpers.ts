import { isRankActionable, Rank } from '@vinto/shapes';
import { MCTSGameState, MCTSPlayerState } from './mcts-types';

/**
 * NEW: Evaluate toss-in potential
 *
 * This is the key to multi-step thinking!
 * We reward having matching cards because they enable cascading toss-ins
 */
export function evaluateTossInPotential(
  state: MCTSGameState,
  botPlayer: MCTSPlayerState
): number {
  // Build rank frequency map
  const rankCounts = new Map<Rank, number>();
  const rankValues = new Map<Rank, number>();

  for (let pos = 0; pos < botPlayer.cardCount; pos++) {
    const memory = botPlayer.knownCards.get(pos);
    if (memory && memory.confidence > 0.5 && memory.card) {
      const rank = memory.card.rank;
      rankCounts.set(rank, (rankCounts.get(rank) || 0) + 1);

      if (!rankValues.has(rank)) {
        rankValues.set(rank, memory.card.value);
      }
    }
  }

  let tossInValue = 0;

  // Calculate value of potential toss-ins
  rankCounts.forEach((count, rank) => {
    if (count >= 2) {
      // We have pairs/triples - HIGH value!
      const value = rankValues.get(rank) || 5;

      // Value formula: (count - 1) * value * multiplier
      // - Pairs worth 1x value
      // - Triples worth 2x value
      // - Quads worth 3x value
      const cascadeMultiplier = count - 1;
      tossInValue += cascadeMultiplier * value * 3; // 3x multiplier

      // EXTRA bonus for high-value pairs (removing 10s, Js, Qs, Ks)
      if (value >= 10) {
        tossInValue += cascadeMultiplier * 5;
      }

      // HUGE bonus for King pairs (enables King cascade + action)
      if (rank === 'K') {
        tossInValue += cascadeMultiplier * 10;
      }
    }
  });

  // Normalize to 0-1 (max potential toss-in value ≈ 60)
  return Math.min(1, tossInValue / 60);
}

/**
 * Evaluate relative position vs opponents
 */
export function evaluateRelativePosition(
  state: MCTSGameState,
  botPlayer: MCTSPlayerState
): number {
  const opponents = state.players.filter((p) => p.id !== botPlayer.id);

  const botScore = botPlayer.score;
  const opponentScores = opponents.map((p) => p.score);
  const avgOpponentScore =
    opponentScores.reduce((a, b) => a + b, 0) / opponentScores.length;
  const bestOpponentScore = Math.min(...opponentScores);

  // Score advantage
  const advantageVsAvg = avgOpponentScore - botScore;
  const advantageVsBest = bestOpponentScore - botScore;

  // Card count advantage
  const botCards = botPlayer.cardCount;
  const avgOpponentCards =
    opponents.reduce((sum, p) => sum + p.cardCount, 0) / opponents.length;
  const cardAdvantage = avgOpponentCards - botCards;

  // Normalize
  const scoreComponent = Math.min(1, Math.max(0, (advantageVsAvg + 10) / 30));
  const cardComponent = Math.min(1, Math.max(0, (cardAdvantage + 2) / 5));
  const competitiveComponent = Math.min(
    1,
    Math.max(0, (advantageVsBest + 5) / 25)
  );

  return (
    scoreComponent * 0.5 + cardComponent * 0.3 + competitiveComponent * 0.2
  );
}

/**
 * NEW: Evaluate action card value
 *
 * Having action cards (especially Kings) is valuable
 */
export function evaluateActionCardValue(
  state: MCTSGameState,
  botPlayer: MCTSPlayerState
): number {
  let actionValue = 0;

  const rankCounts = new Map<Rank, number>();

  for (let pos = 0; pos < botPlayer.cardCount; pos++) {
    const memory = botPlayer.knownCards.get(pos);
    if (memory && memory.confidence > 0.5 && memory.card) {
      const card = memory.card;
      const rank = card.rank;

      rankCounts.set(rank, (rankCounts.get(rank) || 0) + 1);

      // Action cards have strategic value
      if (isRankActionable(card.rank)) {
        if (rank === 'K') {
          actionValue += 15; // King is most powerful
        } else if (rank === 'Q' || rank === 'J') {
          actionValue += 10; // Queen/Jack very strong
        } else if (rank === '9' || rank === '10') {
          actionValue += 6; // Peek opponents
        } else if (rank === '7' || rank === '8') {
          actionValue += 4; // Peek own
        } else if (rank === 'A') {
          actionValue += 3; // Ace situational
        }
      }
    }
  }

  // SYNERGY BONUS: Having King + pairs enables powerful combos
  const hasKing = rankCounts.has('K');
  let pairCount = 0;
  rankCounts.forEach((count) => {
    if (count >= 2) pairCount++;
  });

  if (hasKing && pairCount > 0) {
    actionValue += pairCount * 8; // King + pairs = powerful synergy
  }

  // Normalize to 0-1 (max action value ≈ 50)
  return Math.min(1, actionValue / 50);
}

/**
 * Evaluate information advantage
 */
export function evaluateInformationAdvantage(
  state: MCTSGameState,
  botPlayer: MCTSPlayerState
): number {
  const ownKnowledge =
    botPlayer.cardCount > 0
      ? botPlayer.knownCards.size / botPlayer.cardCount
      : 0;

  // How much we know about opponents
  const opponents = state.players.filter((p) => p.id !== botPlayer.id);
  let totalOppCards = 0;
  let knownOppCards = 0;

  opponents.forEach((opp) => {
    totalOppCards += opp.cardCount;
    knownOppCards += opp.knownCards.size;
  });

  const oppKnowledge = totalOppCards > 0 ? knownOppCards / totalOppCards : 0;

  return ownKnowledge * 0.6 + oppKnowledge * 0.4;
}

/**
 * Evaluate threat level from opponents
 */
export function evaluateThreatLevel(
  state: MCTSGameState,
  botPlayer: MCTSPlayerState
): number {
  const opponents = state.players.filter((p) => p.id !== botPlayer.id);

  let threat = 0;

  opponents.forEach((opp) => {
    // Threat from fewer cards
    if (opp.cardCount < botPlayer.cardCount) {
      threat += (botPlayer.cardCount - opp.cardCount) * 0.1;
    }

    // Threat from lower score
    if (opp.score < botPlayer.score) {
      threat += (botPlayer.score - opp.score) / 40;
    }

    // Threat from unknown cards
    const unknownRatio =
      opp.cardCount > 0
        ? (opp.cardCount - opp.knownCards.size) / opp.cardCount
        : 0;
    threat += unknownRatio * 0.08;
  });

  // Invert (lower threat = higher score)
  return Math.max(0, 1 - threat);
}

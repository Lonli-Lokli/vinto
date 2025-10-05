// lib/game-helpers.ts

import { Card, Difficulty, NeverError, Player, Rank } from '../shapes';
import { getCardConfig } from '../constants/game-setup';

export const createDeck = (): Card[] => {
  const deck: Card[] = [];
  const cardSet = [0, 1, 2, 3];
  const noActionRanks = [2, 3, 4, 5, 6] as const;

  // Number cards 2-6
  for (const rank of noActionRanks) {
    const config = getCardConfig(`${rank}` as Rank);
    cardSet.forEach((no) => {
      deck.push({
        id: `${rank}_${no}`,
        rank: `${rank}`,
        value: config.value,
        played: false,
      });
    });
  }

  // Action cards
  const actionRanks: Rank[] = ['7', '8', '9', '10', 'J', 'Q', 'K', 'A'];

  actionRanks.forEach((rank) => {
    const config = getCardConfig(rank);
    cardSet.forEach((no) => {
      deck.push({
        id: `${rank}_${no}`,
        rank: rank,
        value: config.value,
        action: config.shortDescription,
        played: false,
      });
    });
  });

  // Jokers
  const jokerConfig = getCardConfig('Joker');
  deck.push(
    { id: 'Joker1', rank: 'Joker', value: jokerConfig.value, played: false },
    { id: 'Joker2', rank: 'Joker', value: jokerConfig.value, played: false }
  );

  return deck;
};

export const shuffleDeck = (deck: Card[]): Card[] => {
  const shuffled = [...deck];

  // Use crypto.getRandomValues for better entropy
  const randomBytes = new Uint32Array(shuffled.length);
  crypto.getRandomValues(randomBytes);

  for (let i = shuffled.length - 1; i > 0; i--) {
    const j = Math.floor((randomBytes[i] / 0x100000000) * (i + 1));
    [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
  }
  return shuffled;
};

export const getAIKnowledgeByDifficulty = (difficulty: Difficulty): number => {
  switch (difficulty) {
    case 'easy':
      return 0.3;
    case 'moderate':
      return 0.7;
    case 'hard':
      return 1;
    default:
      throw new NeverError(difficulty);
  }
};

export const calculatePlayerScore = (player: Player): number => {
  let knownScore = 0;
  let unknownCards = 0;

  player.cards.forEach((card, index) => {
    if (player.knownCardPositions.has(index)) {
      knownScore += card.value;
    } else {
      unknownCards++;
    }
  });

  return Math.round(knownScore + unknownCards * 4.5);
};

export const calculateActualScore = (player: Player): number => {
  return player.cards.reduce((total, card) => total + card.value, 0);
};

export const getWinnerInfo = (
  finalScores: { [playerId: string]: number },
  players: Player[]
) => {
  const lowestScore = Math.min(...Object.values(finalScores));
  const winners = Object.keys(finalScores).filter(
    (id) => finalScores[id] === lowestScore
  );

  const winnerNames = winners.map((id) => {
    const player = players.find((p) => p.id === id);
    return player ? player.name : 'Unknown';
  });

  return {
    winners: winnerNames,
    score: lowestScore,
    isMultipleWinners: winners.length > 1,
  };
};

// Re-export ALL_RANKS from game-setup for backwards compatibility
export { ALL_RANKS } from '../constants/game-setup';

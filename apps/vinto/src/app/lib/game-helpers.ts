// lib/game-helpers.ts

import { Card, Difficulty, NeverError, Player, Rank } from '../shapes';

export const createDeck = (): Card[] => {
  const deck: Card[] = [];
  const cardSet = [0, 1, 2, 3];
  const noActionRanks = [2, 3, 4, 5, 6] as const;

  // Number cards 2-6
  for (const rank of noActionRanks) {
    cardSet.forEach((no) => {
      deck.push({
        id: `${rank}_${no}`,
        rank: `${rank}`,
        value: rank,
        played: false,
      });
    });
  }

  // Action cards
  const actionCards = [
    { rank: '7' as const, value: 7, action: 'Peek 1 of your cards' },
    { rank: '8' as const, value: 8, action: 'Peek 1 of your cards' },
    { rank: '9' as const, value: 9, action: 'Peek 1 opponent card' },
    { rank: '10' as const, value: 10, action: 'Peek 1 opponent card' },
    { rank: 'J' as const, value: 10, action: 'Swap 2 face-down cards' },
    { rank: 'Q' as const, value: 10, action: 'Peek 2 cards, swap optional' },
    { rank: 'K' as const, value: 0, action: "Declare any card's action" },
    { rank: 'A' as const, value: 1, action: 'Force opponent to draw' },
  ];

  actionCards.forEach((card) => {
    cardSet.forEach((no) => {
      deck.push({
        id: `${card.rank}_${no}`,
        rank: card.rank,
        value: card.value,
        action: card.action,
        played: false,
      });
    });
  });

  // Jokers
  deck.push(
    { id: 'Joker1', rank: 'Joker', value: -1, played: false },
    { id: 'Joker2', rank: 'Joker', value: -1, played: false }
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


  export const ALL_RANKS: Rank[] = [
    '2',
    '3',
    '4',
    '5',
    '6',
    '7',
    '8',
    '9',
    '10',
    'J',
    'Q',
    'K',
    'A',
    'Joker'
  ];
// lib/game-helpers.ts

import { Card, Difficulty, Player } from "../shapes";

export const createDeck = (): Card[] => {
  const suits: Card['suit'][] = ['♠', '♥', '♦', '♣'];
  const deck: Card[] = [];
  
  // Number cards 2-6
  for (let rank = 2; rank <= 6; rank++) {
    suits.forEach(suit => {
      deck.push({
        id: `${rank}${suit}`,
        rank: rank.toString(),
        value: rank,
        suit
      });
    });
  }
  
  // Action cards
  const actionCards = [
    { rank: '7', value: 7, action: 'Peek 1 of your cards' },
    { rank: '8', value: 8, action: 'Peek 1 of your cards' },
    { rank: '9', value: 9, action: 'Peek 1 opponent card' },
    { rank: '10', value: 10, action: 'Peek 1 opponent card' },
    { rank: 'J', value: 10, action: 'Swap 2 face-down cards' },
    { rank: 'Q', value: 10, action: 'Peek 2 cards, swap optional' },
    { rank: 'K', value: 0, action: 'Declare any action' },
    { rank: 'A', value: 1, action: 'Force opponent to draw' },
  ];
  
  actionCards.forEach(card => {
    suits.forEach(suit => {
      deck.push({
        id: `${card.rank}${suit}`,
        rank: card.rank,
        value: card.value,
        action: card.action,
        suit
      });
    });
  });
  
  // Jokers
  deck.push(
    { id: 'Joker1', rank: '🃏', value: -1 },
    { id: 'Joker2', rank: '🃏', value: -1 }
  );
  
  return deck;
};

export const shuffleDeck = (deck: Card[]): Card[] => {
  const shuffled = [...deck];
  for (let i = shuffled.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
  }
  return shuffled;
};

export const getAIKnowledgeByDifficulty = (difficulty: Difficulty): number => {
  switch (difficulty) {
    case 'easy': return 0.3;
    case 'medium': return 0.6;
    case 'hard': return 0.8;
    default: return 0.6;
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

  return Math.round(knownScore + (unknownCards * 4.5));
};

export const getSuitColor = (suit?: string): string => {
  return suit === '♥' || suit === '♦' ? 'text-red-500' : 'text-gray-900';
};

export const calculateActualScore = (player: Player): number => {
  return player.cards.reduce((total, card) => total + card.value, 0);
};

export const getWinnerInfo = (finalScores: { [playerId: string]: number }, players: Player[]) => {
  const lowestScore = Math.min(...Object.values(finalScores));
  const winners = Object.keys(finalScores).filter(id => finalScores[id] === lowestScore);

  const winnerNames = winners.map(id => {
    const player = players.find(p => p.id === id);
    return player ? player.name : 'Unknown';
  });

  return {
    winners: winnerNames,
    score: lowestScore,
    isMultipleWinners: winners.length > 1
  };
};
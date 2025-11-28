import { describe, it, expect } from 'vitest';
import {
  getStrategicProbabilityWeight,
  sampleCardFromPool,
  buildAvailableRanksPool,
} from '../mcts-determinization';
import { Rank } from '@vinto/shapes';
import { MCTSGameState } from '../mcts-types';
import { BotMemory } from '../bot-memory';

describe('MCTS Determinization - Strategic Weighted Sampling', () => {
  describe('getStrategicProbabilityWeight', () => {
    it('should rank cards in strategic order', () => {
      // Strategic ranking: Joker > Q > J > K > 7 > 8 > A > 9 > 10 > 6 > 5 > 4 > 3 > 2
      const rankings: [Rank, number][] = [
        ['Joker', 2.0],
        ['Q', 1.8],
        ['J', 1.7],
        ['K', 1.6],
        ['7', 1.4],
        ['8', 1.4],
        ['A', 1.3],
        ['9', 1.1],
        ['10', 1.1],
        ['6', 0.7],
        ['5', 0.6],
        ['4', 0.5],
        ['3', 0.5],
        ['2', 0.5],
      ];

      for (const [rank, expectedWeight] of rankings) {
        const weight = getStrategicProbabilityWeight(rank);
        expect(weight).toBe(expectedWeight);
      }
    });

    it('should weight action cards higher than low cards', () => {
      const actionCards: Rank[] = ['Q', 'J', 'K', '7', '8', 'A'];
      const lowCards: Rank[] = ['2', '3', '4', '5', '6'];

      for (const actionRank of actionCards) {
        const actionWeight = getStrategicProbabilityWeight(actionRank);

        for (const lowRank of lowCards) {
          const lowWeight = getStrategicProbabilityWeight(lowRank);
          expect(actionWeight).toBeGreaterThan(lowWeight);
        }
      }
    });

    it('should have Joker as highest weight', () => {
      const allRanks: Rank[] = [
        'A',
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
        'Joker',
      ];

      const jokerWeight = getStrategicProbabilityWeight('Joker');

      for (const rank of allRanks) {
        if (rank !== 'Joker') {
          const weight = getStrategicProbabilityWeight(rank);
          expect(jokerWeight).toBeGreaterThan(weight);
        }
      }
    });

    it('should have low cards (2-6) with weight < 1.0', () => {
      const lowCards: Rank[] = ['2', '3', '4', '5', '6'];

      for (const rank of lowCards) {
        const weight = getStrategicProbabilityWeight(rank);
        expect(weight).toBeLessThan(1.0);
      }
    });
  });

  describe('sampleCardFromPool', () => {
    it('should sample from available ranks and remove it', () => {
      const availableRanks: Rank[] = ['2', '3', '4', '5', '6'];
      const initialLength = availableRanks.length;

      const card = sampleCardFromPool(availableRanks, 'player1', 0);

      // Card should be one of the original ranks
      expect(['2', '3', '4', '5', '6']).toContain(card.rank);

      // Pool should have one less card
      expect(availableRanks.length).toBe(initialLength - 1);

      // Sampled rank should be removed from pool
      expect(availableRanks).not.toContain(card.rank);
    });

    it('should use weighted sampling (statistical test)', () => {
      // This test runs many samples to verify weighted sampling works
      const samples = 10000;
      const counts: Record<string, number> = {
        Joker: 0,
        Q: 0,
        '2': 0,
      };

      for (let i = 0; i < samples; i++) {
        const availableRanks: Rank[] = ['Joker', 'Q', '2'];
        const card = sampleCardFromPool(availableRanks, 'test', 0);
        counts[card.rank]++;
      }

      // Joker (weight 2.0) should appear more than Q (weight 1.8)
      expect(counts.Joker).toBeGreaterThan(counts.Q);

      // Q (weight 1.8) should appear more than 2 (weight 0.5)
      expect(counts.Q).toBeGreaterThan(counts['2']);

      // Joker should appear MUCH more than 2
      expect(counts.Joker).toBeGreaterThan(counts['2'] * 2);
    });

    it('should not sample the same card twice from same pool', () => {
      const availableRanks: Rank[] = ['A', 'A', 'A', 'A'];

      const card1 = sampleCardFromPool([...availableRanks], 'player1', 0);
      const card2 = sampleCardFromPool([...availableRanks], 'player1', 1);

      // Both should be Ace
      expect(card1.rank).toBe('A');
      expect(card2.rank).toBe('A');

      // But they should have different IDs (different positions)
      expect(card1.id).not.toBe(card2.id);
    });

    it('should handle empty pool gracefully', () => {
      const availableRanks: Rank[] = [];

      expect(() => {
        sampleCardFromPool(availableRanks, 'player1', 0);
      }).toThrow();
    });
  });

  describe('buildAvailableRanksPool', () => {
    it('should remove discarded cards from pool', () => {
      const state: MCTSGameState = {
        players: [],
        currentPlayerIndex: 0,
        botPlayerId: 'bot1',
        discardPileTop: null,
        discardPile: [
          {
            id: 'd1',
            rank: 'A',
            value: 1,
            actionText: null,
            played: true,
          },
          {
            id: 'd2',
            rank: 'A',
            value: 1,
            actionText: null,
            played: true,
          },
        ],
        deckSize: 54,
        botMemory: new BotMemory('bot1', 'easy'),
        hiddenCards: new Map(),
        pendingCard: null,
        isTossInPhase: false,
        turnCount: 1,
        finalTurnTriggered: false,
        vintoCallerId: null,
        coalitionLeaderId: null,
        isTerminal: false,
        winner: null,
      };

      const pool = buildAvailableRanksPool(state);

      // Pool should have 2 fewer Aces (standard deck has 4)
      const aceCount = pool.filter((r) => r === 'A').length;
      expect(aceCount).toBe(2);
    });

    it('should remove known cards from pool', () => {
      const knownCards = new Map();
      knownCards.set(0, {
        card: {
          id: 'k1',
          rank: 'K',
          value: 0,
          actionText: null,
          played: false,
        },
        confidence: 1.0,
      });

      const state: MCTSGameState = {
        players: [
          {
            id: 'player1',
            cardCount: 5,
            knownCards,
            score: 0,
          },
        ],
        currentPlayerIndex: 0,
        botPlayerId: 'bot1',
        discardPileTop: null,
        discardPile: [],
        deckSize: 54,
        botMemory: new BotMemory('bot1', 'easy'),
        hiddenCards: new Map(),
        pendingCard: null,
        isTossInPhase: false,
        turnCount: 1,
        finalTurnTriggered: false,
        vintoCallerId: null,
        coalitionLeaderId: null,
        isTerminal: false,
        winner: null,
      };

      const pool = buildAvailableRanksPool(state);

      // Pool should have 1 fewer King (standard deck has 4)
      const kingCount = pool.filter((r) => r === 'K').length;
      expect(kingCount).toBe(3);
    });

    it('should remove pending card from pool', () => {
      const state: MCTSGameState = {
        players: [],
        currentPlayerIndex: 0,
        botPlayerId: 'bot1',
        discardPileTop: null,
        discardPile: [],
        deckSize: 54,
        botMemory: new BotMemory('bot1', 'easy'),
        hiddenCards: new Map(),
        pendingCard: {
          id: 'pending',
          rank: 'Q',
          value: 10,
          actionText: 'Peek & Swap',
          played: false,
        },
        isTossInPhase: false,
        turnCount: 1,
        finalTurnTriggered: false,
        vintoCallerId: null,
        coalitionLeaderId: null,
        isTerminal: false,
        winner: null,
      };

      const pool = buildAvailableRanksPool(state);

      // Pool should have 1 fewer Queen (standard deck has 4)
      const queenCount = pool.filter((r) => r === 'Q').length;
      expect(queenCount).toBe(3);
    });
  });
});

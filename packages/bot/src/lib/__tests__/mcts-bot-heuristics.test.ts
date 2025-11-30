/**
 * Unit tests for MCTS Bot heuristics functions
 * Tests pure heuristic functions that guide bot decision-making
 */

import { describe, it, expect } from 'vitest';
import {
  shouldAlwaysTakeDiscardPeekCard,
  shouldAlwaysUsePeekAction,
  shouldUseAceAction,
  shouldParticipateInTossIn,
  countUnknownCards,
  calculateHandScore,
} from '../mcts-bot-heuristics';
import { createTestCard, createTestPlayer } from './test-helpers';
import { Card, PlayerState } from '@vinto/shapes';

describe('MCTS Bot Heuristics', () => {
  const botId = 'bot1';

  describe('shouldAlwaysTakeDiscardPeekCard', () => {
    describe('Queen (Q) handling', () => {
      it('should always take unused Queen from discard regardless of unknown cards', () => {
        const discardTop = createTestCard('Q', 'discard-q');
        discardTop.played = false;

        // Bot with NO unknown cards (all 5 cards known)
        const botPlayerAllKnown = createTestPlayer(botId, 'Bot', false, [
          createTestCard('2', 'c1'),
          createTestCard('3', 'c2'),
          createTestCard('4', 'c3'),
          createTestCard('5', 'c4'),
          createTestCard('6', 'c5'),
        ]);
        botPlayerAllKnown.knownCardPositions = [0, 1, 2, 3, 4];

        expect(
          shouldAlwaysTakeDiscardPeekCard(discardTop, botPlayerAllKnown)
        ).toBe(true);
      });

      it('should always take unused Queen even with unknown cards', () => {
        const discardTop = createTestCard('Q', 'discard-q');
        discardTop.played = false;

        const botPlayerWithUnknowns = createTestPlayer(botId, 'Bot', false, [
          createTestCard('2', 'c1'),
          createTestCard('3', 'c2'),
          createTestCard('4', 'c3'),
          createTestCard('5', 'c4'),
          createTestCard('6', 'c5'),
        ]);
        botPlayerWithUnknowns.knownCardPositions = [0, 1]; // Only 2 known

        expect(
          shouldAlwaysTakeDiscardPeekCard(discardTop, botPlayerWithUnknowns)
        ).toBe(true);
      });

      it('should NOT take played Queen from discard', () => {
        const discardTop = createTestCard('Q', 'discard-q');
        discardTop.played = true; // Already used

        const botPlayer = createTestPlayer(botId, 'Bot', false, [
          createTestCard('2', 'c1'),
          createTestCard('3', 'c2'),
        ]);

        expect(shouldAlwaysTakeDiscardPeekCard(discardTop, botPlayer)).toBe(
          false
        );
      });
    });

    describe('7/8 (peek own) handling', () => {
      it('should take unused 7 from discard if bot has unknown cards', () => {
        const discardTop = createTestCard('7', 'discard-7');
        discardTop.played = false;

        const botPlayer = createTestPlayer(botId, 'Bot', false, [
          createTestCard('2', 'c1'),
          createTestCard('3', 'c2'),
          createTestCard('4', 'c3'),
        ]);
        botPlayer.knownCardPositions = [0]; // Only 1 known, 2 unknown

        expect(shouldAlwaysTakeDiscardPeekCard(discardTop, botPlayer)).toBe(
          true
        );
      });

      it('should take unused 8 from discard if bot has unknown cards', () => {
        const discardTop = createTestCard('8', 'discard-8');
        discardTop.played = false;

        const botPlayer = createTestPlayer(botId, 'Bot', false, [
          createTestCard('2', 'c1'),
          createTestCard('3', 'c2'),
          createTestCard('4', 'c3'),
        ]);
        botPlayer.knownCardPositions = [0, 1]; // 1 unknown

        expect(shouldAlwaysTakeDiscardPeekCard(discardTop, botPlayer)).toBe(
          true
        );
      });

      it('should NOT take 7 from discard if all cards are known', () => {
        const discardTop = createTestCard('7', 'discard-7');
        discardTop.played = false;

        const botPlayer = createTestPlayer(botId, 'Bot', false, [
          createTestCard('2', 'c1'),
          createTestCard('3', 'c2'),
          createTestCard('4', 'c3'),
        ]);
        botPlayer.knownCardPositions = [0, 1, 2]; // All known

        expect(shouldAlwaysTakeDiscardPeekCard(discardTop, botPlayer)).toBe(
          false
        );
      });

      it('should NOT take 8 from discard if all cards are known', () => {
        const discardTop = createTestCard('8', 'discard-8');
        discardTop.played = false;

        const botPlayer = createTestPlayer(botId, 'Bot', false, [
          createTestCard('2', 'c1'),
          createTestCard('3', 'c2'),
        ]);
        botPlayer.knownCardPositions = [0, 1]; // All known

        expect(shouldAlwaysTakeDiscardPeekCard(discardTop, botPlayer)).toBe(
          false
        );
      });

      it('should NOT take played 7 from discard even with unknowns', () => {
        const discardTop = createTestCard('7', 'discard-7');
        discardTop.played = true; // Already used

        const botPlayer = createTestPlayer(botId, 'Bot', false, [
          createTestCard('2', 'c1'),
          createTestCard('3', 'c2'),
        ]);
        botPlayer.knownCardPositions = [0]; // Has unknowns

        expect(shouldAlwaysTakeDiscardPeekCard(discardTop, botPlayer)).toBe(
          false
        );
      });
    });

    describe('Invalid cards (non-action or no-action)', () => {
      it('should NOT take Joker from discard (has no action)', () => {
        const discardTop = createTestCard('Joker', 'discard-joker');
        discardTop.played = false;

        const botPlayer = createTestPlayer(botId, 'Bot', false, [
          createTestCard('2', 'c1'),
          createTestCard('3', 'c2'),
        ]);

        expect(shouldAlwaysTakeDiscardPeekCard(discardTop, botPlayer)).toBe(
          false
        );
      });

      it('should NOT take number cards from discard (no action)', () => {
        const discardTop = createTestCard('5', 'discard-5');
        discardTop.played = false;

        const botPlayer = createTestPlayer(botId, 'Bot', false, [
          createTestCard('2', 'c1'),
          createTestCard('3', 'c2'),
        ]);

        expect(shouldAlwaysTakeDiscardPeekCard(discardTop, botPlayer)).toBe(
          false
        );
      });

      it('should NOT take King from discard (requires declaration, not in this heuristic)', () => {
        const discardTop = createTestCard('K', 'discard-k');
        discardTop.played = false;

        const botPlayer = createTestPlayer(botId, 'Bot', false, [
          createTestCard('2', 'c1'),
          createTestCard('3', 'c2'),
        ]);

        expect(shouldAlwaysTakeDiscardPeekCard(discardTop, botPlayer)).toBe(
          false
        );
      });

      it('should NOT take Jack from discard (complex swap logic, deferred to MCTS)', () => {
        const discardTop = createTestCard('J', 'discard-j');
        discardTop.played = false;

        const botPlayer = createTestPlayer(botId, 'Bot', false, [
          createTestCard('2', 'c1'),
          createTestCard('3', 'c2'),
        ]);

        expect(shouldAlwaysTakeDiscardPeekCard(discardTop, botPlayer)).toBe(
          false
        );
      });
    });

    describe('Edge cases', () => {
      it('should return false if discardTop is null', () => {
        const botPlayer = createTestPlayer(botId, 'Bot', false, [
          createTestCard('2', 'c1'),
        ]);

        expect(shouldAlwaysTakeDiscardPeekCard(null, botPlayer)).toBe(false);
      });

      it('should return false if bot has no cards', () => {
        const discardTop = createTestCard('Q', 'discard-q');
        discardTop.played = false;

        const botPlayer = createTestPlayer(botId, 'Bot', false, []);

        expect(shouldAlwaysTakeDiscardPeekCard(discardTop, botPlayer)).toBe(
          true
        ); // Queen is always taken regardless
      });
    });
  });

  describe('shouldAlwaysUsePeekAction', () => {
    describe('Queen (Q) handling', () => {
      it('should always use Queen action regardless of unknown cards', () => {
        const drawnCard = createTestCard('Q', 'drawn-q');

        const botPlayerAllKnown = createTestPlayer(botId, 'Bot', false, [
          createTestCard('2', 'c1'),
          createTestCard('3', 'c2'),
          createTestCard('4', 'c3'),
        ]);
        botPlayerAllKnown.knownCardPositions = [0, 1, 2]; // All known

        expect(shouldAlwaysUsePeekAction(drawnCard, botPlayerAllKnown)).toBe(
          true
        );
      });

      it('should always use Queen action with unknown cards', () => {
        const drawnCard = createTestCard('Q', 'drawn-q');

        const botPlayer = createTestPlayer(botId, 'Bot', false, [
          createTestCard('2', 'c1'),
          createTestCard('3', 'c2'),
          createTestCard('4', 'c3'),
        ]);
        botPlayer.knownCardPositions = [0]; // Only 1 known

        expect(shouldAlwaysUsePeekAction(drawnCard, botPlayer)).toBe(true);
      });
    });

    describe('7/8 (peek own) handling', () => {
      it('should use 7 action if bot has unknown cards', () => {
        const drawnCard = createTestCard('7', 'drawn-7');

        const botPlayer = createTestPlayer(botId, 'Bot', false, [
          createTestCard('2', 'c1'),
          createTestCard('3', 'c2'),
          createTestCard('4', 'c3'),
        ]);
        botPlayer.knownCardPositions = [0, 1]; // 1 unknown

        expect(shouldAlwaysUsePeekAction(drawnCard, botPlayer)).toBe(true);
      });

      it('should use 8 action if bot has unknown cards', () => {
        const drawnCard = createTestCard('8', 'drawn-8');

        const botPlayer = createTestPlayer(botId, 'Bot', false, [
          createTestCard('2', 'c1'),
          createTestCard('3', 'c2'),
        ]);
        botPlayer.knownCardPositions = [0]; // 1 unknown

        expect(shouldAlwaysUsePeekAction(drawnCard, botPlayer)).toBe(true);
      });

      it('should NOT use 7 action if all cards are known', () => {
        const drawnCard = createTestCard('7', 'drawn-7');

        const botPlayer = createTestPlayer(botId, 'Bot', false, [
          createTestCard('2', 'c1'),
          createTestCard('3', 'c2'),
        ]);
        botPlayer.knownCardPositions = [0, 1]; // All known

        expect(shouldAlwaysUsePeekAction(drawnCard, botPlayer)).toBe(false);
      });

      it('should NOT use 8 action if all cards are known', () => {
        const drawnCard = createTestCard('8', 'drawn-8');

        const botPlayer = createTestPlayer(botId, 'Bot', false, [
          createTestCard('2', 'c1'),
          createTestCard('3', 'c2'),
          createTestCard('4', 'c3'),
        ]);
        botPlayer.knownCardPositions = [0, 1, 2]; // All known

        expect(shouldAlwaysUsePeekAction(drawnCard, botPlayer)).toBe(false);
      });
    });

    describe('Non-peek action cards', () => {
      it('should NOT use Jack action (deferred to MCTS)', () => {
        const drawnCard = createTestCard('J', 'drawn-j');

        const botPlayer = createTestPlayer(botId, 'Bot', false, [
          createTestCard('2', 'c1'),
          createTestCard('3', 'c2'),
        ]);

        expect(shouldAlwaysUsePeekAction(drawnCard, botPlayer)).toBe(false);
      });

      it('should NOT use King action (deferred to MCTS)', () => {
        const drawnCard = createTestCard('K', 'drawn-k');

        const botPlayer = createTestPlayer(botId, 'Bot', false, [
          createTestCard('2', 'c1'),
          createTestCard('3', 'c2'),
        ]);

        expect(shouldAlwaysUsePeekAction(drawnCard, botPlayer)).toBe(false);
      });

      it('should NOT use Ace action (has separate heuristic)', () => {
        const drawnCard = createTestCard('A', 'drawn-a');

        const botPlayer = createTestPlayer(botId, 'Bot', false, [
          createTestCard('2', 'c1'),
          createTestCard('3', 'c2'),
        ]);

        expect(shouldAlwaysUsePeekAction(drawnCard, botPlayer)).toBe(false);
      });
    });

    describe('Invalid cards', () => {
      it('should return false for card with no action text', () => {
        const drawnCard = createTestCard('5', 'drawn-5');
        drawnCard.actionText = undefined;

        const botPlayer = createTestPlayer(botId, 'Bot', false, [
          createTestCard('2', 'c1'),
        ]);

        expect(shouldAlwaysUsePeekAction(drawnCard, botPlayer)).toBe(false);
      });

      it('should return false for already-played card', () => {
        const drawnCard = createTestCard('Q', 'drawn-q');
        drawnCard.played = true;

        const botPlayer = createTestPlayer(botId, 'Bot', false, [
          createTestCard('2', 'c1'),
        ]);

        expect(shouldAlwaysUsePeekAction(drawnCard, botPlayer)).toBe(false);
      });

      it('should return false for Joker (no action)', () => {
        const drawnCard = createTestCard('Joker', 'drawn-joker');

        const botPlayer = createTestPlayer(botId, 'Bot', false, [
          createTestCard('2', 'c1'),
        ]);

        expect(shouldAlwaysUsePeekAction(drawnCard, botPlayer)).toBe(false);
      });
    });
  });

  describe('shouldUseAceAction', () => {
    it('should swap Ace if bot has high-value known card (8+)', () => {
      const botPlayer = createTestPlayer(botId, 'Bot', false, [
        createTestCard('10', 'c1'),
        createTestCard('3', 'c2'),
        createTestCard('5', 'c3'),
      ]);
      botPlayer.knownCardPositions = [0, 1, 2]; // All known, has 10-point card

      const allPlayers = [
        botPlayer,
        createTestPlayer('p2', 'Player 2', false, [
          createTestCard('2', 'p2-c1'),
          createTestCard('3', 'p2-c2'),
        ]),
      ];

      expect(shouldUseAceAction(botPlayer, allPlayers, botId)).toBe(false);
    });

    it('should use Ace action if opponent is close to calling Vinto', () => {
      const botPlayer = createTestPlayer(botId, 'Bot', false, [
        createTestCard('5', 'c1'),
        createTestCard('6', 'c2'),
        createTestCard('7', 'c3'),
      ]); // Score: 18
      botPlayer.knownCardPositions = [0, 1, 2];

      const opponentPlayer = createTestPlayer('p2', 'Player 2', false, [
        createTestCard('2', 'p2-c1'),
        createTestCard('3', 'p2-c2'),
        createTestCard('A', 'p2-c3'),
      ]); // Score: 6 (low score, close to Vinto)

      const allPlayers = [botPlayer, opponentPlayer];

      // Opponent has low score (6 < 18 - 3) and few cards
      expect(shouldUseAceAction(botPlayer, allPlayers, botId)).toBe(true);
    });

    it('should swap Ace by default if no high-value cards and no Vinto threat', () => {
      const botPlayer = createTestPlayer(botId, 'Bot', false, [
        createTestCard('5', 'c1'),
        createTestCard('6', 'c2'),
        createTestCard('7', 'c3'),
      ]); // Score: 18, max value: 7
      botPlayer.knownCardPositions = [0, 1, 2];

      const opponentPlayer = createTestPlayer('p2', 'Player 2', false, [
        createTestCard('5', 'p2-c1'),
        createTestCard('6', 'p2-c2'),
        createTestCard('7', 'p2-c3'),
        createTestCard('8', 'p2-c4'),
      ]); // Score: 26 (not close to Vinto)

      const allPlayers = [botPlayer, opponentPlayer];

      expect(shouldUseAceAction(botPlayer, allPlayers, botId)).toBe(false);
    });
  });

  describe('shouldParticipateInTossIn', () => {
    it('should participate if bot has matching card', () => {
      const botPlayer = createTestPlayer(botId, 'Bot', false, [
        createTestCard('7', 'c1'),
        createTestCard('3', 'c2'),
        createTestCard('K', 'c3'),
      ]);

      expect(shouldParticipateInTossIn(['7'], botPlayer)).toBe(true);
    });

    it('should not participate if bot has no matching cards', () => {
      const botPlayer = createTestPlayer(botId, 'Bot', false, [
        createTestCard('2', 'c1'),
        createTestCard('3', 'c2'),
        createTestCard('K', 'c3'),
      ]);

      expect(shouldParticipateInTossIn(['7'], botPlayer)).toBe(false);
    });

    it('should participate with multiple matching ranks', () => {
      const botPlayer = createTestPlayer(botId, 'Bot', false, [
        createTestCard('10', 'c1'),
        createTestCard('J', 'c2'),
        createTestCard('K', 'c3'),
      ]);

      expect(shouldParticipateInTossIn(['10', 'J', 'Q'], botPlayer)).toBe(
        true
      );
    });

    it('should not participate with Joker in toss-in ranks (negative value)', () => {
      const botPlayer = createTestPlayer(botId, 'Bot', false, [
        createTestCard('Joker', 'c1'),
        createTestCard('2', 'c2'),
      ]);

      expect(shouldParticipateInTossIn(['Joker'], botPlayer)).toBe(false);
    });
  });

  describe('countUnknownCards', () => {
    it('should count unknown cards correctly', () => {
      const player = createTestPlayer(botId, 'Bot', false, [
        createTestCard('2', 'c1'),
        createTestCard('3', 'c2'),
        createTestCard('4', 'c3'),
        createTestCard('5', 'c4'),
        createTestCard('6', 'c5'),
      ]);
      player.knownCardPositions = [0, 1]; // 2 known, 3 unknown

      expect(countUnknownCards(player)).toBe(3);
    });

    it('should return 0 if all cards are known', () => {
      const player = createTestPlayer(botId, 'Bot', false, [
        createTestCard('2', 'c1'),
        createTestCard('3', 'c2'),
      ]);
      player.knownCardPositions = [0, 1]; // All known

      expect(countUnknownCards(player)).toBe(0);
    });

    it('should return total cards if none are known', () => {
      const player = createTestPlayer(botId, 'Bot', false, [
        createTestCard('2', 'c1'),
        createTestCard('3', 'c2'),
        createTestCard('4', 'c3'),
      ]);
      player.knownCardPositions = []; // None known

      expect(countUnknownCards(player)).toBe(3);
    });
  });

  describe('calculateHandScore', () => {
    it('should calculate hand score correctly', () => {
      const cards: Card[] = [
        createTestCard('2', 'c1'), // 2
        createTestCard('5', 'c2'), // 5
        createTestCard('10', 'c3'), // 10
        createTestCard('K', 'c4'), // 0
        createTestCard('Joker', 'c5'), // -1
      ];

      expect(calculateHandScore(cards)).toBe(16);
    });

    it('should return 0 for empty hand', () => {
      expect(calculateHandScore([])).toBe(0);
    });

    it('should handle negative values (Joker)', () => {
      const cards: Card[] = [
        createTestCard('Joker', 'c1'), // -1
        createTestCard('Joker', 'c2'), // -1
        createTestCard('A', 'c3'), // 1
      ];

      expect(calculateHandScore(cards)).toBe(-1);
    });
  });
});

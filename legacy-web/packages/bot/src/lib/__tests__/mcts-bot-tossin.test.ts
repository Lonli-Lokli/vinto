/**
 * Unit tests for MCTS Bot toss-in functionality
 * Tests that bots correctly participate in toss-ins and select valid cards
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { MCTSBotDecisionService } from '../mcts-bot-decision';
import {
  createTestCard,
  createTestPlayer,
  createTestState,
  createBotContext,
  toPile,
} from './test-helpers';

describe('MCTS Bot - Toss-In Functionality', () => {
  let bot: MCTSBotDecisionService;
  const botId = 'bot1';

  beforeEach(() => {
    // Use 'hard' difficulty for tests to ensure 100% card memory confidence
    // This prevents probabilistic failures where bot fails to remember cards
    bot = new MCTSBotDecisionService('hard');
  });

  describe('shouldParticipateInTossIn', () => {
    it('should participate when bot has a matching card (single rank)', () => {
      // Setup: Bot has a 7 in hand, toss-in is for 7
      const gameState = createTestState({
        subPhase: 'toss_queue_active',
        players: [
          createTestPlayer(botId, 'Bot 1', false, [
            createTestCard('7', 'bot-card-1'),
            createTestCard('3', 'bot-card-2'),
            createTestCard('K', 'bot-card-3'),
          ]),
          createTestPlayer('p2', 'Player 2', true, [
            createTestCard('2', 'p2-card-1'),
            createTestCard('5', 'p2-card-2'),
          ]),
        ],
        activeTossIn: {
          ranks: ['7'],
          initiatorId: 'p1',
          originalPlayerIndex: 0,
          participants: [],
          queuedActions: [],
          waitingForInput: false,
          playersReadyForNextTurn: [],
        },
        discardPile: toPile([createTestCard('7', 'discard-7')]),
      });

      const context = createBotContext(botId, gameState);
      const shouldParticipate = bot.shouldParticipateInTossIn(['7'], context);

      expect(shouldParticipate).toBe(true);
    });

    it('should participate when bot has a matching card (multiple ranks)', () => {
      // Setup: Bot has a 10 (high value), toss-in is for [10, J, Q]
      // Bot should want to toss in the 10 to reduce score
      const gameState = createTestState({
        subPhase: 'toss_queue_active',
        players: [
          createTestPlayer(botId, 'Bot 1', false, [
            createTestCard('10', 'bot-card-1'), // High value - bot wants to discard
            createTestCard('K', 'bot-card-2'), // Zero value - good to keep
            createTestCard('2', 'bot-card-3'), // Low value - good to keep
          ]),
          createTestPlayer('p2', 'Player 2', true, [
            createTestCard('A', 'p2-card-1'),
            createTestCard('J', 'p2-card-2'),
          ]),
        ],
        activeTossIn: {
          ranks: ['10', 'J', 'Q'],
          initiatorId: 'p1',
          originalPlayerIndex: 0,
          participants: [],
          queuedActions: [],
          waitingForInput: false,
          playersReadyForNextTurn: [],
        },
        discardPile: toPile([
          createTestCard('10', 'discard-10'),
          createTestCard('J', 'discard-j'),
          createTestCard('Q', 'discard-q'),
        ]),
      });

      const context = createBotContext(botId, gameState);
      const shouldParticipate = bot.shouldParticipateInTossIn(
        ['10', 'J', 'Q'],
        context
      );

      expect(shouldParticipate).toBe(true);
    });

    it('should not participate when bot does not have matching cards', () => {
      // Setup: Bot has [K, Q, J], toss-in is for 7
      const gameState = createTestState({
        subPhase: 'toss_queue_active',
        players: [
          createTestPlayer(botId, 'Bot 1', false, [
            createTestCard('K', 'bot-card-1'),
            createTestCard('Q', 'bot-card-2'),
            createTestCard('J', 'bot-card-3'),
          ]),
          createTestPlayer('p2', 'Player 2', true, [
            createTestCard('2', 'p2-card-1'),
            createTestCard('5', 'p2-card-2'),
          ]),
        ],
        activeTossIn: {
          ranks: ['7'],
          initiatorId: 'p1',
          originalPlayerIndex: 0,
          participants: [],
          queuedActions: [],
          waitingForInput: false,
          playersReadyForNextTurn: [],
        },
        discardPile: toPile([createTestCard('7', 'discard-7')]),
      });

      const context = createBotContext(botId, gameState);
      const shouldParticipate = bot.shouldParticipateInTossIn(['7'], context);

      expect(shouldParticipate).toBe(false);
    });

    it('should handle multiple matching cards and choose to participate', () => {
      // Setup: Bot has two 7s, toss-in is for 7
      const gameState = createTestState({
        subPhase: 'toss_queue_active',
        players: [
          createTestPlayer(botId, 'Bot 1', false, [
            createTestCard('7', 'bot-card-1'),
            createTestCard('7', 'bot-card-2'),
            createTestCard('K', 'bot-card-3'),
          ]),
          createTestPlayer('p2', 'Player 2', true, [
            createTestCard('2', 'p2-card-1'),
            createTestCard('5', 'p2-card-2'),
          ]),
        ],
        activeTossIn: {
          ranks: ['7'],
          initiatorId: 'p1',
          originalPlayerIndex: 0,
          participants: [],
          queuedActions: [],
          waitingForInput: false,
          playersReadyForNextTurn: [],
        },
        discardPile: toPile([createTestCard('7', 'discard-7')]),
      });

      const context = createBotContext(botId, gameState);
      const shouldParticipate = bot.shouldParticipateInTossIn(['7'], context);

      expect(shouldParticipate).toBe(true);
    });
  });

  describe('Multiple matching cards scenarios', () => {
    it('should participate when bot has one of many possible matching ranks', () => {
      // Setup: Bot has [10, J, K], toss-in is for [10, J]
      // Bot should want to toss in the 10 or J (high value cards)
      const gameState = createTestState({
        subPhase: 'toss_queue_active',
        currentPlayerIndex: 0,
        players: [
          createTestPlayer(botId, 'Bot 1', false, [
            createTestCard('10', 'bot-card-1'), // Position 0 - matches, high value
            createTestCard('J', 'bot-card-2'), // Position 1 - also matches, high value
            createTestCard('K', 'bot-card-3'), // Position 2 - King (0 value, good to keep)
          ]),
          createTestPlayer('p2', 'Player 2', true, [
            createTestCard('2', 'p2-card-1'),
            createTestCard('A', 'p2-card-2'),
          ]),
        ],
        activeTossIn: {
          ranks: ['10', 'J'],
          initiatorId: 'p1',
          originalPlayerIndex: 0,
          participants: [],
          queuedActions: [],
          waitingForInput: false,
          playersReadyForNextTurn: [],
        },
        discardPile: toPile([
          createTestCard('10', 'discard-10'),
          createTestCard('J', 'discard-j'),
        ]),
      });

      const context = createBotContext(botId, gameState);
      const shouldParticipate = bot.shouldParticipateInTossIn(
        ['10', 'J'],
        context
      );
      expect(shouldParticipate).toBe(true);
    });

    it('should not participate when bot has no matching ranks from multiple options', () => {
      // Setup: Bot has [10, J, K], toss-in is for [7, 5, 3]
      const gameState = createTestState({
        subPhase: 'toss_queue_active',
        currentPlayerIndex: 0,
        players: [
          createTestPlayer(botId, 'Bot 1', false, [
            createTestCard('10', 'bot-card-1'),
            createTestCard('J', 'bot-card-2'),
            createTestCard('K', 'bot-card-3'),
          ]),
          createTestPlayer('p2', 'Player 2', true, [
            createTestCard('2', 'p2-card-1'),
            createTestCard('A', 'p2-card-2'),
          ]),
        ],
        activeTossIn: {
          ranks: ['7', '5', '3'],
          initiatorId: 'p1',
          originalPlayerIndex: 0,
          participants: [],
          queuedActions: [],
          waitingForInput: false,
          playersReadyForNextTurn: [],
        },
        discardPile: toPile([
          createTestCard('7', 'discard-7'),
          createTestCard('5', 'discard-5'),
          createTestCard('3', 'discard-3'),
        ]),
      });

      const context = createBotContext(botId, gameState);
      const shouldParticipate = bot.shouldParticipateInTossIn(
        ['7', '5', '3'],
        context
      );
      expect(shouldParticipate).toBe(false);
    });
  });

  describe('Edge cases', () => {
    it('should handle when bot has all matching cards', () => {
      // Setup: Bot has [7, 7, 7], toss-in is for 7
      const gameState = createTestState({
        subPhase: 'toss_queue_active',
        currentPlayerIndex: 0,
        players: [
          createTestPlayer(botId, 'Bot 1', false, [
            createTestCard('7', 'bot-card-1'),
            createTestCard('7', 'bot-card-2'),
            createTestCard('7', 'bot-card-3'),
          ]),
          createTestPlayer('p2', 'Player 2', true, [
            createTestCard('2', 'p2-card-1'),
            createTestCard('5', 'p2-card-2'),
          ]),
        ],
        activeTossIn: {
          ranks: ['7'],
          initiatorId: 'p1',
          originalPlayerIndex: 0,
          participants: [],
          queuedActions: [],
          waitingForInput: false,
          playersReadyForNextTurn: [],
        },
        discardPile: toPile([createTestCard('7', 'discard-7')]),
      });

      const context = createBotContext(botId, gameState);
      const shouldParticipate = bot.shouldParticipateInTossIn(['7'], context);
      expect(shouldParticipate).toBe(true);
    });

    it('should handle empty hand gracefully', () => {
      // Setup: Bot has no cards
      const gameState = createTestState({
        subPhase: 'toss_queue_active',
        players: [
          createTestPlayer(botId, 'Bot 1', false, []),
          createTestPlayer('p2', 'Player 2', true, [
            createTestCard('2', 'p2-card-1'),
            createTestCard('5', 'p2-card-2'),
          ]),
        ],
        activeTossIn: {
          ranks: ['7'],
          initiatorId: 'p1',
          originalPlayerIndex: 0,
          participants: [],
          queuedActions: [],
          waitingForInput: false,
          playersReadyForNextTurn: [],
        },
        discardPile: toPile([createTestCard('7', 'discard-7')]),
      });

      const context = createBotContext(botId, gameState);
      const shouldParticipate = bot.shouldParticipateInTossIn(['7'], context);
      expect(shouldParticipate).toBe(false);
    });
  });
});

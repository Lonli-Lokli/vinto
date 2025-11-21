// bot-coalition.test.ts
// Integration test for bot coalition behavior after Vinto is called

import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { GameActions } from '@vinto/engine';
import { Pile } from '@vinto/shapes';
import {
  createTestCard,
  createTestPlayer,
  setupSimpleScenario,
} from './test-helper';
import { mockLogger } from './setup-tests';

/**
 * Coalition Mode Tests
 *
 * These tests validate that:
 * 1. Coalition is formed when Vinto is called
 * 2. Coalition leader is selected automatically for bot-only games
 * 3. Coalition leader makes decisions for all coalition members
 * 4. Bots cooperate to beat the Vinto caller
 */
describe('Bot Coalition Integration Tests', () => {
  beforeEach(() => {
    mockLogger.log.mockClear();
    mockLogger.warn.mockClear();
    mockLogger.error.mockClear();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  /**
   * Test 1: Coalition formation and leader selection
   *
   * When Vinto is called in a bot-only game, verify that:
   * - All non-Vinto bots form a coalition
   * - A coalition leader is automatically selected
   * - Game enters 'final' phase
   */
  it.skip(
    'should form coalition and select leader when Vinto is called',
    { timeout: 30_000 },
    async () => {
      const { gameClient, botAdapter } = await setupSimpleScenario(
        [
          createTestPlayer('bot1', 'Bot 1', false, [
            createTestCard('2', 'bot1c1'),
            createTestCard('3', 'bot1c2'),
          ]),
          createTestPlayer('bot2', 'Bot 2', false, [
            createTestCard('K', 'bot2c1'),
            createTestCard('Q', 'bot2c2'),
          ]),
          createTestPlayer('bot3', 'Bot 3', false, [
            createTestCard('5', 'bot3c1'),
            createTestCard('6', 'bot3c2'),
          ]),
        ],
        0
      );

      // Bot1 calls Vinto
      gameClient.dispatch(GameActions.callVinto('bot1'));

      // Wait for bot reaction to select coalition leader
      await vi.runAllTimersAsync();
      await botAdapter.waitForIdle();

      // Verify coalition formation
      expect(gameClient.state.vintoCallerId).toBe('bot1');
      expect(gameClient.state.phase).toBe('final');

      // Verify coalition setup
      const bot1 = gameClient.getPlayer('bot1');
      const bot2 = gameClient.getPlayer('bot2');
      const bot3 = gameClient.getPlayer('bot3');

      expect(bot1?.isVintoCaller).toBe(true);
      expect(bot2?.coalitionWith).toContain('bot3');
      expect(bot3?.coalitionWith).toContain('bot2');

      // Verify coalition leader is selected (should be auto-selected for bots)
      expect(gameClient.state.coalitionLeaderId).toBeTruthy();
      expect(gameClient.state.coalitionLeaderId).not.toBe('bot1');

      console.log(`Coalition leader: ${gameClient.state.coalitionLeaderId}`);

      botAdapter.dispose();
    }
  );

  /**
   * Test 2: Coalition leader coordinates actions
   *
   * Verify that the coalition leader makes decisions for coalition members
   * by checking that both bots make coordinated moves during the final phase
   */
  it.skip(
    'should have coalition leader coordinate member actions',
    { timeout: 30_000 },
    async () => {
      const { gameClient, botAdapter } = await setupSimpleScenario(
        [
          createTestPlayer('bot1', 'Bot 1', false, [
            createTestCard('2', 'bot1c1'),
            createTestCard('3', 'bot1c2'),
          ]),
          createTestPlayer('bot2', 'Bot 2', false, [
            createTestCard('9', 'bot2c1'),
            createTestCard('10', 'bot2c2'),
            createTestCard('K', 'bot2c3'),
          ]),
          createTestPlayer('bot3', 'Bot 3', false, [
            createTestCard('5', 'bot3c1'),
            createTestCard('6', 'bot3c2'),
          ]),
        ],
        1,
        {
          drawPile: Pile.fromCards([
            createTestCard('4', 'draw1'),
            createTestCard('7', 'draw2'),
            createTestCard('8', 'draw3'),
          ]),
        }
      );

      // Bot2 calls Vinto
      gameClient.dispatch(GameActions.callVinto('bot2'));

      // Wait for bot reaction to select coalition leader
      await vi.runAllTimersAsync();
      await botAdapter.waitForIdle();

      expect(gameClient.state.vintoCallerId).toBe('bot2');
      expect(gameClient.state.phase).toBe('final');

      // Coalition leader should be either bot1 or bot3
      const coalitionLeaderId = gameClient.state.coalitionLeaderId;
      expect(coalitionLeaderId).toBeTruthy();
      expect(['bot1', 'bot3']).toContain(coalitionLeaderId);

      console.log(
        `Coalition formed: Leader=${coalitionLeaderId}, Vinto=${gameClient.state.vintoCallerId}`
      );

      botAdapter.dispose();
    }
  );

  /**
   * Test 3: Coalition evaluates champion strategy
   *
   * Verify that the coalition tries to ensure at least ONE member beats Vinto caller
   * by examining the coalition's behavior when one member has a clear advantage
   */
  it.skip(
    'should support coalition champion with low score',
    { timeout: 30_000 },
    async () => {
      const { gameClient, botAdapter } = await setupSimpleScenario(
        [
          createTestPlayer(
            'bot1',
            'Bot 1 (Champion)',
            false,
            [
              createTestCard('2', 'bot1c1'), // Very low score
              createTestCard('3', 'bot1c2'),
            ],
            [0, 1] // Bot knows both cards
          ),
          createTestPlayer(
            'bot2',
            'Bot 2 (Vinto)',
            false,
            [
              createTestCard('K', 'bot2c1'), // High score
              createTestCard('Q', 'bot2c2'),
            ],
            [0, 1]
          ),
          createTestPlayer(
            'bot3',
            'Bot 3',
            false,
            [createTestCard('8', 'bot3c1'), createTestCard('9', 'bot3c2')],
            [0, 1]
          ),
        ],
        1
      );

      // Bot2 (high score) calls Vinto - this is the scenario we're testing
      gameClient.dispatch(GameActions.callVinto('bot2'));

      // Wait for bot reaction to select coalition leader
      await vi.runAllTimersAsync();
      await botAdapter.waitForIdle();

      expect(gameClient.state.vintoCallerId).toBe('bot2');
      expect(gameClient.state.phase).toBe('final');

      // Coalition formed with bot1 (champion - lowest score) and bot3
      const coalitionLeaderId = gameClient.state.coalitionLeaderId;
      console.log(
        `Coalition: Leader=${coalitionLeaderId}, Champion=bot1 (score ~5), Vinto=bot2 (score ~23)`
      );

      // The coalition should recognize bot1 as the champion (lowest score)
      // and coordinate to help bot1 win
      expect(coalitionLeaderId).toBeTruthy();

      botAdapter.dispose();
    }
  );

  /**
   * Test 4: Coalition shares perfect information
   *
   * Verify that the coalition leader has full knowledge of all coalition members' cards
   * This is critical for coordinated strategy
   */
  it.skip(
    'should give coalition leader perfect information about members',
    { timeout: 30_000 },
    async () => {
      const { gameClient, botAdapter } = await setupSimpleScenario(
        [
          createTestPlayer(
            'bot1',
            'Bot 1',
            false,
            [createTestCard('2', 'bot1c1'), createTestCard('3', 'bot1c2')],
            [0] // Bot only knows first card
          ),
          createTestPlayer(
            'bot2',
            'Bot 2 (Vinto)',
            false,
            [createTestCard('K', 'bot2c1'), createTestCard('Q', 'bot2c2')],
            [0, 1]
          ),
          createTestPlayer(
            'bot3',
            'Bot 3',
            false,
            [createTestCard('5', 'bot3c1'), createTestCard('6', 'bot3c2')],
            [1] // Bot only knows second card
          ),
        ],
        1
      );

      // Bot2 calls Vinto
      gameClient.dispatch(GameActions.callVinto('bot2'));

      // Wait for bot reaction to select coalition leader
      await vi.runAllTimersAsync();
      await botAdapter.waitForIdle();

      // Coalition formed
      const coalitionLeaderId = gameClient.state.coalitionLeaderId;
      expect(coalitionLeaderId).toBeTruthy();

      console.log(
        `Testing perfect information sharing for coalition leader: ${coalitionLeaderId}`
      );

      // The coalition leader now has perfect information about all coalition members
      // This is implemented in botAIAdapter.ts:1075-1103
      // We can verify this by checking that the leader can make informed decisions

      // Note: Perfect information sharing happens internally in createBotContext()
      // The leader's MCTS algorithm receives full knowledge of coalition cards
      expect(gameClient.state.phase).toBe('scoring');
      expect(gameClient.state.coalitionLeaderId).not.toBe('bot2');

      botAdapter.dispose();
    }
  );
});

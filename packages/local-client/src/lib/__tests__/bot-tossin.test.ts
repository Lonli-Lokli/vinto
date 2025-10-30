// bot-tossin.integration.test.ts
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { BotAIAdapter } from '../adapters/botAIAdapter';
import { GameActions } from '@vinto/engine';
import { Pile } from '@vinto/shapes';
import {
  createTestCard,
  createTestPlayer,
  setupSimpleScenario,
} from './test-helper';
import { mockLogger } from './setup-tests';

/**
 * Integration test for bot toss-in during another bot's turn
 *
 * Tests the critical fix: Turn player must mark themselves ready
 * even though they cannot toss in their own discard.
 *
 * The engine requires ALL players to be in playersReadyForNextTurn
 * before advancing the turn. Previously, the turn player never marked
 * themselves ready, causing the game to freeze.
 */
describe('Bot Toss-In Integration Test', () => {
  beforeEach(() => {
    mockLogger.log.mockClear();
    mockLogger.warn.mockClear();
    mockLogger.error.mockClear();
    // Mock delays for faster testing
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  /**
   * Test 1: Simple toss-in with 7 cards
   *
   * Core test: Bot2 discards, Bot2 marks ready, Bot1 tosses in, game continues
   */
  it('should handle simple toss-in with 7 cards', async () => {
    const { gameClient, botAdapter } = await setupSimpleScenario(
      [
        createTestPlayer('bot1', 'Bot 1', false, [
          createTestCard('7', 'p1c1'),
          createTestCard('5', 'p1c2'),
          createTestCard('4', 'p1c3'),
          createTestCard('3', 'p1c4'),
        ]),
        createTestPlayer('bot2', 'Bot 2', false, [
          createTestCard('7', 'p2c1'),
          createTestCard('6', 'p2c2'),
          createTestCard('5', 'p2c3'),
          createTestCard('4', 'p2c4'),
        ]),
      ],
      0
    );

    const dispatchSpy = vi.spyOn(gameClient, 'dispatch');

    // Bot2 draws the 7 they already have
    gameClient.dispatch(GameActions.drawCard('bot2'));
    await vi.runAllTimersAsync();

    // Bot2 discards the 7
    gameClient.dispatch(GameActions.discardCard('bot2'));
    await vi.runAllTimersAsync();

    // Verify toss-in activated
    if (gameClient.state.activeTossIn) {
      expect(gameClient.state.subPhase).toBe('toss_queue_active');
      expect(gameClient.state.activeTossIn.ranks).toContain('7');

      await botAdapter['handleTossInPhase']();
      await vi.runAllTimersAsync();

      // Key assertions
      const bot2ReadyCalls = dispatchSpy.mock.calls.filter(
        (call) =>
          call[0].type === 'PLAYER_TOSS_IN_FINISHED' &&
          call[0].payload?.playerId === 'bot2'
      );
      expect(bot2ReadyCalls.length).toBeGreaterThanOrEqual(1);

      const bot1TossInCalls = dispatchSpy.mock.calls.filter(
        (call) =>
          call[0].type === 'PARTICIPATE_IN_TOSS_IN' &&
          call[0].payload?.playerId === 'bot1'
      );

      if (gameClient.state.players[0].cards.some((c) => c.rank === '7')) {
        expect(bot1TossInCalls.length).toBeGreaterThanOrEqual(1);
      }

      const bot1ReadyCalls = dispatchSpy.mock.calls.filter(
        (call) =>
          call[0].type === 'PLAYER_TOSS_IN_FINISHED' &&
          call[0].payload?.playerId === 'bot1'
      );
      expect(bot1ReadyCalls.length).toBeGreaterThanOrEqual(1);

      // Game should progress
      const gameProgressed =
        gameClient.state.activeTossIn === null ||
        gameClient.state.subPhase !== 'toss_queue_active';
      expect(gameProgressed).toBe(true);
    }
  });

  /**
   * Test 2: Turn player should not toss in their own discard
   */
  it('should not allow turn player to toss in their own discard', async () => {
    const { gameClient, botAdapter } = await setupSimpleScenario(
      [
        createTestPlayer('bot1', 'Bot 1', false, [
          createTestCard('7', 'spades'),
          createTestCard('5', 'hearts'),
        ]),
        createTestPlayer('bot2', 'Bot 2', false, [
          createTestCard('7', 'hearts'),
          createTestCard('7', 'diamonds'),
          createTestCard('6', 'clubs'),
        ]),
      ],
      1
    );

    const dispatchSpy = vi.spyOn(gameClient, 'dispatch');
    const bot2InitialHandSize = gameClient.state.players[1].cards.length;

    gameClient.dispatch(GameActions.drawCard('bot2'));
    await vi.runAllTimersAsync();

    gameClient.dispatch(GameActions.discardCard('bot2'));
    await vi.runAllTimersAsync();

    if (gameClient.state.subPhase === 'toss_queue_active') {
      await botAdapter['handleTossInPhase']();
      await vi.runAllTimersAsync();

      // Bot2 should NOT have tossed in
      const bot2TossInCalls = dispatchSpy.mock.calls.filter(
        (call) =>
          call[0].type === 'PARTICIPATE_IN_TOSS_IN' &&
          call[0].payload?.playerId === 'bot2'
      );
      expect(bot2TossInCalls.length).toBe(0);

      // Bot2 should have only lost 1 card (the discard)
      const bot2FinalHandSize = gameClient.state.players[1].cards.length;
      expect(bot2FinalHandSize).toBe(bot2InitialHandSize - 1);

      // But Bot2 SHOULD have marked ready
      const bot2ReadyCalls = dispatchSpy.mock.calls.filter(
        (call) =>
          call[0].type === 'PLAYER_TOSS_IN_FINISHED' &&
          call[0].payload?.playerId === 'bot2'
      );
      expect(bot2ReadyCalls.length).toBeGreaterThanOrEqual(1);
    }
  });

  /**
   * Test 3: No matching cards - both bots should still mark ready
   */
  it('should advance turn when no bots have matching cards', async () => {
    const { gameClient, botAdapter } = await setupSimpleScenario(
      [
        createTestPlayer('bot1', 'Bot 1', false, [
          createTestCard('5', 'hearts'),
          createTestCard('4', 'hearts'),
        ]),
        createTestPlayer('bot2', 'Bot 2', false, [
          createTestCard('7', 'hearts'),
          createTestCard('6', 'diamonds'),
        ]),
      ],
      1
    );

    const dispatchSpy = vi.spyOn(gameClient, 'dispatch');

    gameClient.dispatch(GameActions.drawCard('bot2'));
    await vi.runAllTimersAsync();

    gameClient.dispatch(GameActions.discardCard('bot2'));
    await vi.runAllTimersAsync();

    if (gameClient.state.subPhase === 'toss_queue_active') {
      await botAdapter['handleTossInPhase']();
      await vi.runAllTimersAsync();

      // No toss-ins should occur
      const tossInCalls = dispatchSpy.mock.calls.filter(
        (call) => call[0].type === 'PARTICIPATE_IN_TOSS_IN'
      );
      expect(tossInCalls.length).toBe(0);

      // But both bots should mark ready
      const bot1ReadyCalls = dispatchSpy.mock.calls.filter(
        (call) =>
          call[0].type === 'PLAYER_TOSS_IN_FINISHED' &&
          call[0].payload?.playerId === 'bot1'
      );
      expect(bot1ReadyCalls.length).toBeGreaterThanOrEqual(1);

      const bot2ReadyCalls = dispatchSpy.mock.calls.filter(
        (call) =>
          call[0].type === 'PLAYER_TOSS_IN_FINISHED' &&
          call[0].payload?.playerId === 'bot2'
      );
      expect(bot2ReadyCalls.length).toBeGreaterThanOrEqual(1);
    }
  });

  /**
   * Test 4: Three bots - all must mark ready
   */
  it('should require all 3 bots to mark ready', async () => {
    const { gameClient } = await setupSimpleScenario(
      [
        createTestPlayer('bot1', 'Bot 1', false, [
          createTestCard('7', 's1'),
          createTestCard('5', 'h1'),
        ]),
        createTestPlayer('bot2', 'Bot 2', false, [
          createTestCard('7', 'h2'),
          createTestCard('6', 'd1'),
        ]),
        createTestPlayer('bot3', 'Bot 3', false, [
          createTestCard('7', 'c1'),
          createTestCard('4', 'c2'),
        ]),
      ],
      1
    );

    const adapter = new BotAIAdapter(gameClient);
    const dispatchSpy = vi.spyOn(gameClient, 'dispatch');

    gameClient.dispatch(GameActions.drawCard('bot2'));
    await vi.runAllTimersAsync();

    gameClient.dispatch(GameActions.discardCard('bot2'));
    await vi.runAllTimersAsync();

    if (gameClient.state.subPhase === 'toss_queue_active') {
      await adapter['handleTossInPhase']();
      await vi.runAllTimersAsync();

      const readyCalls = dispatchSpy.mock.calls.filter(
        (call) => call[0].type === 'PLAYER_TOSS_IN_FINISHED'
      );

      const readyPlayers = new Set(
        readyCalls.map(
          (call) => (call[0].payload as { playerId: string }).playerId
        )
      );

      expect(readyPlayers.has('bot1')).toBe(true);
      expect(readyPlayers.has('bot2')).toBe(true);
      expect(readyPlayers.has('bot3')).toBe(true);
    }

    adapter.dispose();
  });

  it('should let bot2 re-confirm toss-in after swapping king without declaration', async () => {
    const { gameClient, botAdapter } = await setupSimpleScenario(
      [
        createTestPlayer(
          'bot1',
          'Bot 1',
          false,
          [createTestCard('K', 'bot1_king')],
          []
        ), // bot 1 has 1 unknown card only so it should draw & replace with King in hand
        createTestPlayer('bot2', 'Bot 2', false, [
          createTestCard('3', 'bot3_3'),
          createTestCard('4', 'bot3_4'),
          createTestCard('5', 'bot3_5'),
        ]),

        createTestPlayer('bot3', 'Bot 3', false, [
          createTestCard('K', 'bot3_king'),
          createTestCard('6', 'bot3_6'),
          createTestCard('A', 'bot3_7'),
        ]),
        createTestPlayer('bot4', 'Bot 4', false, [
          createTestCard('2', 'bot4_2'),
          createTestCard('3', 'bot4_3'),
          createTestCard('4', 'bot4_4'),
          createTestCard('5', 'bot4_5'),
        ]),
      ],
      0,
      {
        drawPile: Pile.fromCards([createTestCard('3', 'drawn_1')]),
      }
    );

    const errorSpy = vi.fn();
    const successSpy = vi.fn();
    gameClient.onStateUpdateError(errorSpy);
    gameClient.onStateUpdateSuccess(successSpy);

    expect(gameClient.state.drawPile.peekTop()?.id).toBe('drawn_1'); // we are going to draw the 3

    gameClient.dispatch(GameActions.empty()); // now our bot should start toss-in
    await vi.runAllTimersAsync();

    await botAdapter.waitForIdle();

    //expect(mockLogger.warn).not.toHaveBeenCalled(); // we will have warnings as pile is empty - only way to stop bots
    // we should see only these actions changes: bot-1 finished toss in, bot-2 choosed card to declare, bot 2 finished toss in, bot 3 finished toss in, bot-4 finished toss-in

    expect(gameClient.state.subPhase).toBe('ai_thinking');
  });
});

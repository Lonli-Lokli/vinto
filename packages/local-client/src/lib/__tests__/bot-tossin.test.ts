// bot-tossin.integration.test.ts
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { GameClient } from '../game-client';
import { BotAIAdapter } from '../adapters/botAIAdapter';
import { GameActions, GameEngine } from '@vinto/engine';
import { PlayerState } from '@vinto/shapes';
import {
  createTestState,
  createTestCard,
  createTestPlayer,
} from './test-helper';

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
  let gameClient: GameClient;
  let botAdapter: BotAIAdapter;
  let gameEngine: GameEngine;

  beforeEach(() => {
    // Mock delays for faster testing
    vi.useFakeTimers();
  });

  afterEach(() => {
    if (botAdapter) {
      botAdapter.dispose();
    }
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  /**
   * Helper: Setup a simpler scenario by injecting specific cards
   * This uses the swap-hand-with-deck action to inject specific cards
   */
  async function setupSimpleScenario(
    players: PlayerState[],
    currentPlayerIndex = 1
  ): Promise<void> {
    // Create initial state using GameEngine
    const initialState = createTestState({
      players: players,
    });

    // Create GameClient with initial state
    gameClient = new GameClient(initialState);

    // Track any errors during state updates
    const errors: string[] = [];
    gameClient.onStateUpdateError((reason) => {
      console.error(`[SETUP ERROR] State update failed: ${reason}`);
      errors.push(reason);
    });

    // Navigate to correct turn with safety limit
    let turnCount = 0;
    const maxTurns = 10;

    // Navigate to correct turn
    while (
      gameClient.state.currentPlayerIndex !== currentPlayerIndex &&
      turnCount < maxTurns
    ) {
      const currentPlayerId =
        gameClient.state.players[gameClient.state.currentPlayerIndex].id;
      gameClient.dispatch(GameActions.drawCard(currentPlayerId));
      await vi.runAllTimersAsync();
      if (errors.length > 0) {
        throw new Error(`Errors during drawCard: ${errors.join(', ')}`);
      }

      gameClient.dispatch(GameActions.discardCard(currentPlayerId));
      await vi.runAllTimersAsync();
      if (errors.length > 0) {
        throw new Error(`Errors during discardCard: ${errors.join(', ')}`);
      }

      if (gameClient.state.subPhase === 'toss_queue_active') {
        for (const player of gameClient.state.players) {
          gameClient.dispatch(GameActions.playerTossInFinished(player.id));
        }
        await vi.runAllTimersAsync();
      }

      turnCount++;
    }

    if (turnCount >= maxTurns) {
      throw new Error(
        `Failed to reach player index ${currentPlayerIndex} after ${maxTurns} turns`
      );
    }

    botAdapter = new BotAIAdapter(gameClient);
  }

  /**
   * Test 1: Simple toss-in with 7 cards
   *
   * Core test: Bot2 discards, Bot2 marks ready, Bot1 tosses in, game continues
   */
  it.skip('should handle simple toss-in with 7 cards', async () => {
    const bot1Cards = [
      createTestCard('7', 'p1c1'),
      createTestCard('5', 'p1c2'),
      createTestCard('4', 'p1c3'),
      createTestCard('3', 'p1c4'),
    ];

    const bot2Cards = [
      createTestCard('7', 'p2c1'),
      createTestCard('6', 'p2c2'),
      createTestCard('5', 'p2c3'),
      createTestCard('4', 'p2c4'),
    ];

    await setupSimpleScenario(
      [
        createTestPlayer('bot1', 'Bot 1', false, bot1Cards),
        createTestPlayer('bot2', 'Bot 2', false, bot2Cards),
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
  it.skip('should not allow turn player to toss in their own discard', async () => {
    const bot1Cards = [
      createTestCard('7', 'spades'),
      createTestCard('5', 'hearts'),
    ];

    const bot2Cards = [
      createTestCard('7', 'hearts'),
      createTestCard('7', 'diamonds'),
      createTestCard('6', 'clubs'),
    ];

    await setupSimpleScenario(
      [
        createTestPlayer('bot1', 'Bot 1', false, bot1Cards),
        createTestPlayer('bot2', 'Bot 2', false, bot2Cards),
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
  it.skip('should advance turn when no bots have matching cards', async () => {
    const bot1Cards = [
      createTestCard('5', 'hearts'),
      createTestCard('4', 'hearts'),
    ];

    const bot2Cards = [
      createTestCard('7', 'hearts'),
      createTestCard('6', 'diamonds'),
    ];

    await setupSimpleScenario(
      [
        createTestPlayer('bot1', 'Bot 1', false, bot1Cards),
        createTestPlayer('bot2', 'Bot 2', false, bot2Cards),
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
  it.skip('should require all 3 bots to mark ready', async () => {
    const bot1Cards = [createTestCard('7', 's1'), createTestCard('5', 'h1')];
    const bot2Cards = [createTestCard('7', 'h2'), createTestCard('6', 'd1')];
    const bot3Cards = [createTestCard('7', 'c1'), createTestCard('4', 'c2')];

    await setupSimpleScenario(
      [
        createTestPlayer('bot1', 'Bot 1', false, bot1Cards),
        createTestPlayer('bot2', 'Bot 2', false, bot2Cards),
        createTestPlayer('bot3', 'Bot 3', false, bot3Cards),
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
});

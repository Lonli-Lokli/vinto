// coalition-final-round.test.ts
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { GameActions } from '@vinto/engine';
import { Card, PlayerState, Pile, Rank } from '@vinto/shapes';
import { GameClient } from '../game-client';
import { BotAIAdapter } from '../adapters/botAIAdapter';
import {
  createTestCard,
  createTestPlayer,
  setupSimpleScenario,
} from './test-helper';
import { mockLogger } from './setup-tests';

/**
 * Integration test: the human calls Vinto, the three bots form a coalition
 * and play the whole final round through the BotAIAdapter (planner-driven).
 *
 * The coalition wins iff the LOWEST coalition total is strictly below the
 * caller's total, so the bots must cooperate (Jack/Queen swaps, King
 * declarations, swap-and-declare, toss-ins) rather than play for themselves.
 */

let idCounter = 0;
const card = (rank: Rank): Card =>
  createTestCard(rank, `${rank}-${idCounter++}`);

const HUMAN = 'human';
const BOT1 = 'bot1';
const BOT2 = 'bot2';
const BOT3 = 'bot3';

function total(player: PlayerState): number {
  return player.cards.reduce((sum, c) => sum + c.value, 0);
}

interface FinalRoundSetup {
  human: Rank[];
  bot1: Rank[];
  bot2: Rank[];
  bot3: Rank[];
  /** Draw pile from the top: first card is drawn by the human before calling Vinto */
  drawPile: Rank[];
}

async function playFinalRound(setup: FinalRoundSetup): Promise<{
  gameClient: GameClient;
  botAdapter: BotAIAdapter;
  errors: string[];
}> {
  const human = createTestPlayer(HUMAN, 'Human', true, setup.human.map(card));
  const bots = [
    createTestPlayer(BOT1, 'Bot 1', false, setup.bot1.map(card)),
    createTestPlayer(BOT2, 'Bot 2', false, setup.bot2.map(card)),
    createTestPlayer(BOT3, 'Bot 3', false, setup.bot3.map(card)),
  ];
  // The coalition has seen the caller's whole hand (e.g. via earlier peeks)
  bots[0].opponentKnowledge = {
    [HUMAN]: {
      knownCards: Object.fromEntries(human.cards.map((c, i) => [i, c])),
    },
  };

  const { gameClient, botAdapter } = await setupSimpleScenario(
    [human, ...bots],
    0,
    {
      subPhase: 'idle',
      drawPile: Pile.fromCards(setup.drawPile.map(card)),
      discardPile: Pile.fromCards([card('4')]),
    },
  );

  const errors: string[] = [];
  gameClient.onStateUpdateError((reason) => errors.push(reason));

  // Human's last turn: draw, discard, then call Vinto during the toss-in window
  gameClient.dispatch(GameActions.drawCard(HUMAN));
  gameClient.dispatch(GameActions.discardCard(HUMAN));
  gameClient.dispatch(GameActions.callVinto(HUMAN));
  expect(gameClient.state.phase).toBe('final');
  expect(gameClient.state.vintoCallerId).toBe(HUMAN);

  // Let the coalition bots play the final round to completion
  for (let i = 0; i < 200 && gameClient.state.phase !== 'scoring'; i++) {
    await vi.runAllTimersAsync();
    await botAdapter.waitForIdle();
  }

  return { gameClient, botAdapter, errors };
}

describe('Coalition final round (bots vs human Vinto caller)', () => {
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

  it(
    'wins by using a drawn Jack to move a Joker into a teammate',
    { timeout: 30_000 },
    async () => {
      // Caller = 6. Bot1 [3,5]=8, Bot2 [Joker,9]=8, Bot3 [7,5]=12 → no member wins alone.
      // Bot1 draws a Jack: swapping the Joker into any 8-hand yields a total < 6.
      const { gameClient, botAdapter, errors } = await playFinalRound({
        human: ['2', '3', 'A'],
        bot1: ['3', '5'],
        bot2: ['Joker', '9'],
        bot3: ['7', '5'],
        drawPile: ['6', 'J', '4', '4', '4', '4', '4'],
      });

      expect(errors).toEqual([]);
      expect(gameClient.state.phase).toBe('scoring');

      const [human, ...bots] = gameClient.state.players;
      const coalitionMin = Math.min(...bots.map(total));
      expect(coalitionMin).toBeLessThan(total(human));

      // The caller's cards were never touched
      expect(human.cards.map((c) => c.rank)).toEqual(['2', '3', 'A']);

      botAdapter.dispose();
    },
  );

  it(
    "wins by declaring a King on a teammate's high card",
    { timeout: 30_000 },
    async () => {
      // Caller = 6. Bot1 [3,10]=13, Bot2 [8,9]=17, Bot3 [7,9]=16.
      // Bot1 draws a 4 (nothing useful), Bot2 draws a King → removes Bot1's 10 → 3 < 6.
      const { gameClient, botAdapter, errors } = await playFinalRound({
        human: ['2', '3', 'A'],
        bot1: ['3', '10'],
        bot2: ['8', '9'],
        bot3: ['7', '9'],
        drawPile: ['6', '4', 'K', '5', '5', '5', '5'],
      });

      expect(errors).toEqual([]);
      expect(gameClient.state.phase).toBe('scoring');

      const [human, ...bots] = gameClient.state.players;
      expect(Math.min(...bots.map(total))).toBeLessThan(total(human));

      botAdapter.dispose();
    },
  );

  it(
    'wins by shedding cards through toss-ins',
    { timeout: 30_000 },
    async () => {
      // Caller = 6. Bot1 [5,5]=10, Bot2 [7,9]=16, Bot3 [9,7]=16.
      // Bot1 draws a 5 and discards it → tosses both of its own 5s → total 0.
      const { gameClient, botAdapter, errors } = await playFinalRound({
        human: ['2', '3', 'A'],
        bot1: ['5', '5'],
        bot2: ['7', '9'],
        bot3: ['9', '7'],
        drawPile: ['6', '5', '8', '8', '8', '8', '8'],
      });

      expect(errors).toEqual([]);
      expect(gameClient.state.phase).toBe('scoring');

      const [human, ...bots] = gameClient.state.players;
      expect(Math.min(...bots.map(total))).toBeLessThan(total(human));

      botAdapter.dispose();
    },
  );

  it(
    'finishes the round cleanly when the coalition cannot win',
    { timeout: 30_000 },
    async () => {
      // Caller = -1 (two Jokers + A). No coalition line can beat that.
      const { gameClient, botAdapter, errors } = await playFinalRound({
        human: ['Joker', 'Joker', 'A'],
        bot1: ['9', '10'],
        bot2: ['8', '9'],
        bot3: ['7', '9'],
        drawPile: ['6', '4', '5', '6', '6', '6', '6'],
      });

      expect(errors).toEqual([]);
      expect(gameClient.state.phase).toBe('scoring');
      expect(gameClient.state.players[0].cards).toHaveLength(3);

      botAdapter.dispose();
    },
  );
});

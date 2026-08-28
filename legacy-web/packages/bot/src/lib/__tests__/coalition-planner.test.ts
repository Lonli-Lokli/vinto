/**
 * Coalition planner — final round cooperation against the Vinto caller.
 *
 * The coalition wins iff the lowest coalition hand is strictly below the
 * caller's total. These scenarios check that the planner finds the winning
 * line (Jack/Queen swaps, King removals, swap-and-declare, take-discard,
 * toss-ins) and re-plans from the real state.
 */

import { describe, it, expect } from 'vitest';
import { Card, GameState, PlayerState, Rank } from '@vinto/shapes';
import {
  buildCoalitionPlanInput,
  planCoalitionActionTargets,
  planCoalitionDrawnCard,
  planCoalitionTossIn,
  planCoalitionTurnStart,
  shouldCoalitionUseAction,
} from '../coalition-planner';
import {
  createTestCard,
  createTestPlayer,
  createTestState,
  toPile,
} from './test-helpers';

const HUMAN = 'human';
const BOT1 = 'bot1';
const BOT2 = 'bot2';
const BOT3 = 'bot3';

let cardCounter = 0;
const c = (rank: Rank): Card =>
  createTestCard(rank, `${rank}-${cardCounter++}`);

interface Scenario {
  human: Rank[];
  bot1: Rank[];
  bot2: Rank[];
  bot3: Rank[];
  /** index into [human, bot1, bot2, bot3] of the current turn */
  currentPlayerIndex: number;
  discardPile?: Rank[];
  pendingCard?: Card;
  /** which of the caller's cards the coalition knows (indices) — default all */
  knownHumanCards?: number[];
}

function buildState(s: Scenario): GameState {
  const human = createTestPlayer(HUMAN, 'Human', true, s.human.map(c));
  human.isVintoCaller = true;
  const bots = [
    createTestPlayer(BOT1, 'Bot 1', false, s.bot1.map(c)),
    createTestPlayer(BOT2, 'Bot 2', false, s.bot2.map(c)),
    createTestPlayer(BOT3, 'Bot 3', false, s.bot3.map(c)),
  ];
  for (const b of bots) {
    b.coalitionWith = bots.map((x) => x.id);
  }
  // Coalition knowledge about the caller lives on bot1 (pooled by the planner)
  const knownIdx = s.knownHumanCards ?? human.cards.map((_, i) => i);
  bots[0].opponentKnowledge = {
    [HUMAN]: {
      knownCards: Object.fromEntries(
        knownIdx.map((i) => [i, human.cards[i]] as const),
      ),
    },
  };

  const players: PlayerState[] = [human, ...bots];
  return createTestState({
    phase: 'final',
    subPhase: 'choosing',
    players,
    currentPlayerIndex: s.currentPlayerIndex,
    vintoCallerId: HUMAN,
    coalitionLeaderId: BOT1,
    discardPile: toPile((s.discardPile ?? ['4']).map(c)),
    pendingAction: s.pendingCard
      ? {
          card: s.pendingCard,
          playerId: players[s.currentPlayerIndex].id,
          actionPhase: 'choosing-action',
          from: 'drawing',
          targets: [],
        }
      : null,
  });
}

function score(cards: Card[]): number {
  return cards.reduce((sum, card) => sum + card.value, 0);
}

/** Apply a Jack/Queen swap plan to hands (test-side simulation) */
function applySwap(
  state: GameState,
  targets: { playerId: string; position: number }[],
): Record<string, Card[]> {
  const hands: Record<string, Card[]> = Object.fromEntries(
    state.players.map((p) => [p.id, [...p.cards]]),
  );
  const [a, b] = targets;
  const tmp = hands[a.playerId][a.position];
  hands[a.playerId][a.position] = hands[b.playerId][b.position];
  hands[b.playerId][b.position] = tmp;
  return hands;
}

describe('Coalition planner', () => {
  it('builds input with pooled caller knowledge, unseen distribution and remaining turn queue', () => {
    const state = buildState({
      human: ['2', '3', 'A'],
      bot1: ['3', '5'],
      bot2: ['Joker', '9'],
      bot3: ['7', '5'],
      currentPlayerIndex: 2, // bot2's turn
      knownHumanCards: [0, 1],
    });

    const input = buildCoalitionPlanInput(state, BOT2);
    expect(input).not.toBeNull();
    expect(input!.members.map((m) => m.id)).toEqual([BOT1, BOT2, BOT3]);
    expect(input!.turnQueue).toEqual([BOT3]); // bot3 still to play, then back to caller
    expect(input!.callerKnownValues.sort()).toEqual([2, 3]);
    expect(input!.callerUnknownCount).toBe(1);
    // Two 3s are visible (human known + bot1) → 2 left unseen
    expect(input!.unseenCounts['3']).toBe(2);
    // The Ace of the caller is unknown → still counted as unseen
    expect(input!.unseenCounts['A']).toBe(4);
    // Discard pile '4' consumed
    expect(input!.unseenCounts['4']).toBe(3);
  });

  it('returns null outside of a coalition final round', () => {
    const state = buildState({
      human: ['2'],
      bot1: ['3'],
      bot2: ['4'],
      bot3: ['5'],
      currentPlayerIndex: 1,
    });
    state.phase = 'playing';
    state.vintoCallerId = null;
    expect(buildCoalitionPlanInput(state, BOT1)).toBeNull();
  });

  it('uses a drawn Jack to move a Joker into a coalition member so it beats the caller', () => {
    // Caller = 6. Bot1 [3,5]=8, Bot2 [Joker,9]=8, Bot3 [7,5]=12 → nobody wins as-is.
    const drawn = c('J');
    const state = buildState({
      human: ['2', '3', 'A'],
      bot1: ['3', '5'],
      bot2: ['Joker', '9'],
      bot3: ['7', '5'],
      currentPlayerIndex: 2,
      pendingCard: drawn,
    });
    const input = buildCoalitionPlanInput(state, BOT2)!;

    const decision = planCoalitionDrawnCard(input, drawn);
    expect(decision.choice).toBe('use-action');
    if (decision.choice !== 'use-action') return;

    expect(decision.action.targets).toHaveLength(2);
    expect(decision.action.shouldSwap).toBe(true);
    const hands = applySwap(state, decision.action.targets);
    const coalitionMin = Math.min(
      score(hands[BOT1]),
      score(hands[BOT2]),
      score(hands[BOT3]),
    );
    expect(coalitionMin).toBeLessThan(6);
    // Never touches the caller
    expect(decision.action.targets.every((t) => t.playerId !== HUMAN)).toBe(
      true,
    );
  });

  it('selects Jack targets for a pending action that concentrate low cards', () => {
    const jack = c('J');
    const state = buildState({
      human: ['2', '3', 'A'],
      bot1: ['3', '5'],
      bot2: ['Joker', '9'],
      bot3: ['7', '5'],
      currentPlayerIndex: 2,
      pendingCard: jack,
    });
    state.subPhase = 'awaiting_action';
    const input = buildCoalitionPlanInput(state, BOT2)!;

    const decision = planCoalitionActionTargets(input, jack);
    expect(decision.targets).toHaveLength(2);
    expect(decision.shouldSwap).toBe(true);
    const hands = applySwap(state, decision.targets);
    expect(
      Math.min(score(hands[BOT1]), score(hands[BOT2]), score(hands[BOT3])),
    ).toBeLessThan(6);
  });

  it("uses a drawn King to remove the champion-to-be's high card", () => {
    // Caller = 6. Bot1 [3,10]=13 → removing the 10 leaves 3 (< 6).
    // Bot3 acts last (no lookahead), so this is the only winning line.
    const drawn = c('K');
    const state = buildState({
      human: ['2', '3', 'A'],
      bot1: ['3', '10'],
      bot2: ['7', '9'],
      bot3: ['8', '9'],
      currentPlayerIndex: 3,
      pendingCard: drawn,
    });
    const input = buildCoalitionPlanInput(state, BOT3)!;
    expect(input.turnQueue).toEqual([]);

    const decision = planCoalitionDrawnCard(input, drawn);
    expect(decision.choice).toBe('use-action');
    if (decision.choice !== 'use-action') return;
    expect(decision.action.targets).toEqual([{ playerId: BOT1, position: 1 }]);
    expect(decision.action.declaredRank).toBe('10');
  });

  it("sacrifices its own hand to trigger a toss-in that empties a teammate's hand", () => {
    // Bot2 holds a lone 9. Bot3 [7,9] draws a 4: swapping it over its 9 discards
    // a 9 → Bot2 tosses its 9 → Bot2 has no cards (total 0) → certain win.
    const drawn = c('4');
    const state = buildState({
      human: ['2', '3', 'A'],
      bot1: ['3', '10'],
      bot2: ['9'],
      bot3: ['7', '9'],
      currentPlayerIndex: 3,
      pendingCard: drawn,
    });
    const input = buildCoalitionPlanInput(state, BOT3)!;
    const decision = planCoalitionDrawnCard(input, drawn);
    expect(decision).toEqual({ choice: 'swap', position: 1 });
  });

  it('swaps a low drawn card over its own Jack and declares it to play the swap', () => {
    // Caller = 6. Bot2 [J,8]. Drawing a 2: swap over the J (declare J) → Bot2 [2,8],
    // then Jack swap gives some coalition member a total below 6.
    const drawn = c('2');
    const state = buildState({
      human: ['2', '3', 'A'],
      bot1: ['3', '9'],
      bot2: ['J', '8'],
      bot3: ['7', '9'],
      currentPlayerIndex: 2,
      pendingCard: drawn,
    });
    const input = buildCoalitionPlanInput(state, BOT2)!;

    const decision = planCoalitionDrawnCard(input, drawn);
    expect(decision).toEqual({
      choice: 'swap',
      position: 0,
      declaredRank: 'J',
    });
  });

  it('takes an unplayed Jack from the discard pile when it wins the round', () => {
    const state = buildState({
      human: ['2', '3', 'A'],
      bot1: ['3', '5'],
      bot2: ['Joker', '9'],
      bot3: ['7', '5'],
      currentPlayerIndex: 2,
      discardPile: ['J'],
    });
    state.subPhase = 'ai_thinking';
    const input = buildCoalitionPlanInput(state, BOT2)!;
    expect(input.discardTop?.rank).toBe('J');

    expect(planCoalitionTurnStart(input)).toEqual({ action: 'take-discard' });
  });

  it('draws when the discard top is useless or already played', () => {
    const state = buildState({
      human: ['2', '3', 'A'],
      bot1: ['3', '5'],
      bot2: ['Joker', '9'],
      bot3: ['7', '5'],
      currentPlayerIndex: 2,
      discardPile: ['7'],
    });
    const input = buildCoalitionPlanInput(state, BOT2)!;
    expect(planCoalitionTurnStart(input)).toEqual({ action: 'draw' });
  });

  it('prefers a swap that lowers its own total when nothing better exists', () => {
    // Bot3 acts last with [9,8]; draws a 2 → swap over the 9 (position 0)
    const drawn = c('2');
    const state = buildState({
      human: ['2', '3', 'A'],
      bot1: ['6', '5'],
      bot2: ['7', '10'],
      bot3: ['9', '8'],
      currentPlayerIndex: 3,
      pendingCard: drawn,
    });
    const input = buildCoalitionPlanInput(state, BOT3)!;
    const decision = planCoalitionDrawnCard(input, drawn);
    expect(decision).toEqual({ choice: 'swap', position: 0 });
  });

  it('tosses in every matching positive card and Kings, but never Jokers', () => {
    const state = buildState({
      human: ['2', '3', 'A'],
      bot1: ['5', 'K', '5', 'Joker'],
      bot2: ['9', '8'],
      bot3: ['7', '9'],
      currentPlayerIndex: 2,
    });
    const input = buildCoalitionPlanInput(state, BOT1)!;
    expect(planCoalitionTossIn(input, ['5'])).toEqual([0, 2]);
    expect(planCoalitionTossIn(input, ['K'])).toEqual([1]);
    expect(planCoalitionTossIn(input, ['Joker'])).toEqual([]);
    expect(planCoalitionTossIn(input, ['2'])).toEqual([]);
  });

  it('plays a queued toss-in Jack when a swap helps and skips peeks/aces', () => {
    const state = buildState({
      human: ['2', '3', 'A'],
      bot1: ['3', '5'],
      bot2: ['Joker', '9'],
      bot3: ['7', '5'],
      currentPlayerIndex: 2,
    });
    const input = buildCoalitionPlanInput(state, BOT2)!;
    expect(shouldCoalitionUseAction(input, c('J'))).toBe(true);
    expect(shouldCoalitionUseAction(input, c('7'))).toBe(false);
    expect(shouldCoalitionUseAction(input, c('A'))).toBe(false);
    expect(planCoalitionActionTargets(input, c('9')).targets).toEqual([]);
  });

  it('never targets the Vinto caller even when that would be the best swap', () => {
    // Caller has a Joker the coalition knows about; it must not be swapped out.
    const jack = c('J');
    const state = buildState({
      human: ['Joker', '2', '3'],
      bot1: ['3', '5'],
      bot2: ['4', '9'],
      bot3: ['7', '5'],
      currentPlayerIndex: 2,
      pendingCard: jack,
    });
    const input = buildCoalitionPlanInput(state, BOT2)!;
    const decision = planCoalitionActionTargets(input, jack);
    expect(decision.targets.every((t) => t.playerId !== HUMAN)).toBe(true);
  });

  it('decides quickly enough for the UI thread on a full 5-card table with a drawn King', () => {
    const drawn = c('K');
    const state = buildState({
      human: ['2', '3', 'A', '4', 'Joker'],
      bot1: ['3', '10', 'J', '6', '2'],
      bot2: ['8', '9', 'Q', '5', 'A'],
      bot3: ['7', '9', 'K', '4', 'J'],
      currentPlayerIndex: 1, // bot1 acts, bot2 & bot3 still to play
      pendingCard: drawn,
      knownHumanCards: [0, 1],
    });
    const input = buildCoalitionPlanInput(state, BOT1)!;

    const start = performance.now();
    const decision = planCoalitionDrawnCard(input, drawn);
    const elapsed = performance.now() - start;

    expect(decision).toBeDefined();
    expect(elapsed).toBeLessThan(1500);
  });
});

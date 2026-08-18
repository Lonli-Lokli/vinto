import { describe, expect, it } from 'vitest';
import { GameActions } from '../game-actions';
import { GameEngine } from '../game-engine';
import {
  createTestCard,
  createTestPlayer,
  createTestState,
  markPlayersReady,
  toPile,
  unsafeReduce,
} from './test-helpers';

/**
 * Replay and cross-implementation parity both rest on one property: the same state and
 * the same action always produce the same next state, ids and rngState included.
 */
describe('engine determinism', () => {
  it('produces identical results for two reductions of equal inputs', () => {
    const state = createTestState({
      phase: 'playing',
      subPhase: 'idle',
      drawPile: toPile([
        createTestCard('5', 'd1'),
        createTestCard('9', 'd2'),
        createTestCard('K', 'd3'),
      ]),
      rngState: 12345,
    });

    const action = GameActions.drawCard('p1');
    const first = GameEngine.reduce(state, action);
    const second = GameEngine.reduce(state, action);

    expect(first.success).toBe(true);
    expect(second.success).toBe(true);
    expect(JSON.stringify(second.state)).toBe(JSON.stringify(first.state));
    expect(second.state.rngState).toBe(first.state.rngState);
  });

  it('leaves rngState untouched when an action consumes no randomness', () => {
    const state = createTestState({
      phase: 'playing',
      subPhase: 'idle',
      drawPile: toPile([createTestCard('5', 'd1'), createTestCard('9', 'd2')]),
      rngState: 999,
    });

    const result = GameEngine.reduce(state, GameActions.drawCard('p1'));

    expect(result.success).toBe(true);
    expect(result.state.rngState).toBe(999);
  });

  it('advances rngState reproducibly when the draw pile is reshuffled', () => {
    // The reshuffle fires from advanceTurnAfterTossIn once the draw pile is down to one
    // card, which is the engine's only consumer of randomness. Driven through real turns,
    // mirroring Scenario 07 in scenarios.test.ts.
    const buildState = (rngState: number) =>
      createTestState({
        subPhase: 'idle',
        currentPlayerIndex: 0,
        turnNumber: 1,
        players: [
          createTestPlayer('p1', 'Player 1', true, [
            createTestCard('2', 'p1c1'),
          ]),
          createTestPlayer('p2', 'Player 2', false, [
            createTestCard('3', 'p2c1'),
          ]),
          createTestPlayer('p3', 'Player 3', false, [
            createTestCard('4', 'p3c1'),
          ]),
          createTestPlayer('p4', 'Player 4', false, [
            createTestCard('5', 'p4c1'),
          ]),
        ],
        drawPile: toPile([
          createTestCard('2', 'draw1'),
          createTestCard('3', 'draw2'),
          createTestCard('4', 'draw3'),
          createTestCard('5', 'draw4'),
        ]),
        discardPile: toPile([
          createTestCard('6', 'discard1'),
          createTestCard('6', 'discard2'),
          createTestCard('6', 'discard3'),
        ]),
        rngState,
      });

    const playToReshuffle = (rngState: number) => {
      let state = buildState(rngState);
      for (const playerId of ['p1', 'p2', 'p3']) {
        state = unsafeReduce(state, GameActions.drawCard(playerId));
        state = unsafeReduce(state, GameActions.discardCard(playerId));
        state = markPlayersReady(state, ['p1', 'p2', 'p3', 'p4']);
      }
      return state;
    };

    const runA = playToReshuffle(42);
    const runB = playToReshuffle(42);
    const different = playToReshuffle(4242);

    // The reshuffle happened.
    expect(runA.drawPile.length).toBe(6);
    expect(runA.discardPile.length).toBe(1);

    // Same seed in, same shuffled order out.
    expect(runB.drawPile.toArray()).toEqual(runA.drawPile.toArray());
    expect(runB.rngState).toBe(runA.rngState);

    // The generator actually moved, and a different seed gives a different order.
    expect(runA.rngState).not.toBe(42);
    expect(different.drawPile.toArray()).not.toEqual(runA.drawPile.toArray());
  });

  it('mints toss-in queued card ids without a clock', () => {
    const state = createTestState({
      phase: 'playing',
      subPhase: 'toss_queue_active',
      turnNumber: 7,
      activeTossIn: {
        ranks: ['9'],
        initiatorId: 'p2',
        originalPlayerIndex: 0,
        participants: ['p2'],
        queuedActions: [{ playerId: 'p2', rank: '9', position: 0 }],
        waitingForInput: false,
        playersReadyForNextTurn: ['p1', 'p3', 'p4'],
      },
    });

    const first = unsafeReduce(state, GameActions.playerTossInFinished('p2'));
    const second = unsafeReduce(state, GameActions.playerTossInFinished('p2'));

    expect(first.pendingAction?.card.id).toBe(second.pendingAction?.card.id);
    expect(first.pendingAction?.card.id).not.toMatch(/\d{13}/); // no epoch millis
    expect(first.pendingAction?.card.id).toContain('tossin_queued_7_p2_9');
  });
});

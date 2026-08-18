import { describe, expect, it } from 'vitest';
import {
  GAME_RECORDING_FORMAT_VERSION,
  GameRecording,
  UnsupportedRecordingVersionError,
  hashGameState,
} from '@vinto/shapes';
import { GameActions } from '../game-actions';
import { GameEngine } from '../game-engine';
import { formatDivergence, replayRecording } from '../replay';
import {
  createTestCard,
  createTestPlayer,
  createTestState,
  toPile,
} from './test-helpers';

/**
 * Builds a small real recording by driving the engine, so the fixtures under test are
 * produced the same way the generator produces them.
 */
async function buildRecording(
  options: { withHashes?: boolean } = {},
): Promise<GameRecording> {
  const initialState = createTestState({
    phase: 'playing',
    subPhase: 'idle',
    currentPlayerIndex: 0,
    players: [
      createTestPlayer('p1', 'P1', true, [createTestCard('2', 'p1c1')]),
      createTestPlayer('p2', 'P2', false, [createTestCard('3', 'p2c1')]),
      createTestPlayer('p3', 'P3', false, [createTestCard('4', 'p3c1')]),
      createTestPlayer('p4', 'P4', false, [createTestCard('5', 'p4c1')]),
    ],
    drawPile: toPile([
      createTestCard('7', 'd1'),
      createTestCard('8', 'd2'),
      createTestCard('9', 'd3'),
    ]),
    rngState: 123,
  });

  const actions = [GameActions.drawCard('p1'), GameActions.discardCard('p1')];

  let state = initialState;
  const recorded = [];
  for (const action of actions) {
    const result = GameEngine.reduce(state, action);
    if (!result.success) throw new Error(`setup failed: ${result.reason}`);
    state = result.state;
    recorded.push({
      action,
      ...(options.withHashes ? { stateHash: await hashGameState(state) } : {}),
    });
  }

  return {
    formatVersion: GAME_RECORDING_FORMAT_VERSION,
    meta: { recordedAt: '2026-01-01T00:00:00.000Z', producer: 'test' },
    settings: {
      humanPlayerName: 'You',
      difficulty: 'moderate',
      botVersion: 'v1',
      seed: 123,
    },
    initialState,
    actions: recorded,
    finalState: state,
  };
}

describe('replayRecording', () => {
  it('replays a recording faithfully', async () => {
    const recording = await buildRecording();
    const result = await replayRecording(recording);

    expect(result.ok).toBe(true);
    expect(result.divergence).toBeUndefined();
    expect(result.steps).toBe(recording.actions.length);
    expect(result.states).toHaveLength(recording.actions.length);
  });

  it('verifies per-action hashes when present', async () => {
    const recording = await buildRecording({ withHashes: true });

    expect((await replayRecording(recording)).ok).toBe(true);
  });

  it('survives a JSON round trip (Piles rehydrate)', async () => {
    const recording = await buildRecording({ withHashes: true });
    const roundTripped: GameRecording = JSON.parse(JSON.stringify(recording));

    const result = await replayRecording(roundTripped);

    expect(result.ok).toBe(true);
    // A plain array would have thrown on the first drawTop() without rehydration.
    expect(result.finalState.drawPile.length).toBeGreaterThan(0);
  });

  it('reports a hash mismatch with both hashes', async () => {
    const recording = await buildRecording({ withHashes: true });
    recording.actions[1].stateHash = 'f'.repeat(64);

    const result = await replayRecording(recording);

    expect(result.ok).toBe(false);
    expect(result.divergence?.reason).toBe('hash-mismatch');
    expect(result.divergence?.index).toBe(1);
    expect(result.divergence?.expectedHash).toBe('f'.repeat(64));
    expect(result.divergence?.actualHash).toMatch(/^[0-9a-f]{64}$/);
  });

  it('reports a rejected action and stops there', async () => {
    const recording = await buildRecording();
    // Discarding twice in a row is invalid.
    recording.actions.push({ action: GameActions.discardCard('p1') });

    const result = await replayRecording(recording);

    expect(result.ok).toBe(false);
    expect(result.divergence?.reason).toBe('action-rejected');
    expect(result.divergence?.index).toBe(2);
    expect(result.divergence?.detail).toContain('DISCARD_CARD');
    expect(result.steps).toBe(2);
  });

  it('reports a final-state mismatch', async () => {
    const recording = await buildRecording();
    recording.finalState = {
      ...recording.finalState,
      rngState: recording.finalState.rngState + 1,
    };

    const result = await replayRecording(recording);

    expect(result.ok).toBe(false);
    expect(result.divergence?.reason).toBe('final-state-mismatch');
    expect(result.divergence?.index).toBe(-1);
  });

  it('can skip final-state verification', async () => {
    const recording = await buildRecording();
    recording.finalState = {
      ...recording.finalState,
      rngState: recording.finalState.rngState + 1,
    };

    const result = await replayRecording(recording, {
      verifyFinalState: false,
    });

    expect(result.ok).toBe(true);
  });

  it('ignores client-written history, which the engine never reproduces', async () => {
    const recording = await buildRecording();
    // A client records history entries into finalState; a replayed state has none.
    // The canonical hash excludes them, so this must still match.
    recording.finalState = {
      ...recording.finalState,
      turnActions: [
        {
          playerId: 'p1',
          playerName: 'P1',
          description: 'P1 drew a card',
          timestamp: 0,
          turnNumber: 1,
          roundNumber: 1,
        },
      ],
    };

    expect((await replayRecording(recording)).ok).toBe(true);
  });

  it('rejects an unknown format version', async () => {
    const recording = await buildRecording();
    recording.formatVersion = 99;

    await expect(replayRecording(recording)).rejects.toThrow(
      UnsupportedRecordingVersionError,
    );
  });
});

describe('formatDivergence', () => {
  it('names the action, reason and both hashes', async () => {
    const recording = await buildRecording({ withHashes: true });
    recording.actions[0].stateHash = 'a'.repeat(64);

    const result = await replayRecording(recording);
    const report = formatDivergence(result.divergence!);

    expect(report).toContain('index 0');
    expect(report).toContain('hash-mismatch');
    expect(report).toContain('DRAW_CARD');
    expect(report).toContain('a'.repeat(64));
  });
});

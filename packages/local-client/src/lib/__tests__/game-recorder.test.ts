import { describe, expect, it, vi } from 'vitest';
import {
  GAME_RECORDING_FORMAT_VERSION,
  GameRecording,
  UnsupportedRecordingVersionError,
  assertRecordingVersion,
} from '@vinto/shapes';
import { GameClient } from '../game-client';
import { GameActions } from '@vinto/engine';
import { fourPlayerGame } from '../initializeGame';
import { RecordingAutoSave, RecordingStorage } from '../recording-auto-save';

function createStorageStub(): RecordingStorage & { data: Map<string, string> } {
  const data = new Map<string, string>();
  return {
    data,
    getItem: (key) => data.get(key) ?? null,
    setItem: (key, value) => void data.set(key, value),
    removeItem: (key) => void data.delete(key),
  };
}

/**
 * A fresh seeded game taken through the real setup sequence: the human must peek at
 * two of their own cards before FINISH_SETUP is legal.
 */
function startedGame(seed = 4242, autoSave?: RecordingAutoSave) {
  const client = new GameClient(fourPlayerGame('You', 'moderate', 'v1', seed), {
    autoSave,
  });
  const humanId = client.state.players[0].id;

  client.dispatch(GameActions.peekSetupCard(humanId, 0));
  client.dispatch(GameActions.peekSetupCard(humanId, 1));
  client.dispatch(GameActions.finishSetup(humanId));

  return client;
}

describe('assertRecordingVersion', () => {
  it('accepts the current version', () => {
    expect(() =>
      assertRecordingVersion({ formatVersion: GAME_RECORDING_FORMAT_VERSION }),
    ).not.toThrow();
  });

  it.each([99, 0, undefined, null])('rejects version %s', (formatVersion) => {
    expect(() =>
      assertRecordingVersion({
        formatVersion,
      } as unknown as GameRecording),
    ).toThrow(UnsupportedRecordingVersionError);
  });

  it('names the unsupported version in the message', () => {
    expect(() =>
      assertRecordingVersion({ formatVersion: 99 } as GameRecording),
    ).toThrow(/99/);
  });
});

describe('GameClient recording', () => {
  it('produces a well-formed v1 recording', () => {
    const client = startedGame();
    const recording: GameRecording = JSON.parse(client.exportRecording());

    expect(recording.formatVersion).toBe(GAME_RECORDING_FORMAT_VERSION);
    expect(recording.settings.seed).toBe(4242);
    expect(recording.settings.difficulty).toBe('moderate');
    expect(recording.initialState.gameId).toBe('vinto-4242');
    expect(recording.meta.producer).toBe('vinto-ts');
    expect(Array.isArray(recording.actions)).toBe(true);
  });

  it('records accepted actions in dispatch order', () => {
    const client = startedGame();
    const before = client.recordedActionCount;

    client.dispatch(GameActions.drawCard(client.state.players[0].id));

    expect(client.recordedActionCount).toBe(before + 1);

    const recording: GameRecording = JSON.parse(client.exportRecording());
    expect(recording.actions.at(-1)?.action.type).toBe('DRAW_CARD');
  });

  it('does not record a rejected action', () => {
    const client = startedGame();
    const before = client.recordedActionCount;
    const stateBefore = client.state;

    // Swapping outside the 'choosing' sub-phase is invalid.
    client.dispatch(GameActions.swapCard(client.state.players[0].id, 0));

    expect(client.recordedActionCount).toBe(before);
    expect(client.state).toBe(stateBefore);
  });

  it('keeps the initial state fixed as actions accumulate', () => {
    const client = startedGame();
    const initial = JSON.parse(client.exportRecording()).initialState;

    client.dispatch(GameActions.drawCard(client.state.players[0].id));
    const later = JSON.parse(client.exportRecording());

    expect(later.initialState).toEqual(initial);
    expect(later.finalState).not.toEqual(initial);
  });

  it('exports states free of wall-clock and uuid values', () => {
    const client = startedGame();
    client.dispatch(GameActions.drawCard(client.state.players[0].id));

    const recording: GameRecording = JSON.parse(client.exportRecording());
    const states = JSON.stringify([
      recording.initialState,
      recording.finalState,
    ]);

    expect(states).not.toMatch(/\d{13}/);
    expect(states).not.toMatch(
      /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i,
    );
  });
});

describe('RecordingAutoSave', () => {
  it('coalesces bursts into a single write', () => {
    vi.useFakeTimers();
    const storage = createStorageStub();
    const autoSave = new RecordingAutoSave({ storage, debounceMs: 100 });
    const serialise = vi.fn(() => '{"formatVersion":1}');

    autoSave.schedule(serialise);
    autoSave.schedule(serialise);
    autoSave.schedule(serialise);
    expect(serialise).not.toHaveBeenCalled();

    vi.advanceTimersByTime(100);

    expect(serialise).toHaveBeenCalledTimes(1);
    expect(storage.data.get('vinto:recording:v1')).toBe('{"formatVersion":1}');
    vi.useRealTimers();
  });

  it('survives a storage that throws', () => {
    const autoSave = new RecordingAutoSave({
      storage: {
        getItem: () => null,
        setItem: () => {
          throw new Error('QuotaExceededError');
        },
        removeItem: () => undefined,
      },
      debounceMs: 0,
    });

    autoSave.schedule(() => 'x');
    expect(() => autoSave.flush()).not.toThrow();
  });

  it('clears the previous game when a new client starts', () => {
    const storage = createStorageStub();
    storage.setItem('vinto:recording:v1', 'stale');

    const autoSave = new RecordingAutoSave({ storage });
    new GameClient(fourPlayerGame('You', 'moderate', 'v1', 1), { autoSave });

    expect(storage.data.has('vinto:recording:v1')).toBe(false);
  });

  it('persists a recording that replays back to the same actions', () => {
    vi.useFakeTimers();
    const storage = createStorageStub();
    const autoSave = new RecordingAutoSave({ storage, debounceMs: 10 });
    const client = startedGame(777, autoSave);

    expect(client.recordedActionCount).toBe(3);
    vi.advanceTimersByTime(10);

    const saved: GameRecording = JSON.parse(autoSave.load() as string);
    expect(saved.formatVersion).toBe(GAME_RECORDING_FORMAT_VERSION);
    expect(saved.actions.map((entry) => entry.action.type)).toEqual([
      'PEEK_SETUP_CARD',
      'PEEK_SETUP_CARD',
      'FINISH_SETUP',
    ]);
    vi.useRealTimers();
  });
});

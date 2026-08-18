import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { GameRecording } from '@vinto/shapes';
import { formatDivergence, replayRecording } from '../replay';

/**
 * The cross-implementation parity gate.
 *
 * Every committed recording must replay through this engine with identical per-action
 * hashes. The Kotlin engine runs the same corpus; a divergence in either implementation
 * fails here first, naming the action that disagreed.
 */

const FIXTURES_DIR = join(__dirname, '../../../../../fixtures/recordings');

function fixtureFiles(): string[] {
  if (!existsSync(FIXTURES_DIR)) return [];
  return readdirSync(FIXTURES_DIR)
    .filter((name) => name.endsWith('.json'))
    .sort();
}

const files = fixtureFiles();

describe('recording fixtures replay', () => {
  if (!existsSync(FIXTURES_DIR)) {
    // The corpus is committed in task 3.5, which is deliberately blocked until
    // botVersion leaves GameState (removing it changes every canonical hash). Skipping
    // is honest here; once the directory exists the assertions below take over.
    it.skip('corpus not committed yet (see add-game-recording-replay task 3.5)', () => {
      /* intentionally empty */
    });
    return;
  }

  it('the committed corpus is not empty', () => {
    expect(files.length).toBeGreaterThan(0);
  });

  it.each(files)('%s replays without divergence', async (name) => {
    const recording: GameRecording = JSON.parse(
      readFileSync(join(FIXTURES_DIR, name), 'utf8'),
    );

    const result = await replayRecording(recording);

    if (!result.divergence) {
      expect(result.ok).toBe(true);
      return;
    }

    // Surface the divergence report rather than a bare boolean mismatch.
    throw new Error(
      `${name} diverged:\n${formatDivergence(result.divergence)}`,
    );
  });

  it('every recorded action carries a state hash', () => {
    const missing = files.filter((name) => {
      const recording: GameRecording = JSON.parse(
        readFileSync(join(FIXTURES_DIR, name), 'utf8'),
      );
      return recording.actions.some((entry) => !entry.stateHash);
    });

    // Without per-action hashes a fixture only proves the engine did not crash, not
    // that it agreed step by step -- which is the whole point of the gate.
    expect(missing).toEqual([]);
  });
});

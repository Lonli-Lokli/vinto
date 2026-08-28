import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { beforeAll, describe, expect, it } from 'vitest';
import { GameRecording, GameState } from '@vinto/shapes';
import { ReplayResult, formatDivergence, replayRecording } from '../replay';

/**
 * The cross-implementation parity gate.
 *
 * Every committed recording must replay through this engine with identical per-action
 * hashes. The Kotlin engine runs the same corpus; a divergence in either implementation
 * fails here first, naming the action that disagreed.
 *
 * The suite also asserts what the corpus *contains*. A corpus that replays perfectly but
 * never reaches scoring, never reshuffles and never plays a coalition round would look
 * like a gate while testing only the easy half of the engine.
 */

const FIXTURES_DIR = join(__dirname, '../../../../../../fixtures/recordings');

function fixtureFiles(): string[] {
  if (!existsSync(FIXTURES_DIR)) return [];
  return readdirSync(FIXTURES_DIR)
    .filter((name) => name.endsWith('.json'))
    .sort();
}

const files = fixtureFiles();

/** The draw pile only ever grows when the discard pile is reshuffled into it. */
function hasMidGameReshuffle(states: GameState[]): boolean {
  for (let i = 1; i < states.length; i++) {
    if (states[i].drawPile.length > states[i - 1].drawPile.length) return true;
  }
  return false;
}

describe('recording fixtures replay', () => {
  if (!existsSync(FIXTURES_DIR) || files.length === 0) {
    // The corpus lands in add-game-recording-replay task 3.5. Skipping is honest while
    // it is absent; once files exist every assertion below applies.
    it.skip('corpus not committed yet (see add-game-recording-replay task 3.5)', () => {
      /* intentionally empty */
    });
    return;
  }

  const recordings = new Map<string, GameRecording>();
  const results = new Map<string, ReplayResult>();

  beforeAll(async () => {
    // Replay once and share the results, so the coverage assertions below cost nothing
    // beyond the gate itself.
    for (const name of files) {
      const recording: GameRecording = JSON.parse(
        readFileSync(join(FIXTURES_DIR, name), 'utf8'),
      );
      recordings.set(name, recording);
      results.set(name, await replayRecording(recording));
    }
  }, 600_000);

  it.each(files)('%s replays without divergence', (name) => {
    const result = results.get(name)!;

    if (result.divergence) {
      // Surface the report rather than a bare boolean mismatch.
      throw new Error(
        `${name} diverged:\n${formatDivergence(result.divergence)}`,
      );
    }

    expect(result.ok).toBe(true);
  });

  it('every recorded action carries a state hash', () => {
    const missing = files.filter((name) =>
      recordings.get(name)!.actions.some((entry) => !entry.stateHash),
    );

    // Without per-action hashes a fixture only proves the engine did not crash, not
    // that it agreed step by step -- which is the whole point of the gate.
    expect(missing).toEqual([]);
  });

  describe('corpus coverage', () => {
    it('has at least 50 recordings', () => {
      expect(files.length).toBeGreaterThanOrEqual(50);
    });

    it('contains completed games that reach scoring', () => {
      const scored = files.filter(
        (name) => recordings.get(name)!.finalState.phase === 'scoring',
      );

      expect(scored.length).toBeGreaterThan(0);
    });

    it('contains a coalition final round', () => {
      const coalition = files.filter((name) => {
        const final = recordings.get(name)!.finalState;
        return final.vintoCallerId !== null && final.coalitionLeaderId !== null;
      });

      expect(coalition.length).toBeGreaterThan(0);
    });

    it('contains a mid-game draw-pile reshuffle', () => {
      // The reshuffle is the engine's only consumer of rngState, so a corpus without one
      // never exercises seeded randomness at all.
      const reshuffled = files.filter((name) =>
        hasMidGameReshuffle(results.get(name)!.states),
      );

      expect(reshuffled.length).toBeGreaterThan(0);
    });

    it('exercises every action card', () => {
      const seen = new Set<string>();
      for (const name of files) {
        for (const entry of recordings.get(name)!.actions) {
          seen.add(entry.action.type);
        }
      }

      for (const required of [
        'DRAW_CARD',
        'DISCARD_CARD',
        'USE_CARD_ACTION',
        'SELECT_ACTION_TARGET',
        'PARTICIPATE_IN_TOSS_IN',
        'DECLARE_KING_ACTION',
        'CALL_VINTO',
      ]) {
        expect(Array.from(seen)).toContain(required);
      }
    });
  });
});

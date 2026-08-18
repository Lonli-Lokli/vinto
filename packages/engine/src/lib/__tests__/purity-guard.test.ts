import { readdirSync, readFileSync } from 'node:fs';
import { join, relative } from 'node:path';
import { describe, expect, it } from 'vitest';

/**
 * The engine must be a pure function of (state, action). Anything ambient — a clock, a
 * random source, a uuid — makes a game unreplayable and breaks cross-implementation
 * parity with the Kotlin port. This guard fails the build if any creeps back in.
 */

const ENGINE_SRC = join(__dirname, '..');

const FORBIDDEN: { name: string; pattern: RegExp }[] = [
  { name: 'Date.*', pattern: /\bDate\s*\./ },
  { name: 'new Date', pattern: /\bnew\s+Date\b/ },
  { name: 'Math.random', pattern: /\bMath\s*\.\s*random\b/ },
  { name: 'crypto.*', pattern: /\bcrypto\s*\./ },
  { name: 'performance.now', pattern: /\bperformance\s*\.\s*now\b/ },
  { name: "import from 'uuid'", pattern: /from\s+['"]uuid['"]/ },
  { name: 'uuid call', pattern: /\buuidv4\s*\(/ },
];

function engineSourceFiles(): string[] {
  return readdirSync(ENGINE_SRC, { recursive: true, encoding: 'utf8' })
    .map((entry) => entry.replace(/\\/g, '/'))
    .filter((entry) => entry.endsWith('.ts'))
    .filter((entry) => !entry.includes('__tests__'))
    .map((entry) => join(ENGINE_SRC, entry));
}

describe('engine purity guard', () => {
  const files = engineSourceFiles();

  it('finds engine sources to scan', () => {
    // Guards the guard: a bad glob would otherwise make every assertion below vacuous.
    expect(files.length).toBeGreaterThan(10);
  });

  it.each(FORBIDDEN)(
    'has no reference to $name in the reducer path',
    ({ pattern }) => {
      const offenders = files
        .filter((file) => pattern.test(readFileSync(file, 'utf8')))
        .map((file) => relative(ENGINE_SRC, file).replace(/\\/g, '/'));

      expect(offenders).toEqual([]);
    },
  );

  it('does not reach for ambient randomness instead of GameState.rngState', () => {
    const usesRngState = files.some((file) =>
      /\brngState\b/.test(readFileSync(file, 'utf8')),
    );

    // The reshuffle in toss-in-utils is the engine's only randomness consumer; if this
    // stops holding, the seeded generator has been bypassed somewhere.
    expect(usesRngState).toBe(true);
  });
});

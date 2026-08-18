import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

import { Prng } from '../prng';

interface Vectors {
  algorithm: string;
  increment: number;
  sequences: { seed: number; values: number[]; finalState: number }[];
  boundedSequences: { seed: number; bound: number; values: number[] }[];
  shuffles: {
    seed: number;
    deckSize: number;
    order: number[];
    finalState: number;
  }[];
}

// The committed vectors are the cross-language contract: the Kotlin port reads this same
// file. Loading it from disk (rather than inlining values) means a drift in either
// direction fails here first.
const VECTORS_PATH = join(
  __dirname,
  '../../../../../fixtures/prng/vectors.json',
);
const vectors: Vectors = JSON.parse(readFileSync(VECTORS_PATH, 'utf8'));

const UINT32_MAX = 0xffffffff;

describe('Prng', () => {
  it('reads the committed vector file', () => {
    expect(vectors.algorithm).toBe('mulberry32');
    expect(vectors.sequences.length).toBeGreaterThan(0);
  });

  describe('next', () => {
    it.each(vectors.sequences)(
      'reproduces the published sequence for seed $seed',
      ({ seed, values, finalState }) => {
        let state = Prng.seed(seed);
        const produced: number[] = [];

        for (let i = 0; i < values.length; i++) {
          const result = Prng.next(state);
          produced.push(result.value);
          state = result.state;
        }

        expect(produced).toEqual(values);
        expect(state).toBe(finalState);
      },
    );

    it('produces uint32 values and states', () => {
      let state = Prng.seed(987654321);

      for (let i = 0; i < 1000; i++) {
        const result = Prng.next(state);
        expect(Number.isInteger(result.value)).toBe(true);
        expect(result.value).toBeGreaterThanOrEqual(0);
        expect(result.value).toBeLessThanOrEqual(UINT32_MAX);
        expect(Number.isInteger(result.state)).toBe(true);
        expect(result.state).toBeGreaterThanOrEqual(0);
        expect(result.state).toBeLessThanOrEqual(UINT32_MAX);
        state = result.state;
      }
    });

    it('is a pure function of the state', () => {
      const state = Prng.seed(2026);
      expect(Prng.next(state)).toEqual(Prng.next(state));
    });
  });

  describe('nextInt', () => {
    it.each(vectors.boundedSequences)(
      'reproduces the published bounded sequence for seed $seed bound $bound',
      ({ seed, bound, values }) => {
        let state = Prng.seed(seed);
        const produced: number[] = [];

        for (let i = 0; i < values.length; i++) {
          const result = Prng.nextInt(state, bound);
          produced.push(result.value);
          state = result.state;
        }

        expect(produced).toEqual(values);
      },
    );

    it('stays within the bound', () => {
      let state = Prng.seed(5);

      for (let i = 0; i < 500; i++) {
        const result = Prng.nextInt(state, 54);
        expect(result.value).toBeGreaterThanOrEqual(0);
        expect(result.value).toBeLessThan(54);
        state = result.state;
      }
    });

    it('always returns 0 for bound 1', () => {
      expect(Prng.nextInt(Prng.seed(99), 1).value).toBe(0);
    });

    it.each([0, -1, 2.5, Number.NaN])(
      'rejects the invalid bound %s',
      (bound) => {
        expect(() => Prng.nextInt(Prng.seed(1), bound)).toThrow(RangeError);
      },
    );
  });

  describe('shuffle', () => {
    it.each(vectors.shuffles)(
      'reproduces the published 54-card shuffle for seed $seed',
      ({ seed, deckSize, order, finalState }) => {
        const deck = Array.from({ length: deckSize }, (_, i) => i);
        const result = Prng.shuffle(deck, Prng.seed(seed));

        expect(result.items).toEqual(order);
        expect(result.state).toBe(finalState);
      },
    );

    it('is a permutation and does not mutate the input', () => {
      const deck = Array.from({ length: 54 }, (_, i) => i);
      const result = Prng.shuffle(deck, Prng.seed(31337));

      expect(deck).toEqual(Array.from({ length: 54 }, (_, i) => i));
      expect([...result.items].sort((a, b) => a - b)).toEqual(deck);
    });

    it('gives different orders for different seeds', () => {
      const deck = Array.from({ length: 54 }, (_, i) => i);
      const a = Prng.shuffle(deck, Prng.seed(1));
      const b = Prng.shuffle(deck, Prng.seed(2));

      expect(a.items).not.toEqual(b.items);
    });

    it('handles empty and single-element inputs', () => {
      const state = Prng.seed(7);
      expect(Prng.shuffle([], state)).toEqual({ items: [], state });
      expect(Prng.shuffle(['a'], state)).toEqual({ items: ['a'], state });
    });
  });

  describe('seed', () => {
    it('normalises negative and out-of-range integers to uint32', () => {
      expect(Prng.seed(-1)).toBe(UINT32_MAX);
      expect(Prng.seed(0)).toBe(0);
      expect(Prng.seed(UINT32_MAX)).toBe(UINT32_MAX);
    });

    it('rejects non-integers', () => {
      expect(() => Prng.seed(1.5)).toThrow(RangeError);
    });
  });
});

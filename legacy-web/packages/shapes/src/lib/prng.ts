/**
 * mulberry32 — the seeded generator that makes the engine reproducible.
 *
 * The generator state is an unsigned 32-bit integer carried in `GameState.rngState`.
 * Every operation here is 32-bit integer arithmetic with no floating-point step, so a
 * Kotlin port produces byte-identical output. `fixtures/prng/vectors.json` pins the
 * output and is read by both implementations' tests.
 *
 * Callers MUST carry the returned `state` forward; reusing a stale state repeats values.
 */

const INCREMENT = 0x6d2b79f5;

export interface PrngValue {
  /** Uniformly distributed unsigned 32-bit integer. */
  readonly value: number;
  /** Advanced generator state. */
  readonly state: number;
}

export interface PrngShuffle<T> {
  readonly items: T[];
  readonly state: number;
}

export const Prng = {
  /** Normalise any integer seed to a valid uint32 generator state. */
  seed(seed: number): number {
    if (!Number.isInteger(seed)) {
      throw new RangeError(`Prng.seed requires an integer, got ${seed}`);
    }
    return seed >>> 0;
  },

  next(state: number): PrngValue {
    const advanced = (state + INCREMENT) >>> 0;
    let t = advanced;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return { value: (t ^ (t >>> 14)) >>> 0, state: advanced };
  },

  /**
   * Uniform-ish integer in `[0, bound)`. Uses modulo rather than rejection sampling:
   * the bias is negligible for the bounds this game uses (<= 54) and modulo is trivially
   * identical across languages, which rejection sampling is not.
   */
  nextInt(state: number, bound: number): PrngValue {
    if (!Number.isInteger(bound) || bound <= 0) {
      throw new RangeError(
        `Prng.nextInt requires a positive integer bound, got ${bound}`,
      );
    }
    const next = Prng.next(state);
    return { value: next.value % bound, state: next.state };
  },

  /** Fisher-Yates, descending — the loop order is part of the cross-language contract. */
  shuffle<T>(items: readonly T[], state: number): PrngShuffle<T> {
    const shuffled = [...items];
    let current = state;

    for (let i = shuffled.length - 1; i > 0; i--) {
      const next = Prng.nextInt(current, i + 1);
      current = next.state;
      const j = next.value;
      [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
    }

    return { items: shuffled, state: current };
  },
};

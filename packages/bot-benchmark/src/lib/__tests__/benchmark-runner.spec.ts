import { describe, it, expect, beforeEach } from 'vitest';
import { BenchmarkRunner } from '../benchmark-runner';
import type { BenchmarkConfig } from '../benchmark-types';

describe('BenchmarkRunner', () => {
  let runner: BenchmarkRunner;

  beforeEach(() => {
    runner = new BenchmarkRunner();
  });

  it('should run a simple benchmark', async () => {
    const config: BenchmarkConfig = {
      name: 'Test Benchmark',
      description: 'Testing benchmark runner',
      participants: [
        { id: 'bot1', botVersion: 'v1', difficulty: 'easy' },
        { id: 'bot2', botVersion: 'v1', difficulty: 'moderate' },
      ],
      games: 10,
      seed: 12345,
    };

    const result = await runner.run(config);

    expect(result).toBeDefined();
    expect(result.summary.totalGames).toBe(10);
    expect(result.games.length).toBe(10);
    expect(result.summary.winRates.size).toBe(2);
  });

  it('should compute win rates correctly', async () => {
    const config: BenchmarkConfig = {
      name: 'Win Rate Test',
      description: 'Test win rate calculation',
      participants: [
        { id: 'bot1', botVersion: 'v1', difficulty: 'hard' },
        { id: 'bot2', botVersion: 'v1', difficulty: 'easy' },
      ],
      games: 20,
      seed: 99999,
    };

    const result = await runner.run(config);

    const bot1WinRate = result.summary.winRates.get('bot1');
    const bot2WinRate = result.summary.winRates.get('bot2');

    expect(bot1WinRate).toBeDefined();
    expect(bot2WinRate).toBeDefined();

    // Win rates should sum to approximately 1 (within rounding error)
    expect(bot1WinRate! + bot2WinRate!).toBeCloseTo(1, 1);
  });

  it('should compute confidence intervals', async () => {
    const config: BenchmarkConfig = {
      name: 'CI Test',
      description: 'Test confidence interval calculation',
      participants: [
        { id: 'bot1', botVersion: 'v1', difficulty: 'moderate' },
      ],
      games: 50,
    };

    const result = await runner.run(config);

    const ci = result.summary.confidenceIntervals.get('bot1');
    expect(ci).toBeDefined();

    // CI should be [lower, upper] where 0 <= lower <= upper <= 1
    expect(ci![0]).toBeGreaterThanOrEqual(0);
    expect(ci![1]).toBeLessThanOrEqual(1);
    expect(ci![0]).toBeLessThanOrEqual(ci![1]);
  });

  it('should respect seed for reproducibility', async () => {
    const config: BenchmarkConfig = {
      name: 'Seed Test',
      description: 'Test reproducibility with seed',
      participants: [
        { id: 'bot1', botVersion: 'v1', difficulty: 'hard' },
        { id: 'bot2', botVersion: 'v1', difficulty: 'hard' },
      ],
      games: 10,
      seed: 42,
    };

    const result1 = await runner.run(config);
    const result2 = await runner.run(config);

    // With same seed, results should be identical
    expect(result1.summary.winRates.get('bot1')).toBe(
      result2.summary.winRates.get('bot1')
    );
  });

  it('should compute average statistics', async () => {
    const config: BenchmarkConfig = {
      name: 'Stats Test',
      description: 'Test average statistics',
      participants: [
        { id: 'bot1', botVersion: 'v1', difficulty: 'easy' },
      ],
      games: 10,
    };

    const result = await runner.run(config);

    expect(result.summary.avgScores.get('bot1')).toBeDefined();
    expect(result.summary.avgDecisionTime.get('bot1')).toBeDefined();
    expect(result.summary.avgGameLength).toBeGreaterThan(0);
  });
});

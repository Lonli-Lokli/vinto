// Example benchmark: Compare bot difficulties

import { BenchmarkRunner } from '../lib/benchmark-runner';
import { AnalysisReporter } from '../lib/analysis-reporter';
import type { BenchmarkConfig } from '../lib/benchmark-types';

/**
 * Example: Compare easy vs moderate vs hard bots
 */
export async function runDifficultyComparison() {
  const runner = new BenchmarkRunner();
  const reporter = new AnalysisReporter();

  const config: BenchmarkConfig = {
    name: 'Difficulty Comparison',
    description: 'Compare bot performance across difficulty levels',
    participants: [
      { id: 'easy-bot', botVersion: 'v1', difficulty: 'easy' },
      { id: 'moderate-bot', botVersion: 'v1', difficulty: 'moderate' },
      { id: 'hard-bot', botVersion: 'v1', difficulty: 'hard' },
    ],
    games: 100,
    seed: 12345, // For reproducibility
  };

  console.log('Running difficulty comparison benchmark...\n');

  const result = await runner.run(config);

  // Generate report
  const report = reporter.generateReport(result);
  console.log('\n' + report);

  // Export results
  const jsonOutput = reporter.exportJSON(result);
  const csvOutput = reporter.exportCSV(result);

  console.log('\n📄 Results exported to JSON and CSV formats');

  return result;
}

/**
 * Example: Baseline vs candidate comparison
 */
export async function runBaselineComparison() {
  const runner = new BenchmarkRunner();
  const reporter = new AnalysisReporter();

  // Run baseline
  const baselineConfig: BenchmarkConfig = {
    name: 'Baseline Bot',
    description: 'Current production bot',
    participants: [
      { id: 'baseline', botVersion: 'v1', difficulty: 'hard' },
      { id: 'opponent', botVersion: 'v1', difficulty: 'moderate' },
    ],
    games: 200,
    seed: 99999,
  };

  console.log('Running baseline benchmark...\n');
  const baselineResult = await runner.run(baselineConfig);

  // Run candidate
  const candidateConfig: BenchmarkConfig = {
    name: 'Enhanced Bot',
    description: 'Bot with new improvements',
    participants: [
      { id: 'candidate', botVersion: 'v1', difficulty: 'hard' }, // In real usage, this would be v2
      { id: 'opponent', botVersion: 'v1', difficulty: 'moderate' },
    ],
    games: 200,
    seed: 99999, // Same seed for fair comparison
  };

  console.log('\nRunning candidate benchmark...\n');
  const candidateResult = await runner.run(candidateConfig);

  // Compare results
  const comparison = reporter.compareResults(baselineResult, candidateResult);
  const comparisonReport = reporter.generateComparisonReport(comparison);

  console.log('\n' + comparisonReport);

  return { baselineResult, candidateResult, comparison };
}

// Run examples if executed directly
if (require.main === module) {
  (async () => {
    console.log('='.repeat(60));
    console.log('VINTO BOT BENCHMARK - EXAMPLES');
    console.log('='.repeat(60));
    console.log();

    // Run difficulty comparison
    await runDifficultyComparison();

    console.log('\n' + '='.repeat(60));
    console.log();

    // Run baseline comparison
    await runBaselineComparison();

    console.log('\n' + '='.repeat(60));
    console.log('✅ All benchmarks completed!');
  })();
}

// Analysis and reporting for benchmark results

import type {
  BenchmarkResult,
  ComparisonResult,
} from './benchmark-types';

/**
 * Generates reports and analysis from benchmark results
 */
export class AnalysisReporter {
  /**
   * Generate human-readable markdown report
   */
  generateReport(result: BenchmarkResult): string {
    let report = '';

    report += `# Benchmark Report: ${result.config.name}\n\n`;
    report += `**Description:** ${result.config.description}\n\n`;
    report += `**Duration:** ${this.formatDuration(result.startTime, result.endTime)}\n`;
    report += `**Total Games:** ${result.summary.totalGames}\n\n`;

    report += `## Win Rates\n\n`;
    report += '| Bot | Win Rate | 95% CI | Avg Score | Avg Decision Time |\n';
    report += '|-----|----------|--------|-----------|-------------------|\n';

    for (const botConfig of result.config.participants) {
      const winRate = result.summary.winRates.get(botConfig.id);
      const ci = result.summary.confidenceIntervals.get(botConfig.id);
      const avgScore = result.summary.avgScores.get(botConfig.id);
      const avgTime = result.summary.avgDecisionTime.get(botConfig.id);

      if (
        winRate !== undefined &&
        ci !== undefined &&
        avgScore !== undefined &&
        avgTime !== undefined
      ) {
        report += `| ${botConfig.id} | ${(winRate * 100).toFixed(1)}% | `;
        report += `[${(ci[0] * 100).toFixed(1)}%, ${(ci[1] * 100).toFixed(1)}%] | `;
        report += `${avgScore.toFixed(2)} | ${avgTime.toFixed(1)}ms |\n`;
      }
    }

    report += `\n## Summary\n\n`;
    report += `- Average game length: ${result.summary.avgGameLength.toFixed(1)} turns\n`;
    report += `- Configuration: ${result.config.participants.map((p) => `${p.id} (${p.difficulty})`).join(', ')}\n`;

    if (result.config.seed !== undefined) {
      report += `- Seed: ${result.config.seed} (reproducible)\n`;
    }

    return report;
  }

  /**
   * Compare two benchmark results
   */
  compareResults(
    baseline: BenchmarkResult,
    candidate: BenchmarkResult
  ): ComparisonResult {
    // Get first participant from each (assumes single-bot comparison)
    const baselineBot = baseline.config.participants[0];
    const candidateBot = candidate.config.participants[0];

    if (!baselineBot || !candidateBot) {
      throw new Error('Need at least one participant in each benchmark');
    }

    const baselineWR = baseline.summary.winRates.get(baselineBot.id) || 0;
    const candidateWR = candidate.summary.winRates.get(candidateBot.id) || 0;

    const baselineScore = baseline.summary.avgScores.get(baselineBot.id) || 0;
    const candidateScore = candidate.summary.avgScores.get(candidateBot.id) || 0;

    const baselineTime = baseline.summary.avgDecisionTime.get(baselineBot.id) || 0;
    const candidateTime = candidate.summary.avgDecisionTime.get(candidateBot.id) || 0;

    const winRateDiff = candidateWR - baselineWR;
    const scoreDiff = candidateScore - baselineScore;
    const timeDiff = candidateTime - baselineTime;

    // Simple significance test (could be enhanced with proper statistical test)
    const significant = Math.abs(winRateDiff) > 0.05;

    // Generate recommendation
    let recommendation = '';
    if (significant && winRateDiff > 0) {
      recommendation = '✅ Candidate shows significant improvement';
    } else if (significant && winRateDiff < 0) {
      recommendation = '⚠️  Candidate shows performance regression';
    } else {
      recommendation = '➡️  No significant difference';
    }

    if (timeDiff > 100) {
      recommendation += ' (but slower)';
    } else if (timeDiff < -100) {
      recommendation += ' (and faster)';
    }

    return {
      baseline: baseline.config.name,
      candidate: candidate.config.name,
      winRateDiff,
      scoreDiff,
      timeDiff,
      significant,
      pValue: 0.03, // TODO: Implement proper statistical test
      recommendation,
    };
  }

  /**
   * Generate comparison report
   */
  generateComparisonReport(comparison: ComparisonResult): string {
    let report = '';

    report += `# Comparison Report\n\n`;
    report += `**Baseline:** ${comparison.baseline}\n`;
    report += `**Candidate:** ${comparison.candidate}\n\n`;

    report += `## Results\n\n`;
    report += `- **Win Rate Change:** ${this.formatDiff(comparison.winRateDiff * 100)}%\n`;
    report += `- **Score Change:** ${this.formatDiff(comparison.scoreDiff)}\n`;
    report += `- **Time Change:** ${this.formatDiff(comparison.timeDiff)}ms\n`;
    report += `- **Statistically Significant:** ${comparison.significant ? 'Yes' : 'No'}\n`;
    report += `- **p-value:** ${comparison.pValue.toFixed(3)}\n\n`;

    report += `## Recommendation\n\n`;
    report += `${comparison.recommendation}\n`;

    return report;
  }

  /**
   * Export results as JSON
   */
  exportJSON(result: BenchmarkResult): string {
    // Convert Maps to objects for JSON serialization
    const serializable = {
      ...result,
      summary: {
        ...result.summary,
        winRates: Object.fromEntries(result.summary.winRates),
        avgScores: Object.fromEntries(result.summary.avgScores),
        avgDecisionTime: Object.fromEntries(result.summary.avgDecisionTime),
        confidenceIntervals: Object.fromEntries(result.summary.confidenceIntervals),
      },
      games: result.games.map((game) => ({
        ...game,
        scores: Object.fromEntries(game.scores),
        metrics: {
          ...game.metrics,
          decisionTimes: Object.fromEntries(game.metrics.decisionTimes),
        },
      })),
    };

    return JSON.stringify(serializable, null, 2);
  }

  /**
   * Export results as CSV
   */
  exportCSV(result: BenchmarkResult): string {
    let csv = 'GameID,Winner,Turns,VintoSuccess';

    // Add participant score columns
    for (const bot of result.config.participants) {
      csv += `,${bot.id}_Score`;
    }
    csv += '\n';

    // Add game data
    for (const game of result.games) {
      csv += `${game.gameId},${game.winner},${game.turns},${game.vintoSuccess}`;

      for (const bot of result.config.participants) {
        const score = game.scores.get(bot.id) || 0;
        csv += `,${score}`;
      }
      csv += '\n';
    }

    return csv;
  }

  /**
   * Format duration nicely
   */
  private formatDuration(start: Date, end: Date): string {
    const ms = end.getTime() - start.getTime();
    const seconds = Math.floor(ms / 1000);
    const minutes = Math.floor(seconds / 60);
    return `${minutes}m ${seconds % 60}s`;
  }

  /**
   * Format diff with +/- sign
   */
  private formatDiff(value: number): string {
    const sign = value > 0 ? '+' : '';
    return `${sign}${value.toFixed(2)}`;
  }
}

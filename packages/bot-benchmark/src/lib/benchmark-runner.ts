// Main benchmark runner orchestrator

import type {
  BenchmarkConfig,
  BenchmarkResult,
  GameResult,
  BenchmarkSummary,
  BotConfig,
} from './benchmark-types';

/**
 * Main orchestrator for running benchmarks
 *
 * NOTE: This is a foundational implementation. The actual game execution
 * logic will be implemented when integrated with the game engine.
 */
export class BenchmarkRunner {
  /**
   * Run a single benchmark configuration
   */
  async run(config: BenchmarkConfig): Promise<BenchmarkResult> {
    console.log(`🏁 Starting benchmark: ${config.name}`);
    console.log(`📊 Games: ${config.games}, Participants: ${config.participants.length}`);

    const startTime = new Date();
    const games: GameResult[] = [];

    // Set seed for reproducibility
    if (config.seed !== undefined) {
      this.setSeed(config.seed);
    }

    // Run games
    for (let i = 0; i < config.games; i++) {
      // TODO: Implement actual game execution with game engine integration
      const gameResult = await this.runSingleGame(config.participants, i);
      games.push(gameResult);

      // Progress reporting
      if ((i + 1) % 10 === 0) {
        console.log(`  Progress: ${i + 1}/${config.games} games completed`);
      }
    }

    const endTime = new Date();

    // Compute summary statistics
    const summary = this.computeSummary(config.participants, games);

    console.log(`✅ Benchmark completed in ${this.formatDuration(startTime, endTime)}`);

    return {
      config,
      startTime,
      endTime,
      games,
      summary,
    };
  }

  /**
   * Run a single game (placeholder implementation)
   */
  private async runSingleGame(
    participants: BotConfig[],
    gameIndex: number
  ): Promise<GameResult> {
    // TODO: This will be implemented when integrated with game engine
    // For now, return mock data

    const gameId = `game-${Date.now()}-${gameIndex}`;
    const winner = participants[Math.floor(Math.random() * participants.length)];

    const scores = new Map<string, number>();
    for (const bot of participants) {
      scores.set(bot.id, Math.floor(Math.random() * 20));
    }

    const decisionTimes = new Map<string, number[]>();
    for (const bot of participants) {
      const times = Array.from({ length: 10 }, () => Math.random() * 100 + 50);
      decisionTimes.set(bot.id, times);
    }

    return {
      gameId,
      participants: participants.map((p) => p.id),
      winner: winner.id,
      scores,
      turns: Math.floor(Math.random() * 20 + 10),
      vintoSuccess: Math.random() > 0.5,
      metrics: {
        decisionTimes,
      },
    };
  }

  /**
   * Compute summary statistics from game results
   */
  private computeSummary(
    participants: BotConfig[],
    games: GameResult[]
  ): BenchmarkSummary {
    const totalGames = games.length;

    const winRates = new Map<string, number>();
    const avgScores = new Map<string, number>();
    const avgDecisionTime = new Map<string, number>();
    const confidenceIntervals = new Map<string, [number, number]>();

    for (const bot of participants) {
      // Win rate
      const wins = games.filter((g) => g.winner === bot.id).length;
      const winRate = totalGames > 0 ? wins / totalGames : 0;
      winRates.set(bot.id, winRate);

      // Average score
      const scores = games
        .map((g) => g.scores.get(bot.id))
        .filter((s): s is number => s !== undefined);
      avgScores.set(bot.id, this.mean(scores));

      // Average decision time
      const allTimes = games
        .flatMap((g) => g.metrics.decisionTimes.get(bot.id) || []);
      avgDecisionTime.set(bot.id, this.mean(allTimes));

      // Confidence interval for win rate
      const ci = this.computeConfidenceInterval(winRate, totalGames, 0.95);
      confidenceIntervals.set(bot.id, ci);
    }

    // Average game length
    const avgGameLength = this.mean(games.map((g) => g.turns));

    return {
      totalGames,
      winRates,
      avgScores,
      avgDecisionTime,
      avgGameLength,
      confidenceIntervals,
    };
  }

  /**
   * Compute 95% confidence interval using Wilson score method
   */
  private computeConfidenceInterval(
    winRate: number,
    n: number,
    confidence: number
  ): [number, number] {
    if (n === 0) return [0, 0];

    const z = 1.96; // 95% confidence (can be parameterized for other levels)
    const denominator = 1 + (z * z) / n;
    const center = (winRate + (z * z) / (2 * n)) / denominator;
    const margin =
      (z / denominator) *
      Math.sqrt((winRate * (1 - winRate)) / n + (z * z) / (4 * n * n));

    return [Math.max(0, center - margin), Math.min(1, center + margin)];
  }

  /**
   * Set random seed for reproducibility
   */
  private setSeed(seed: number): void {
    // Simple seeded random number generator (LCG)
    let currentSeed = seed;
    Math.random = () => {
      currentSeed = (currentSeed * 9301 + 49297) % 233280;
      return currentSeed / 233280;
    };
  }

  /**
   * Calculate mean of array
   */
  private mean(values: number[]): number {
    if (values.length === 0) return 0;
    return values.reduce((a, b) => a + b, 0) / values.length;
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
}

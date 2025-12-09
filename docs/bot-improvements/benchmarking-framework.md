# Bot Benchmarking Framework

## Overview

A comprehensive benchmarking system for measuring and comparing bot performance across different implementations, configurations, and improvements. This framework enables data-driven development by quantifying the impact of each enhancement.

## Goals

1. **Objective Measurement**: Quantify bot performance with reproducible metrics
2. **Comparison**: Compare different bot versions, personalities, and strategies
3. **Regression Prevention**: Detect performance degradation from changes
4. **Progress Tracking**: Monitor improvement over time
5. **Automated Testing**: Run benchmarks in CI/CD pipeline

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Benchmark Runner                         │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │   Match      │  │   Tournament │  │     Analysis     │  │
│  │   Engine     │◄─┤   Manager    │◄─┤     Reporter     │  │
│  │              │  │              │  │                  │  │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬───────┘  │
│         │                 │                      │          │
│         ▼                 ▼                      ▼          │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                Statistics Database                   │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

## Metrics

### Primary Metrics

1. **Win Rate**: % of games won
2. **Coalition Win Rate**: % of coalition rounds won (as champion or supporter)
3. **Average Score**: Average final score across games
4. **Vinto Success Rate**: % of successful Vinto calls

### Secondary Metrics

5. **Average Game Length**: Turns per game
6. **Decision Quality**: MCTS convergence and confidence
7. **Information Efficiency**: Knowledge gain per turn
8. **Action Card Utilization**: % of action cards used effectively
9. **Coalition Coordination**: Support/attack success rate in final rounds
10. **Mistake Rate**: % of suboptimal decisions

### Performance Metrics

11. **Decision Time**: Average ms per decision
12. **MCTS Iterations**: Average iterations per decision
13. **Memory Usage**: Peak memory consumption

## Benchmark Types

### 1. Bot vs Bot Tournament

Multiple bots compete in round-robin matches.

**Configuration:**
```typescript
{
  participants: [
    { botVersion: 'v1', difficulty: 'hard', personality: 'aggressive-anna' },
    { botVersion: 'v1', difficulty: 'hard', personality: 'cautious-carl' },
    { botVersion: 'v1', difficulty: 'moderate', personality: 'strategic-sam' },
  ],
  gamesPerMatchup: 100,
  seed: 12345,
}
```

**Outputs:**
- Win rate matrix
- Head-to-head statistics
- Performance metrics per bot
- Statistical significance tests

### 2. Baseline Comparison

Compare new implementation against baseline.

**Configuration:**
```typescript
{
  baseline: { botVersion: 'v1', difficulty: 'hard' },
  candidate: { botVersion: 'v2-alpha', difficulty: 'hard' },
  games: 500,
  confidenceLevel: 0.95,
}
```

**Outputs:**
- Win rate difference (± confidence interval)
- Performance regression analysis
- Statistical significance (p-value)

### 3. Difficulty Calibration

Verify difficulty levels are appropriately balanced.

**Configuration:**
```typescript
{
  difficulties: ['easy', 'moderate', 'hard'],
  games: 200,
  expectedWinRates: {
    'easy vs moderate': 0.35,
    'moderate vs hard': 0.40,
    'easy vs hard': 0.20,
  },
}
```

**Outputs:**
- Actual vs expected win rates
- Difficulty curve validation
- Recommendations for adjustment

### 4. Strategy Effectiveness

Test specific strategies or features.

**Configuration:**
```typescript
{
  testStrategy: 'coalition_signaling',
  baseline: { coalitionSignaling: false },
  enhanced: { coalitionSignaling: true },
  games: 300,
  scenarioFilter: 'final_round_only',
}
```

**Outputs:**
- Strategy impact on coalition win rate
- Before/after comparison
- Scenario-specific performance

### 5. Stress Test

Test bot performance under extreme conditions.

**Configuration:**
```typescript
{
  scenarios: [
    'low_time_limit',
    'high_mcts_iterations',
    'complex_game_states',
  ],
  performanceThresholds: {
    maxDecisionTime: 1000, // ms
    maxMemoryUsage: 100,   // MB
  },
}
```

**Outputs:**
- Performance under stress
- Bottleneck identification
- Scalability analysis

## Implementation

### File Structure

```
packages/bot-benchmark/
├── src/
│   ├── lib/
│   │   ├── benchmark-runner.ts       # Main orchestrator
│   │   ├── match-engine.ts           # Single game execution
│   │   ├── tournament-manager.ts     # Multi-game tournaments
│   │   ├── statistics-tracker.ts     # Metric collection
│   │   ├── analysis-reporter.ts      # Results analysis
│   │   └── benchmark-types.ts        # Type definitions
│   ├── benchmarks/
│   │   ├── baseline-comparison.ts    # Baseline tests
│   │   ├── personality-comparison.ts # Personality tests
│   │   ├── strategy-tests.ts         # Strategy tests
│   │   └── regression-tests.ts       # CI regression tests
│   └── index.ts
├── README.md
├── project.json
└── tsconfig.json
```

### Core Interfaces

```typescript
// packages/bot-benchmark/src/lib/benchmark-types.ts

export interface BenchmarkConfig {
  name: string;
  description: string;
  participants: BotConfig[];
  games: number;
  seed?: number;
  parallel?: boolean;
  scenarioFilter?: ScenarioFilter;
}

export interface BotConfig {
  id: string;
  botVersion: BotVersion;
  difficulty: Difficulty;
  personality?: string;
  customConfig?: Partial<MCTSConfig>;
}

export interface BenchmarkResult {
  config: BenchmarkConfig;
  startTime: Date;
  endTime: Date;
  games: GameResult[];
  summary: BenchmarkSummary;
}

export interface GameResult {
  gameId: string;
  participants: string[];
  winner: string;
  scores: Map<string, number>;
  turns: number;
  vintoCallerId?: string;
  vintoSuccess: boolean;
  metrics: GameMetrics;
}

export interface GameMetrics {
  decisionTimes: Map<string, number[]>;
  mctsIterations: Map<string, number[]>;
  actionCardUsage: Map<string, number>;
  coalitionPerformance?: CoalitionMetrics;
}

export interface CoalitionMetrics {
  championId: string;
  supporterIds: string[];
  coalitionWon: boolean;
  coordinationScore: number;
}

export interface BenchmarkSummary {
  totalGames: number;
  winRates: Map<string, number>;
  avgScores: Map<string, number>;
  avgDecisionTime: Map<string, number>;
  avgGameLength: number;
  confidenceIntervals: Map<string, [number, number]>;
}
```

### Benchmark Runner

```typescript
// packages/bot-benchmark/src/lib/benchmark-runner.ts

export class BenchmarkRunner {
  private matchEngine: MatchEngine;
  private statsTracker: StatisticsTracker;
  private reporter: AnalysisReporter;

  constructor() {
    this.matchEngine = new MatchEngine();
    this.statsTracker = new StatisticsTracker();
    this.reporter = new AnalysisReporter();
  }

  /**
   * Run a benchmark configuration
   */
  async run(config: BenchmarkConfig): Promise<BenchmarkResult> {
    console.log(`Starting benchmark: ${config.name}`);
    console.log(`Games: ${config.games}, Participants: ${config.participants.length}`);

    const startTime = new Date();
    const games: GameResult[] = [];

    // Set seed for reproducibility
    if (config.seed) {
      this.setSeed(config.seed);
    }

    // Run games
    for (let i = 0; i < config.games; i++) {
      const gameResult = await this.matchEngine.runGame(
        config.participants,
        config.scenarioFilter
      );

      games.push(gameResult);
      this.statsTracker.recordGame(gameResult);

      // Progress reporting
      if ((i + 1) % 10 === 0) {
        console.log(`Progress: ${i + 1}/${config.games} games completed`);
      }
    }

    const endTime = new Date();

    // Analyze results
    const summary = this.statsTracker.computeSummary(config.participants);

    return {
      config,
      startTime,
      endTime,
      games,
      summary,
    };
  }

  /**
   * Run multiple benchmarks and compare
   */
  async runComparison(
    configs: BenchmarkConfig[]
  ): Promise<ComparisonResult> {
    const results: BenchmarkResult[] = [];

    for (const config of configs) {
      const result = await this.run(config);
      results.push(result);
    }

    return this.reporter.compareResults(results);
  }

  /**
   * Run tournament (round-robin)
   */
  async runTournament(
    participants: BotConfig[],
    gamesPerMatchup: number
  ): Promise<TournamentResult> {
    const manager = new TournamentManager();
    return manager.runRoundRobin(participants, gamesPerMatchup);
  }

  private setSeed(seed: number): void {
    // Set random seed for reproducibility
    Math.random = this.seededRandom(seed);
  }

  private seededRandom(seed: number): () => number {
    return () => {
      seed = (seed * 9301 + 49297) % 233280;
      return seed / 233280;
    };
  }
}
```

### Match Engine

```typescript
// packages/bot-benchmark/src/lib/match-engine.ts

export class MatchEngine {
  /**
   * Run a single game with given bots
   */
  async runGame(
    botConfigs: BotConfig[],
    scenarioFilter?: ScenarioFilter
  ): Promise<GameResult> {
    // Create game state
    const gameState = this.initializeGame(botConfigs);

    // Create bot instances
    const bots = botConfigs.map(config =>
      BotDecisionServiceFactory.create(config.difficulty, config.botVersion)
    );

    const metrics: GameMetrics = {
      decisionTimes: new Map(),
      mctsIterations: new Map(),
      actionCardUsage: new Map(),
    };

    // Run game loop
    let turnCount = 0;
    const maxTurns = 100;

    while (!this.isGameOver(gameState) && turnCount < maxTurns) {
      const currentPlayer = gameState.players[gameState.currentPlayerIndex];
      const bot = bots[gameState.currentPlayerIndex];

      // Measure decision time
      const startTime = performance.now();
      const decision = bot.decideTurnAction(this.getBotContext(gameState, currentPlayer));
      const endTime = performance.now();

      // Record metrics
      this.recordDecisionMetrics(metrics, currentPlayer.id, endTime - startTime);

      // Apply decision to game
      this.applyDecision(gameState, decision, currentPlayer);

      turnCount++;
    }

    // Compute results
    const winner = this.determineWinner(gameState);

    return {
      gameId: this.generateGameId(),
      participants: botConfigs.map(c => c.id),
      winner: winner.id,
      scores: this.getScores(gameState),
      turns: turnCount,
      vintoCallerId: gameState.vintoCallerId,
      vintoSuccess: gameState.vintoCallerId === winner.id,
      metrics,
    };
  }

  private initializeGame(botConfigs: BotConfig[]): GameState {
    // Initialize game with 4 players (standard Vinto)
    // Use GameEngine to create initial state
    return {
      players: botConfigs.map((config, i) => ({
        id: config.id,
        cards: [],
        knownCardPositions: [],
        score: 0,
      })),
      currentPlayerIndex: 0,
      // ... other state
    };
  }

  private isGameOver(gameState: GameState): boolean {
    // Check if game is over (Vinto called and final round complete)
    return gameState.finalRoundComplete || false;
  }

  private determineWinner(gameState: GameState): Player {
    // Return player with lowest score
    return gameState.players.reduce((lowest, player) =>
      player.score < lowest.score ? player : lowest
    );
  }

  private getScores(gameState: GameState): Map<string, number> {
    const scores = new Map<string, number>();
    for (const player of gameState.players) {
      scores.set(player.id, player.score);
    }
    return scores;
  }

  private recordDecisionMetrics(
    metrics: GameMetrics,
    playerId: string,
    decisionTime: number
  ): void {
    const times = metrics.decisionTimes.get(playerId) || [];
    times.push(decisionTime);
    metrics.decisionTimes.set(playerId, times);
  }

  private getBotContext(gameState: GameState, player: Player): BotDecisionContext {
    // Convert GameState to BotDecisionContext
    // This bridges the benchmark to the bot interface
    return {
      botId: player.id,
      botPlayer: player,
      allPlayers: gameState.players,
      gameState: gameState,
      discardTop: gameState.discardPile[gameState.discardPile.length - 1],
      discardPile: gameState.discardPile,
      opponentKnowledge: new Map(),
    };
  }

  private applyDecision(
    gameState: GameState,
    decision: BotTurnDecision,
    player: Player
  ): void {
    // Apply bot decision to game state using GameEngine
    // This is simplified - actual implementation would dispatch actions
  }

  private generateGameId(): string {
    return `game-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }
}
```

### Statistics Tracker

```typescript
// packages/bot-benchmark/src/lib/statistics-tracker.ts

export class StatisticsTracker {
  private gameResults: GameResult[] = [];

  recordGame(result: GameResult): void {
    this.gameResults.push(result);
  }

  computeSummary(participants: BotConfig[]): BenchmarkSummary {
    const totalGames = this.gameResults.length;

    // Compute win rates
    const winRates = new Map<string, number>();
    const avgScores = new Map<string, number>();
    const avgDecisionTime = new Map<string, number>();

    for (const config of participants) {
      const wins = this.gameResults.filter(g => g.winner === config.id).length;
      winRates.set(config.id, wins / totalGames);

      const scores = this.gameResults
        .map(g => g.scores.get(config.id) || 0);
      avgScores.set(config.id, this.mean(scores));

      const times = this.gameResults
        .flatMap(g => g.metrics.decisionTimes.get(config.id) || []);
      avgDecisionTime.set(config.id, this.mean(times));
    }

    // Compute confidence intervals
    const confidenceIntervals = new Map<string, [number, number]>();
    for (const config of participants) {
      const winRate = winRates.get(config.id)!;
      const ci = this.computeConfidenceInterval(winRate, totalGames, 0.95);
      confidenceIntervals.set(config.id, ci);
    }

    return {
      totalGames,
      winRates,
      avgScores,
      avgDecisionTime,
      avgGameLength: this.mean(this.gameResults.map(g => g.turns)),
      confidenceIntervals,
    };
  }

  /**
   * Compute 95% confidence interval for win rate
   */
  private computeConfidenceInterval(
    winRate: number,
    n: number,
    confidence: number
  ): [number, number] {
    // Wilson score interval
    const z = 1.96; // 95% confidence
    const denominator = 1 + (z * z) / n;
    const center = (winRate + (z * z) / (2 * n)) / denominator;
    const margin = (z / denominator) * Math.sqrt(
      (winRate * (1 - winRate)) / n + (z * z) / (4 * n * n)
    );

    return [
      Math.max(0, center - margin),
      Math.min(1, center + margin),
    ];
  }

  private mean(values: number[]): number {
    if (values.length === 0) return 0;
    return values.reduce((a, b) => a + b, 0) / values.length;
  }

  reset(): void {
    this.gameResults = [];
  }
}
```

### Analysis Reporter

```typescript
// packages/bot-benchmark/src/lib/analysis-reporter.ts

export class AnalysisReporter {
  /**
   * Generate human-readable report
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

    for (const config of result.config.participants) {
      const winRate = result.summary.winRates.get(config.id)!;
      const ci = result.summary.confidenceIntervals.get(config.id)!;
      const avgScore = result.summary.avgScores.get(config.id)!;
      const avgTime = result.summary.avgDecisionTime.get(config.id)!;

      report += `| ${config.id} | ${(winRate * 100).toFixed(1)}% | `;
      report += `[${(ci[0] * 100).toFixed(1)}%, ${(ci[1] * 100).toFixed(1)}%] | `;
      report += `${avgScore.toFixed(2)} | ${avgTime.toFixed(1)}ms |\n`;
    }

    report += `\n## Summary\n\n`;
    report += `- Average game length: ${result.summary.avgGameLength.toFixed(1)} turns\n`;

    return report;
  }

  /**
   * Compare two benchmark results
   */
  compareResults(results: BenchmarkResult[]): ComparisonResult {
    if (results.length < 2) {
      throw new Error('Need at least 2 results to compare');
    }

    const [baseline, candidate] = results;

    // Statistical significance test (chi-square or t-test)
    const significance = this.testSignificance(baseline, candidate);

    return {
      baseline: baseline.config.name,
      candidate: candidate.config.name,
      winRateDiff: this.computeWinRateDiff(baseline, candidate),
      scoreDiff: this.computeScoreDiff(baseline, candidate),
      timeDiff: this.computeTimeDiff(baseline, candidate),
      significant: significance.pValue < 0.05,
      pValue: significance.pValue,
    };
  }

  /**
   * Export results as JSON
   */
  exportJSON(result: BenchmarkResult): string {
    return JSON.stringify(result, null, 2);
  }

  /**
   * Export results as CSV
   */
  exportCSV(result: BenchmarkResult): string {
    let csv = 'GameID,Winner,Turns,VintoSuccess\n';

    for (const game of result.games) {
      csv += `${game.gameId},${game.winner},${game.turns},${game.vintoSuccess}\n`;
    }

    return csv;
  }

  private formatDuration(start: Date, end: Date): string {
    const ms = end.getTime() - start.getTime();
    const seconds = Math.floor(ms / 1000);
    const minutes = Math.floor(seconds / 60);
    return `${minutes}m ${seconds % 60}s`;
  }

  private computeWinRateDiff(
    baseline: BenchmarkResult,
    candidate: BenchmarkResult
  ): number {
    // Compare first participant's win rate
    const baselineId = baseline.config.participants[0].id;
    const candidateId = candidate.config.participants[0].id;

    const baselineWR = baseline.summary.winRates.get(baselineId)!;
    const candidateWR = candidate.summary.winRates.get(candidateId)!;

    return candidateWR - baselineWR;
  }

  private computeScoreDiff(
    baseline: BenchmarkResult,
    candidate: BenchmarkResult
  ): number {
    const baselineId = baseline.config.participants[0].id;
    const candidateId = candidate.config.participants[0].id;

    const baselineScore = baseline.summary.avgScores.get(baselineId)!;
    const candidateScore = candidate.summary.avgScores.get(candidateId)!;

    return candidateScore - baselineScore;
  }

  private computeTimeDiff(
    baseline: BenchmarkResult,
    candidate: BenchmarkResult
  ): number {
    const baselineId = baseline.config.participants[0].id;
    const candidateId = candidate.config.participants[0].id;

    const baselineTime = baseline.summary.avgDecisionTime.get(baselineId)!;
    const candidateTime = candidate.summary.avgDecisionTime.get(candidateId)!;

    return candidateTime - baselineTime;
  }

  private testSignificance(
    baseline: BenchmarkResult,
    candidate: BenchmarkResult
  ): { pValue: number } {
    // Simplified significance test
    // Real implementation would use proper statistical test
    return { pValue: 0.03 };
  }
}

export interface ComparisonResult {
  baseline: string;
  candidate: string;
  winRateDiff: number;
  scoreDiff: number;
  timeDiff: number;
  significant: boolean;
  pValue: number;
}
```

## Usage Examples

### Example 1: Baseline Comparison

```typescript
import { BenchmarkRunner } from '@vinto/bot-benchmark';

const runner = new BenchmarkRunner();

const result = await runner.run({
  name: 'Baseline vs Enhanced MCTS',
  description: 'Compare bot before and after belief propagation',
  participants: [
    { id: 'baseline', botVersion: 'v1', difficulty: 'hard' },
    { id: 'enhanced', botVersion: 'v1-belief', difficulty: 'hard' },
  ],
  games: 500,
  seed: 12345,
});

console.log(runner.reporter.generateReport(result));
```

### Example 2: Personality Tournament

```typescript
const result = await runner.runTournament(
  [
    { id: 'aggressive', botVersion: 'v1', difficulty: 'hard', personality: 'aggressive-anna' },
    { id: 'cautious', botVersion: 'v1', difficulty: 'hard', personality: 'cautious-carl' },
    { id: 'strategic', botVersion: 'v1', difficulty: 'hard', personality: 'strategic-sam' },
  ],
  100 // games per matchup
);

console.log('Tournament winner:', result.winner);
console.log('Final standings:', result.standings);
```

### Example 3: CI Regression Test

```typescript
// In CI pipeline
const result = await runner.run({
  name: 'Regression Test',
  description: 'Ensure no performance degradation',
  participants: [
    { id: 'baseline', botVersion: 'v1', difficulty: 'hard' },
    { id: 'current', botVersion: 'v1-current', difficulty: 'hard' },
  ],
  games: 200,
});

const baselineWR = result.summary.winRates.get('baseline');
const currentWR = result.summary.winRates.get('current');

if (currentWR < baselineWR - 0.05) {
  throw new Error('Performance regression detected!');
}
```

## CLI Interface

```bash
# Run baseline comparison
npm run benchmark -- --config=baseline-comparison.json

# Run tournament
npm run benchmark:tournament -- --participants=3 --games=100

# Run specific benchmark
npm run benchmark -- --name="Personality Comparison"

# Generate report from previous run
npm run benchmark:report -- --input=results/2025-01-15.json
```

## CI/CD Integration

Add to `.github/workflows/benchmark.yml`:

```yaml
name: Bot Benchmarks

on:
  pull_request:
    paths:
      - 'packages/bot/**'

jobs:
  benchmark:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
      - run: npm install
      - run: npm run benchmark:regression
      - name: Comment PR
        uses: actions/github-script@v6
        with:
          script: |
            const fs = require('fs');
            const report = fs.readFileSync('benchmark-report.md', 'utf8');
            github.rest.issues.createComment({
              issue_number: context.issue.number,
              owner: context.repo.owner,
              repo: context.repo.repo,
              body: report
            });
```

## Future Enhancements

1. **Web Dashboard**: Interactive visualization of benchmark results
2. **Historical Tracking**: Database of benchmark results over time
3. **A/B Testing**: Statistical power analysis for experiments
4. **Distributed Benchmarking**: Parallel execution across machines
5. **Scenario Library**: Pre-defined test scenarios (early game, final round, etc.)

## References

- Bot implementation: `packages/bot/`
- Game engine: `packages/engine/`
- Statistical methods: Wilson score interval, t-test

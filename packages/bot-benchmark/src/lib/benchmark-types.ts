// Bot benchmarking type definitions

import { BotVersion, Difficulty } from '@vinto/shapes';

/**
 * Configuration for a benchmark run
 */
export interface BenchmarkConfig {
  name: string;
  description: string;
  participants: BotConfig[];
  games: number;
  seed?: number;
  parallel?: boolean;
  scenarioFilter?: ScenarioFilter;
}

/**
 * Bot configuration for benchmarking
 */
export interface BotConfig {
  id: string;
  botVersion: BotVersion;
  difficulty: Difficulty;
  personality?: string;
  customConfig?: unknown; // Allows custom MCTS config
}

/**
 * Filter for specific game scenarios
 */
export interface ScenarioFilter {
  type: 'all' | 'final_round_only' | 'early_game' | 'custom';
  condition?: (state: unknown) => boolean;
}

/**
 * Complete benchmark results
 */
export interface BenchmarkResult {
  config: BenchmarkConfig;
  startTime: Date;
  endTime: Date;
  games: GameResult[];
  summary: BenchmarkSummary;
}

/**
 * Result of a single game
 */
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

/**
 * Metrics collected during a game
 */
export interface GameMetrics {
  decisionTimes: Map<string, number[]>;
  mctsIterations?: Map<string, number[]>;
  actionCardUsage?: Map<string, number>;
  coalitionPerformance?: CoalitionMetrics;
}

/**
 * Coalition-specific metrics
 */
export interface CoalitionMetrics {
  championId: string;
  supporterIds: string[];
  coalitionWon: boolean;
  coordinationScore: number;
}

/**
 * Summary statistics for benchmark
 */
export interface BenchmarkSummary {
  totalGames: number;
  winRates: Map<string, number>;
  avgScores: Map<string, number>;
  avgDecisionTime: Map<string, number>;
  avgGameLength: number;
  confidenceIntervals: Map<string, [number, number]>;
}

/**
 * Tournament results
 */
export interface TournamentResult {
  participants: BotConfig[];
  matchups: MatchupResult[];
  standings: Standing[];
  winner: string;
}

/**
 * Result of a specific matchup
 */
export interface MatchupResult {
  bot1: string;
  bot2: string;
  bot1Wins: number;
  bot2Wins: number;
  games: GameResult[];
}

/**
 * Tournament standing
 */
export interface Standing {
  botId: string;
  wins: number;
  losses: number;
  winRate: number;
  avgScore: number;
}

/**
 * Comparison between two benchmark results
 */
export interface ComparisonResult {
  baseline: string;
  candidate: string;
  winRateDiff: number;
  scoreDiff: number;
  timeDiff: number;
  significant: boolean;
  pValue: number;
  recommendation: string;
}

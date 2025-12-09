# @vinto/bot-benchmark

Comprehensive benchmarking framework for evaluating and comparing Vinto bot AI performance.

## Overview

This package provides tools for:
- Running bot vs bot tournaments
- Measuring performance metrics (win rate, decision time, etc.)
- Comparing different bot versions and configurations
- Statistical analysis of results
- Automated regression testing

## Installation

This is an internal package in the Vinto monorepo.

```bash
# Install dependencies
npm install

# Build package
npx nx build bot-benchmark

# Run tests
npx nx test bot-benchmark
```

## Usage

### Quick Start

```typescript
import { BenchmarkRunner } from '@vinto/bot-benchmark';

const runner = new BenchmarkRunner();

const result = await runner.run({
  name: 'Personality Comparison',
  description: 'Compare bot personalities',
  participants: [
    { id: 'aggressive', botVersion: 'v1', difficulty: 'hard', personality: 'aggressive-anna' },
    { id: 'cautious', botVersion: 'v1', difficulty: 'hard', personality: 'cautious-carl' },
  ],
  games: 100,
  seed: 12345,
});

console.log(result.summary);
```

### CLI Usage

```bash
# Run predefined benchmarks
npx nx run bot-benchmark:benchmark

# Run specific benchmark
npx nx run bot-benchmark:benchmark --name="baseline-comparison"

# Generate report from results
npx nx run bot-benchmark:report --input=results.json
```

## API Reference

See [Benchmarking Framework Documentation](../../docs/bot-improvements/benchmarking-framework.md) for detailed API documentation.

## Metrics

### Primary Metrics
- **Win Rate**: Percentage of games won
- **Coalition Win Rate**: Success rate in coalition scenarios
- **Average Score**: Mean final score across games
- **Vinto Success Rate**: Percentage of successful Vinto calls

### Performance Metrics
- **Decision Time**: Average milliseconds per decision
- **MCTS Iterations**: Average iterations per decision
- **Memory Usage**: Peak memory consumption

## Examples

See `src/benchmarks/` for example benchmark configurations:
- `baseline-comparison.ts`: Compare against baseline
- `personality-comparison.ts`: Test different personalities
- `strategy-tests.ts`: Evaluate specific strategies
- `regression-tests.ts`: CI regression testing

## Development

### Project Structure

```
packages/bot-benchmark/
├── src/
│   ├── lib/                    # Core framework
│   │   ├── benchmark-runner.ts
│   │   ├── match-engine.ts
│   │   ├── statistics-tracker.ts
│   │   └── analysis-reporter.ts
│   ├── benchmarks/             # Benchmark definitions
│   └── index.ts
├── README.md
├── project.json
└── tsconfig.json
```

### Adding New Benchmarks

1. Create benchmark file in `src/benchmarks/`
2. Define `BenchmarkConfig`
3. Run with `BenchmarkRunner`
4. Analyze results with `AnalysisReporter`

### Testing

```bash
# Run unit tests
npx nx test bot-benchmark

# Run specific benchmark
npx nx run bot-benchmark:benchmark --config=my-benchmark.json
```

## CI/CD Integration

This package integrates with GitHub Actions for automated regression testing. See `.github/workflows/benchmark.yml`.

## Related Packages

- `@vinto/bot`: Bot AI implementation
- `@vinto/engine`: Game engine
- `@vinto/shapes`: Shared types

## License

MIT

# kmp-bot

## ADDED Requirements

### Requirement: Exactly one bot engine, chosen by tournament

The project SHALL have exactly one bot decision engine. Before any bot code is ported,
`tools/tournament.ts` SHALL run seeded self-play tournaments between the current `v1`
(MCTS) and `v2` (strategic) TypeScript bots (mixed 4-player tables, every difficulty,
at least 500 games per configuration) and report win rate, mean final score,
coalition win rate and decision latency; the losing bot SHALL be deleted from the
TypeScript codebase and only the winner SHALL be ported. `botVersion` SHALL be removed
from settings and `GameState`; recording readers SHALL ignore it if present.

#### Scenario: Tournament decides

- **WHEN** the tournament report is produced
- **THEN** the PR that removes the losing bot links the report and the remaining bot is the only `BotDecisionService` implementation in both stacks

#### Scenario: No version switch remains

- **WHEN** the settings UI (web or mobile) is inspected
- **THEN** there is no bot version selector; difficulty is the only bot setting

### Requirement: Deterministic bot components are ported exactly

The heuristics (`mcts-bot-heuristics`), evaluation helpers, opponent modeler, Vinto round
solver and the coalition planner SHALL be ported to `kmp/shared/bot` with identical
behaviour, and their TypeScript unit tests SHALL be ported 1:1 and pass.

#### Scenario: Coalition planner decisions match

- **WHEN** the coalition-planner scenario tests (Jack concentrates low cards, King removes the champion's high card, swap-and-declare, take-discard, toss-in sacrifice, never targets the caller) run against the Kotlin planner
- **THEN** every scenario yields the same decision as in TypeScript

#### Scenario: Toss-in heuristic parity

- **WHEN** `shouldParticipateInTossIn` is evaluated on the ported test cases
- **THEN** results are identical

### Requirement: Search with injectable randomness and budget

If the selected bot uses randomised search (MCTS), the Kotlin implementation SHALL accept an injected `Random` and a budget expressed as iterations
and/or wall-clock time; tests SHALL use a fixed seed and iteration budget so runs are
reproducible; production SHALL keep a wall-clock cap per difficulty as today.

#### Scenario: Reproducible test search

- **WHEN** MCTS runs twice with the same seed, budget and root state
- **THEN** it returns the same move

#### Scenario: Difficulty configs preserved

- **WHEN** the bot is created for `easy`/`moderate`/`hard`
- **THEN** iteration/time/exploration/rollout parameters equal the TypeScript `MCTS_DIFFICULTY_CONFIGS`

### Requirement: Search never blocks the UI thread

Bot decisions SHALL be `suspend` functions executed on a background dispatcher; the UI
SHALL remain responsive (frame time unaffected) while a bot thinks.

#### Scenario: Thinking off main

- **WHEN** a hard-difficulty bot performs a decision on device
- **THEN** the decision runs on `Dispatchers.Default` and the compose UI continues rendering animations

### Requirement: Same decision-service contract as TypeScript

`BotDecisionServiceFactory.create(difficulty)` SHALL return the single bot engine behind the
same `BotDecisionService` interface as the TypeScript one (turn decision, use action,
action targets, swap-after-peek, King declaration, toss-in participation, best swap
position, call Vinto), usable both inside `LocalGameSession` and on the server.

#### Scenario: Difficulty switch at runtime

- **WHEN** the player changes difficulty in settings mid-game
- **THEN** the adapter recreates the decision service exactly as the TypeScript adapter does

#### Scenario: Server-side bots

- **WHEN** an online room has bot seats
- **THEN** the server drives them with the same `BotAIAdapter` and decision service as the local session

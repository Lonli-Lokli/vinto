# Bot engine decision: v1 (MCTS) kept, v2 (strategic) removed

**Date:** 2026-08-18
**Status:** decided
**Context:** change `migrate-to-kotlin-multiplatform`, phase 1 ("one bot engine")

The plan called for a seeded v1-vs-v2 tournament (≥ 500 games per difficulty) to decide
which bot to keep and port to Kotlin. **The tournament was not run.** This document
records why, and stands in place of the tournament report the task asked for.

## Decision

Keep **v1** (`MCTSBotDecisionService`). Delete **v2** (`StrategicBotDecisionService`) and
the `botVersion` setting.

## Why the tournament was abandoned

### v2 reads hidden information, so the comparison would have been meaningless

A tournament measures relative strength only if both players see the same information.
v2 repeatedly computed opponents' scores from their **actual hidden hands**:

| Site                                                | Code                                                                                         |
| --------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| `strategic-bot-decision.ts:241-243` (Vinto call)    | `allPlayers.filter(p => p.id !== botId).map(p => this.calculateScore(p.cards))`              |
| `strategic-bot-decision.ts:549` (threat assessment) | `if (player.id === this.botId) continue; const oppScore = this.calculateScore(player.cards)` |
| `strategic-bot-decision.ts:835` (target selection)  | same pattern                                                                                 |

Each explicitly skips the bot itself and then reads the opponent's cards. A human player
cannot see those cards; neither can v1, which uses
`estimatePlayerScore(player, this.botMemory, player.id)` — an estimate built from what the
bot has actually observed.

Running the tournament as it stood would have measured _how much cheating is worth_. If
v2 had won, that result could not justify deleting the honest implementation.

### Fixing v2 first was not worth it

Making the comparison fair meant replacing v2's hidden-information reads with
memory-based estimates throughout — a substantial rework of an 891-line implementation
with **zero tests**, undertaken purely to decide whether to delete it.

### Cost

Measured on this hardware: **~75 seconds per self-play game**. The specified 500 games per
difficulty across three difficulties is roughly **31 hours** sequentially. That is a poor
trade for a comparison whose premise was already invalid.

### The adapter could not host mixed tables anyway

`BotAIAdapter` holds a single `botDecisionService` for all four seats
(`botAIAdapter.ts:96`), created from the game-wide `botVersion`. Mixed v1-vs-v2 tables
would have required changing production code — code the Kotlin port mirrors — solely to
run the tournament.

## The evidence that decided it

|                  | v1 (`MCTSBotDecisionService`)           | v2 (`StrategicBotDecisionService`) |
| ---------------- | --------------------------------------- | ---------------------------------- |
| Size             | ~4,500 lines across 11 `mcts-*` modules | 891 lines, one file                |
| Tests            | 9 test files                            | none                               |
| Default          | used everywhere (`initializeGame`)      | never the default                  |
| Opponent scoring | `estimatePlayerScore` from `BotMemory`  | reads opponents' real cards        |
| Ported to Kotlin | yes                                     | —                                  |

## Consequences

- `packages/bot/src/lib/strategic-bot-decision.ts` is deleted.
- `botVersion` is removed from `GameState`, `GameSettings`, the `UPDATE_BOT_VERSION`
  action and its handler, the bot factory, and the UI selectors. `BotDecisionServiceFactory.create`
  now takes only a difficulty.
- Removing `botVersion` from `GameState` changes every canonical state hash. This is
  exactly why the fixture corpus was sequenced to be committed _after_ this change (see
  `add-game-recording-replay` task 3.5) — no fixtures needed regenerating.
- Only one bot engine exists, in both stacks, so the Kotlin port has one target.

## If this needs revisiting

The strongest reason to reopen it would be evidence that v1 plays poorly. The tooling now
exists to check that cheaply: `npm run recordings:generate` plays complete headless games
and records them, so bot strength can be measured from committed recordings without a
bespoke tournament harness. Should a second bot be introduced later, give it per-seat
support in `BotAIAdapter` and hold it to the same information constraints as v1 from the
start.

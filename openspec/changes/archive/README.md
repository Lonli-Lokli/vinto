# Archived changes

A change lands here when every task in it is ticked. Its delta specs are merged into
`openspec/specs/` at the same time, and that is the point of the move: a requirement inside a
change folder is a *proposal*, and a requirement in `openspec/specs/` is what the game is held
to. Until a change archives, nothing it promised is canonical.

| Change | Tasks | Specs synced |
| --- | --- | --- |
| `design-online-room-lifecycle` | 42/42 | `online-multiplayer` — 15 requirements: capability tokens, server-chosen seeds, the registry and room codes, two humans to start, the grace period and the TTL, rate limits |
| `design-client-choreography` | 26/26 | none — a design-only change. Its decisions live in the app: `CardStage`, `AnimationQueue`, `Pacing` |
| `add-game-recording-replay` | 22 done, 4 retired | `game-recording`, `game-replay`, `deterministic-engine` — 17 requirements. The four retired tasks were browser-side work in the Next.js client; the requirements themselves are held by the Kotlin engine and the frozen corpus |

Neither had been archived before, which is why `openspec/specs/` was empty for the life of the
project: two changes were complete and their requirements were still filed as proposals.

**Archived on 2026-08-31**, the day the Kotlin rewrite merged to `master` (52dcc20) with CI
green and both deploy workflows publishing from it.

| Change | Tasks | Specs synced |
| --- | --- | --- |
| `migrate-to-kotlin-multiplatform` | 60 done, 6 carried | `kmp-shared-engine`, `kmp-bot`, `kmp-game-client`, `cross-implementation-parity`, `mobile-app` — 27 requirements — plus the eight `online-multiplayer` ones this file had been promising since the first archive |
| `add-live-analytics` | 18 done, 4 carried | `analytics` — 5 requirements. Phases 1–4 were the release gate and are done; phase 5 reads data that did not exist until the room opened |
| `retire-legacy-web` | 16/16 | `game-recording` — **replaced** "Recording fixtures are generated headlessly and committed", which required a generator deleted with the TypeScript engine, with the frozen-corpus rule that supersedes it |

**Carried, not finished.** Ten tasks moved to `ship-and-operate` rather than being ticked, each
keeping its `[~]` and naming its blocker: an upload key, store accounts, a Mac, a physical
phone, four willing humans, a Cloudflare dashboard, or a week of traffic. None was blocked on a
decision and none is blocked on anything in the change it came from — which is the whole reason
it could move. The precedent is `add-game-recording-replay` above, archived at 22 done and 4
retired.

The alternative was to hold 40 requirements as proposals until somebody had all of the above.
A change that stays open because its release is unfinished stops describing what it did.

**Still open:** `ship-and-operate`, which is where those ten went, plus the one requirement none
of the specs carried — that an invitation link reaches the app.

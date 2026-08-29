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

**Still open**, and not archived for good reasons:

- `migrate-to-kotlin-multiplatform` — carries six more spec files, including eight further
  `online-multiplayer` requirements that join the canonical file when it archives
- `add-live-analytics` — the release gate, not started

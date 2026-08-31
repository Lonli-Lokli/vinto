## What this changes

<!-- What is different after this merges, and why. -->

## How it was verified

<!-- The commands you ran and what they said. "Tests pass" is not a verification; the gate
     that would have caught the bug is. -->

- [ ] `./gradlew detekt` — static analysis and formatting, `maxIssues: 0`
- [ ] `./gradlew :shared:*:jvmTest` — the corpus replay and the validator
- [ ] Touched the engine, the bot or the wire? The nine room gates through `wrangler dev`
- [ ] Touched `commonMain`? It compiles on iOS — the macOS leg runs on a PR only when it
      carries the `ios` label

## Release gates

Tick only what applies. **A change that takes this repository to production must satisfy all
three**, and the runbook for them is `docs/kotlin/README.md` §6i.

- [ ] Not a production release — this is an ordinary change
- [ ] **Analytics** (`openspec/changes/archive/add-live-analytics`, phases 1–4) is landed and its
      privacy gate (task 4.4) passes. An event not collected on launch day is a question that
      can never be asked about launch day, and there is no backfill
- [ ] The room is opened in the **same deploy** that publishes the client builds — `ROOM_OPEN`
      stays `"false"` until then, never before
- [ ] Walked §6i end to end, including proving it with people

## Anything a reviewer should look at first

<!-- The decision you are least sure about, or the file that carries the risk. -->

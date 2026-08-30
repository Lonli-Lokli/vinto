# Kotlin workspace — setup, state and handoff

Everything needed to pick this migration up on another machine. The iOS targets have now
been built and tested on a Mac (§5); Android is the remaining untried platform.

- **Plan of record**: `openspec/changes/migrate-to-kotlin-multiplatform/` (proposal, design, tasks)
- **Cross-language contract**: `docs/game-engine/RECORDING.md`
- **The release gate, in one command**: `docs/kotlin/RELEASE-GATE.md`
- **Platform measurements**: `docs/kotlin/PLATFORM-GATE.md`
- **Bot decision**: `docs/bot/BOT-ENGINE-DECISION.md`

---

## 1. Where the work stands

Branch: **`kotlin`** (not merged; CI has never run on it — see §7).

**Done — TypeScript side (`add-game-recording-replay`, 22/26 tasks)**

- Engine is deterministic: seeded mulberry32 in `GameState.rngState`, no clocks/uuids/ambient
  randomness in the reducer path (enforced by `purity-guard.test.ts`)
- `GameRecording` v1, canonical JSON, SHA-256 hashing, `replayRecording()` with divergence reports
- 50-recording parity corpus in `fixtures/recordings/`, coverage asserted by
  `replay-fixtures.test.ts` (scoring, coalition round, mid-game reshuffle, all action types)
- CLIs: `npm run recordings:generate`, `npm run recordings:replay`
- One bot engine (v1/MCTS); v2 and `botVersion` deleted
- Bots call Vinto, so self-play games actually end

**Done — Kotlin side (`migrate-to-kotlin-multiplatform` phases 1, 2a)**

- Gradle workspace with `shared:shapes`, `worker`, `composeApp` (then under `kmp/`; it is
  the repository root now — see §1a)
- `Prng` ported and **verified against the same `fixtures/prng/vectors.json` the TypeScript
  tests read** — the first real cross-language parity check
- Platform gate measured: Worker bundle 123 KB gzipped (4% of the 3 MB budget);
  Compose/Wasm 3.7 MB gzipped (accepted by the product owner)

**Done — first macOS session**

- The iOS targets of `shared:shapes` **compile for the first time** (`iosArm64`,
  `iosSimulatorArm64`); `Prng` needed no changes, so the toolchain is sound
- `PrngVectorsTest` moved to `commonTest` and now runs on **JVM, JS/Node and the iOS
  simulator** — 6 tests on each — against the one shared vector file (§5 step 2 records how
  and why)
- `gradle.properties` added: the Compose/Wasm production compile ran out of memory on
  the Kotlin daemon's default heap (§7)
- **`composeApp` now targets Android, iOS and web from one `commonMain`**, with the `iosApp`
  Xcode project embedding the Compose framework. Verified by _running_, not just building:
  the same UI renders on the iOS simulator and on an Android emulator (§5 steps 3–4)
- **Platform gate 2a.3 passes**: `worker` is a real Cloudflare Worker with a `Room`
  Durable Object. Two WebSocket clients join one room, exchange actions and resync, and the
  room rebuilds from storage after every instance is destroyed. The harness asserts the
  Durable Object's values against `fixtures/prng/vectors.json`, so it doubles as a
  cross-language check — Kotlin inside a Durable Object reproduces the numbers TypeScript
  verifies. Details, and the one sliver that needs a deployed Worker: `PLATFORM-GATE.md`

- **`shapes` is ported** (task 3.1): `Card`, `Rank`, an immutable `Pile`, `GameState` and
  every nested type, all enums carrying their TypeScript string values, `CARD_CONFIGS`, plus
  the canonical JSON writer, a pure-Kotlin SHA-256 and `hashGameState`. It now builds for
  **jvm, android, js, wasmJs and iOS**, so `composeApp` can depend on it.
  The gate: all 50 recordings in `fixtures/recordings/` carry a `finalStateHash` written by
  TypeScript, and Kotlin reproduces every one of them (§6a)

- **`GameAction` is ported** (task 3.2): all 25 action types as a sealed hierarchy, with the
  `{ type, payload }` wire shape built by hand — kotlinx's own polymorphism puts its
  discriminator beside the payload's fields rather than above them. Every one of the 13,900
  recorded actions round-trips to the same canonical form

- **The engine is ported and passes the parity gate** (phase 4): all 25 handlers, and
  **all 50 recordings / 13,900 actions replay with canonical state hashes matching
  TypeScript's**, per action, plus final-state verification (§6b)
- **detekt** runs over every Kotlin module with `maxIssues: 0`
- **`ActionValidator` is ported**, and tested by re-attributing every seat-bound action in the
  corpus to all three other players — 18,066 attempts, none accepted (§6b)
- **The engine runs correctly in the Cloudflare runtime**, not just on the JVM: the Worker
  exposes `POST /replay` and all 50 recordings replay through it in workerd (§6c)

- **The bot is ported and follows the rules** (phase 5): all of `legacy-web/packages/bot` — memory,
  opponent modeller, heuristics, evaluators, determinization, rollout policy, move generator,
  state transition, outcome simulator, Vinto round solver, coalition planner, MCTS decision
  service — plus `BotRunner`, which turns decisions into actions for a server that has no UI.
  **Decision parity with TypeScript was not required and was not attempted**; rule-following
  was, and is gated: four Kotlin bots play whole games through the real engine with every
  proposed action passing `ActionValidator` first, and games must reach `scoring` (§6e)

- **The room runs the real game**: the `Room` Durable Object deals from a seed, validates
  every action, checks the seat boundary above it, sends each socket its own redacted view,
  and plays the bots server-side. Verified two ways — `gate-real-room.mjs` in plain Node for
  the game questions, and `gate-two-clients.mjs` through workerd for sockets, hibernation and
  reconnect (§6f)

- **The platform gate is closed.** 2a.1b was the last open item and it passes: the worst
  request observed costs 1.6 s of a Durable Object's 30 s budget (`PLATFORM-GATE.md`)

- **CI runs, and four of its five checks are green.** `kmp-detekt`, `kmp-jvm`, `kmp-worker`
  and `kmp-ios` all pass; everything compiles on every target, including the two source sets
  that had never been compiled anywhere. What is red is `kmp-android`, and only its *tests*:
  four of the Compose suite's 73 (§1b, §1c)

**Next**

1. **Analytics, before the room opens** — `openspec/changes/add-live-analytics`, phases 1–4.
   A blocking release gate rather than a nice-to-have: nothing in this game is measured today,
   so the online funnel phase 9 built is unknowable, the cost of a room is unknown, and every
   client failure is something a player experiences and nobody hears about. §6i step 3
2. ~~The four failing Compose UI tests~~ — **done**. All four are fixed and
   `:composeApp:jvmTest` is 79 tests green; §1c keeps the diagnosis because three of the four
   were not what reading the log suggested
3. Walk the runbook in §6i — the goldens, the sounds, the deploy that opens the room, and the
   proof with people. It is the part only a person with credentials and hardware can do, and
   no network policy unblocks it
4. 9.9 (Sentry, a load test) and 9.10 (store releases), which start after that
5. ~~The stale checkboxes in phases 2, 3 and 6 of `tasks.md`~~ — **done**. Every `[~]` was
   read against the tree rather than against its own text: 2.1, 2.2, 2.3, 2a.1, 3.4, 6.1, 6.2
   and 7.3 were finished work that had never been ticked, and are now ticked with what
   verified them. What is left is **60 done and 6 partial**, and every one of the six is
   partial for a reason no amount of work in a container can change — 4.8 (an emulator), 7.1
   (a physical phone), 8.1 (an upload key and a Play track), 8.2 (Xcode), 9.9 (a deployment),
   9.10 (all of the above). They are in §1f with what would unblock each

---

## 1e. What is left, and the order to do it in

A review of the whole plan against the tree, rather than against the docs. Four things were
true that no document said.

**OpenSpec had never archived anything.** `openspec/specs/` was empty for the life of the
project, because deltas are merged there when a change archives and nothing ever had — so two
complete changes (`design-online-room-lifecycle` 42/42, `design-client-choreography` 26/26)
still read as proposals, and there was no consolidated statement of what the game *is*. Three
changes are archived now and `openspec/specs/` holds 32 requirements across four capabilities.

**`project.md` and `config.yaml` described a different project.** They called TypeScript, Nx
and MobX "current", named **Koin** for DI (never adopted — zero occurrences) and a **Ktor**
server (replaced by a Durable Object per room). Anything reading them for context, human or
model, got the wrong architecture. Corrected, and Koin is now recorded as a decision against
rather than a task deferred.

**Several tasks were done but unticked, and three could no longer be done at all.** 3.3, 3.5,
6.4, 2a.2 and 2a.4 were verified complete in the tree. 5.6 and 6.7 depended on
`npm run recordings:generate` and `tools/replay-recording.ts` — both going with `legacy-web/`
(§1d) — so they are rewritten around what still exists: bot strength measured against a
committed self-play baseline, and the recording round trip run across *targets* (JVM, JS,
wasmJs) rather than across languages. That is the property that still matters once one engine
ships, because a `Long` is two `Int`s on Kotlin/JS.

**There was no desktop app**, though §6i step 1 told the maintainer to run one and listen for
the four sounds. `compose.desktop.currentOs` was a test dependency only: no `main()`, no
`application` block, no `run` task. There is now — `./gradlew :composeApp:run` — and it is the
fastest way to look at a UI change, with no emulator to boot.

### The order

**Tier 0 — done in this pass.** The desktop run target, the OpenSpec corrections, the
archiving, and the retired tasks above.

**Tier 1 — the release gate.** Nothing ships before these.

1. Analytics phases 1–4 (§6i step 3, `openspec/changes/add-live-analytics`)
2. Sentry (8.2 client, 9.9 server) — a separate pipe from analytics on purpose
3. The goldens, the sounds, and walking §6i end to end

**Tier 2 — the funnel and the players.** In this order, because each makes the next
measurable.

4. ~~**Deep links for invites.**~~ **Done in the app; the two association files are §1f.**
   `roomCodeFrom` parses an https link, a `vinto://` link, a bare path and a bare code, in any
   case and with whitespace, and refuses anything the registry could not have issued — the
   same `looksLikeRoomCode` the Worker applies, which moved to `shared/protocol` so the client
   and the room cannot disagree about it. Android has both intent filters, iOS has both
   handlers, the browser reads its own path, and an invitation now shares a link with the code
   underneath it for reading aloud. An invited player lands on the table with `ROOM_JOINED`
   recorded, which is the funnel step analytics 3.3 specified
5. ~~**Finish the translation.**~~ **Done** (§6h, eight slices). Every string a player sees now
   comes from `strings.xml`: `Narration`, `TableModel` and `TeachScript` return typed messages —
   `Say`, `Label`, `Ask`, `Detail`, `Explains`, `Teaches` — and the UI renders them. Five string
   literals are left in `shared/client` and none of them is a word a player sees. Adding
   `values-be/` or `values-uk/` is now a file and no code, which is what the exercise was for.
   Seven of the eight slices turned up a defect that had nothing to do with language
6. ~~**Local statistics.**~~ **Done.** `Stats` beside `Settings` in the same vault: rounds
   played, win rate, best hand, streak and best streak. On the device, no server, no privacy
   cost. A line under the wordmark once there is a round to show, and a way to clear it in
   Settings — personal data that cannot be cleared is data nobody agreed to keep.
   Two decisions worth knowing: **won means finished lowest**, not "took the round's points",
   because the Vinto caller takes +3 on a tie and a player told they won a round they did not
   finish lowest in would not believe the other three numbers either; and the win rate rounds
   *down*, because a number shown to somebody about themselves should not flatter

**Tier 3 — after the room is open.** Store releases (9.10), `docs/kotlin/ARCHITECTURE.md`
(8.4), a `CONTRIBUTING.md` that is about this repository rather than the retired one, and a
change of its own for retiring `legacy-web/` (10.1 gestures at it; the CI half is already
done).

## 1a. The Gradle build is the repository root

It was built under `kmp/`. It is not there any more: once the Kotlin build became the one
that ships, the tree was hoisted and the Next.js client was retired to `legacy-web/`. So
`./gradlew` runs from the root, `fixtures/` is a sibling of `shared/` rather than one
directory up, and `npm` commands run from inside `legacy-web/`.

What moved, and what to watch for if an old command fails:

| Was                            | Is                        |
| ------------------------------ | ------------------------- |
| `kmp/shared/*`, `kmp/worker`, `kmp/composeApp`, `kmp/iosApp` | the same names at the root |
| `kmp/gradlew`, `kmp/gradle.properties`, `kmp/config/detekt`  | the same names at the root |
| `kmp/keystore.properties`, `kmp/local.properties`            | the same names at the root |
| `apps/`, `packages/`, `package.json`, `nx.json`, `tools/*.ts` | under `legacy-web/`       |
| `fixtures/`, `docs/`, `openspec/`, `codecov.yml`              | unchanged, at the root    |

The JVM suites that read the corpus no longer count `..` from their module directory: the
root build injects the absolute path as the `vinto.fixtures` system property, and each test
falls back to a relative path only so an IDE can still launch it without Gradle. A future
move of the tree will not silently empty the parity gate.

`legacy-web/` is frozen, not dead. Its engine and bot are the other half of the parity gate,
and `fixtures/recordings` is generated from them — a rules change still has to land in both.

## 1b. Continuous integration

`.github/workflows/kmp.yml`, six checks, split by what each needs. It is the only workflow
that *checks* anything — the web client's three were removed with its CI (§1d). Beside it sits
`deploy-room.yml`, which checks nothing and publishes: `workflow_dispatch` only, so it never
runs on a push, and it is how the room is deployed without a desktop (§6i step 4).

| Check         | Runner | What it proves                                                                   |
| ------------- | ------ | -------------------------------------------------------------------------------- |
| `kmp-detekt`  | Linux  | Static analysis and formatting over every module and source set, `maxIssues: 0`   |
| `kmp-jvm`     | Linux  | The six shared modules' JVM suites — the corpus replay, the validator, the bot     |
| `kmp-web`     | Linux  | The same `commonTest` suites on Kotlin/JS and Kotlin/Wasm — 538 tests on each — and the Compose web client's own compile, which nothing else covers |
| `kmp-android` | Linux  | `assembleDebug`, plus the Compose suites headless (goldens excluded — see §6i)     |
| `kmp-worker`  | Linux  | The Kotlin/JS bundle, all twelve room gates, and the Worker's gzipped size budget  |
| `kmp-ios`     | macOS  | Simulator tests for the five Apple-target modules, and the framework Xcode embeds  |

**Where it stands.** All six are expected green: the four Compose failures below are fixed
(and the section is kept for the diagnosis, because three of them were not what the CI log
suggested). What follows was written when four of five were green. `kmp-android` compiles — the APK, and the Compose JVM test sources — and
fails on four of the 73 tests it then runs, which is the first time that suite has executed
anywhere. Those four are set out with their evidence in §1c; the rest of this section is how
the other four got green, which is worth keeping because most of it was CI finding things
that had been wrong for a while.

**What the first runs found.** `kmp-jvm` is green — the corpus
replays and the validator holds after the move, which is the check the migration rested on —
and so is `kmp-detekt` once its debt was baselined. Two jobs were red for reasons that
predate this work, both reproduced locally against the real compiled engine (build the
Kotlin/JS bundle, run the gate in Node) and both confirmed identical on a worktree of the
tree *before* the move:

- `gate-real-room` asserted that a peeked card stays visible to its seat *after*
  `FINISH_SETUP`. `projectView` says the opposite in as many words, and `PeekPrivacyTest`
  pins it: the setup peek is visible during setup and the room stops sending it once the
  round is on, because remembering your own hand is the game. The gate was the stale party;
  it now checks both sides of `FINISH_SETUP`.
- `gate-two-clients` compared the two sockets' event envelopes byte for byte. Since events
  started travelling with the view they left behind, those envelopes legitimately differ —
  each carries the view redacted for the socket it went to, which the very next assertion in
  the same gate checks. It now compares what happened (index, seat, actor, action, byBot)
  and asserts separately that each socket was sent the view for its own seat.
- `gate-sessions` never finished a round. Two harness gaps: it treated any `activeTossIn` as
  blocking rather than only one with `waitingForInput`, and it never fired the room's alarm —
  so a window waiting on the room stayed open, and the harness then jumped its clock to the
  next alarm, which is the thirty-minute session buzzer, discarding the round it was trying
  to measure. It now fires `onAlarm` at the moment `nextAlarmAt` names.

All twelve gates pass locally now — the nine in plain Node and the three through a real
`wrangler dev`, with the deployment bundle measured at 295 KB gzipped against a 3 MB limit.

**What composeApp's own targets turned out to be hiding.** `composeApp` was red for the two
source sets that had never been compiled anywhere — §6i says as much, that composeApp ships
"verified by `:composeApp:detekt` plus everything the shared modules prove" — so CI asked
what no machine had asked before, and got two answers:

- `:composeApp:jvmTestClasses`: `StringEscapeTest` read `Res.string.online_body`, a string
  that is not in `strings.xml` and never was. The test's other half — the one that greps the
  XML for `\'` — has been doing its job all along; the half that renders a string to prove
  `\n` still works had never been run. It reads `report_body` now, which is a real entry with
  a real `\n` in it.
- `:composeApp:compileKotlinIosSimulatorArm64`: five errors in `Net.ios.kt`, all import-shaped.
  `NSMutableURLRequest`'s mutating setters come from an Objective-C category, so Kotlin/Native
  exposes them as extensions that have to be imported by name; `sharedSession` is the
  opposite — a class property on the metaclass, so `import platform.Foundation.sharedSession`
  names nothing. Both are the kind of mistake only a compiler catches, which is the argument
  for the macOS leg existing at all.

Neither reproduces in the container this work was done in: androidx resolves from
dl.google.com, which answers 403 here, and there is no Mac. What made them fixable from here
was reading the runner's log rather than guessing — the steps are split into compile and
run/link so the step *name* says which half broke, and a `What the compiler said` step
repeats the `e:` lines at the end of a failed job so they survive being read from a phone or
a tool that shows only the tail.

**The detekt baseline.** The first CI run found seven findings that predate it — two
cyclomatic-complexity, a loop with too many jumps, a return count, a file name that does not
match its declaration, a file one function over the limit, and one dead private function.
They were confirmed present on the branch *before* the Gradle build moved, so none is
fallout from the move; the honest reading is that `./gradlew detekt` had not been run in a
while. They are listed in `config/detekt/baseline.xml` so the gate holds the line at today's
debt and fails on anything new. Fix one and delete its line. Do not regenerate the file to
silence a fresh violation — that is the one use that makes the baseline a lie.

Notes worth knowing before editing it:

- **JDK 17, not newer.** Every module pins `jvmTarget = 17` and none declares a toolchain,
  so a newer JDK fails with a Java/Kotlin target mismatch before it compiles anything.
- **`kmp-ios` is rationed.** macOS minutes bill at ten times the Linux rate, so it runs on
  pushes to long-lived branches, nightly, on demand, and on a pull request only when it
  carries the `ios` label.
- **The jobs do not `needs:` each other.** A detekt violation says nothing about whether the
  engine still replays the corpus; chaining them would turn four parallel jobs into one long
  one and hide the second failure behind the first.
- **Caches**: `gradle/actions/setup-gradle` restores the Gradle build cache and dependency
  jars, and writes it only from branch pushes — pull requests read. The macOS job also
  caches `~/.konan`, which is most of its wall clock on a cold machine.
- **Path-filtered.** A change under `legacy-web/` does not start the Kotlin jobs, and a
  Kotlin change does not start `legacy-web.yml` or the Playwright run. `fixtures/**` starts
  both, because a regenerated corpus is exactly the change that can break either engine.
- **wrangler is pinned** to an exact version in the workflow's `env`. `wrangler@latest` in CI
  means the day Cloudflare ships a change is the day this build breaks on an unrelated commit.
- **Each room gate is its own step**, named for what it asks. A loop over the nine would be
  shorter and would tell a reviewer only that "room gates failed"; the summary page should
  name the gate without anyone opening a log. `wrangler dev` is started once and left running
  across the three steps that need it — a background process outlives its step.

---

## 1c. Picking this up on a machine that can build composeApp

**All four of these are now fixed** — `:composeApp:jvmTest` is 79 tests, 0 failed. The
section is kept rather than deleted because three of the four were *not* what reading the CI
log suggested, and the difference between the guess and the finding is the useful part.

What each turned out to be:

| | §1c's reading | What running it showed |
| --- | --- | --- |
| 1 | Duplicated deck description | Correct. The pile keeps the count; the badge names its action |
| 2 | Chip is 51x28dp, add `heightIn(44)` | **Wrong.** Chips are 51x**64**; the `heightIn` was there and working. `boundsInRoot` is *clipped*, so it was reporting the visible sliver of a scrolled control |
| 3 | `canContinue`, or `Pace.CALM` unsettling the tree | **Neither.** "Back" sat 370px below the fold with bounds of zero, so `performClick` hit the window corner and reported success |
| 4 | The runner, or a wedged stage | The runner — but the fix was in the app: `LocalPacing` lets a caller with nobody watching drop the dwells. It also uncovered a real bug, below |

Two and three were the same trap twice: Compose clips `boundsInRoot` to what is on screen, so
a scrolled-out control measures as nothing and a click aimed at it lands on the origin —
silently, because a press on the corner of the window is a perfectly good press.

Four uncovered a genuine defect: `game.result?.takeIf { scoreOpen }` short-circuited on a
plain getter, so `scoreOpen` was never *read* and the scope never subscribed to it. Pressing
"See the score" set a flag nobody was watching. Normally the table is still animating and the
next frame draws it a beat late, which looks like pacing; on a table that has stopped, the
button is dead.

The original text follows, unchanged.

### What the container this was written in could not do

It could not compile `composeApp` at all, which is why the loop was push-and-read-a-log. The
denials, from the egress proxy's own report rather than from guessing:

| host | needed for | consequence when blocked |
| --- | --- | --- |
| `dl.google.com` (and `maven.google.com`, which redirects there) | AGP, androidx, Compose Multiplatform's Android artifacts | **no `composeApp` at all** — not the APK, not the JVM test suites |
| `cache-redirector.jetbrains.com` | Kotlin/JS and wasm toolchain fetches | no `wasmJsBrowserDistribution` |
| `download.jetbrains.com` | the Kotlin/Native prebuilt toolchain | nothing on Linux; Apple targets need macOS regardless |
| `github.com` beyond this repository | release assets, e.g. binaryen for wasm | the wasm target's own toolchain download |

Maven Central, the Gradle plugin portal, `services.gradle.org`, `nodejs.org`, npm and
`api.github.com` were all reachable, which is why the shared modules, the Worker bundle and
every Node room gate *could* be verified locally. androidx is not mirrored on Maven Central —
checked, 404 — so there was no way around it.

Two more things about that host, in case the next one is like it:

- **The JDK was 21, and the build wants 17.** Every module pins `jvmTarget = 17` and none
  declares a toolchain. The shared modules happen to compile on 21; the AGP-driven ones are
  where the mismatch bites, so this and the androidx allowance only pay off together.
- **The AGP-stripped copy is obsolete.** With `dl.google.com` reachable there is no reason to
  keep a second tree with `androidTarget()` deleted; run Gradle against the real one.

### The first hour, in order

```sh
./gradlew detekt                                    # the gate, all modules
./gradlew :shared:shapes:jvmTest :shared:engine:jvmTest :shared:bot:jvmTest \
          :shared:client:jvmTest :shared:protocol:jvmTest :shared:room:jvmTest
./gradlew :composeApp:assembleDebug                 # green as of CI run 10
./gradlew :composeApp:jvmTest                       # 73 tests, 4 red — the work below
```

Then the Worker, which needs the bundle built first:

```sh
./gradlew :worker:jsProductionExecutableCompileSync
cd worker/cloudflare && npx wrangler dev --port 8787 --var ROOM_OPEN:true &
node gate-real-room.mjs && node gate-sessions.mjs && node gate-lobby.mjs \
  && node gate-lifecycle.mjs && node gate-limits.mjs && node gate-registry.mjs \
  && node gate-room-codes.mjs && node gate-two-clients.mjs && node gate-engine-replay.mjs
```

All nine passed on the last run of this branch; they are here so a broken environment is
told apart from a broken change before anything else is believed.

### The four tests, with what CI actually said

`:composeApp:jvmTest` — **73 tests, 4 failed**. This suite had never run anywhere before CI
ran it, so none of these is a regression; they are the first reading of a gauge nobody had
looked at. Each is given here with the evidence and the cheapest next experiment, because a
machine that can run them will resolve in minutes what could only be reasoned about here.

**1. `HeaderControlsTest.theDeckCountExplainsItself` (`:25`)** — deterministic, and arguably
a real finding.

```
Expected exactly '1' node but found '2' that satisfy:
  ContentDescription contains 'cards left in the deck'
  1) Node #553 at (663,8)-(709,52)   ContentDescription = '[34 cards left in the deck]'  Text = '[34]'
  2) Node #595 at (306,299)-(362,377) ContentDescription = '[34 cards left in the deck]'
```

Both come from `header_deck_left`: `TableScreen.kt:284` (the header badge, which is the one
the test means to click) and `TableScreen.kt:834` (the draw pile on the felt). A screen
reader currently says the same sentence twice on one screen. Decide which of the two should
say it — the pile is the thing being counted, the badge is the thing that opens an
explanation — rather than making the test's matcher cleverer, which would hide the
duplication instead of settling it.

**2. `TouchTargetTest.everyRankChipSurvivesALargeFont` (`:74`)** — deterministic, and a real
accessibility finding.

```
AssertionError: 2 is 51x28dp, under a 44dp thumb
```

At `fontScale = 2f` a rank chip grows sideways and not down: `GameButton(compact = true)` has
its height from padding and a fixed `CompactLabel`, so the label can double while the box
cannot. `everythingSurvivesALargeFont` (the same sweep without the rank grid) passes, so it
is the chips specifically. The obvious fix is a `heightIn(min = 44.dp)` on the compact
button — and the obvious risk is the rail: fourteen chips at 44dp is a taller grid, and
`railHeight(screen)` is a *fixed* share of the screen with `CrowdedTableTest` and
`LandscapeTableTest` holding the felt to what remains. Run those two after touching it.

**3. `MenuUiTest.aSettingSurvivesLeavingTheScreen` (`:75`)**

```
Expected exactly '1' node but could not find any that satisfies:
  ContentDescription = 'Play'
```

The setting *did* reach the vault — the `assertEquals` four lines above it passed. What fails
is the home screen afterwards: no "Play" button. Two sibling tests in the same file walk
Home → Online → Back → `button("Play")` and pass, so the way back is not broken in general.
What is different about this one is that it is the only test that goes through **Settings**
and the only one that **changes a setting** before coming back. Cheapest experiments, in
order: assert on `onRoot().printToLog()` right after the Back click to see what the home
screen actually rendered; then check whether `canContinue` came back true (Home draws
"Continue"/"New game" instead of "Play" when it does — `HomeScreen.kt:258`), and whether
`Pace.CALM` reaching `LocalReducedMotion` / the card-fan `animateTo` at `HomeScreen.kt:174`
leaves the tree unsettled.

**4. `FullGameUiTest.aWholeRoundIsPlayedOutOnTheScreen` (`:46`, the wait at `:76`)** — the one
that may be the runner rather than the code.

```
ComposeTimeoutException: Condition still not satisfied after 120000 ms
```

It waits for "See the score" to appear after a whole round has been animated through
`CardStage` at `Pace.BRISK`. Two possibilities and they are easy to separate on a machine
that can run it: the stage genuinely never drains (a bug), or two cores and software
rendering simply cannot animate a round in two minutes (raise `END_TIMEOUT`, and say in the
comment that the number is a CI budget rather than a claim about the app). Run it locally
first — if it passes in twenty seconds on a real machine, it is the second.

### What is left after those four

- The goldens, the sounds, the deploy and the four-human table: §6i, unchanged. None of them
  is unblocked by a network.
- 9.9 (Sentry on the Worker, a load test) and 9.10 (store releases).
- The stale checkboxes in phases 2, 3 and 6 of `tasks.md`.

## 1d. Retiring the web client's CI

The web client under `legacy-web/` is being deleted. The CI that served it has gone ahead of
the tree, because it was failing on every pull request for a reason nothing on the Kotlin
side can fix:

```
npm error code EBADENGINE
npm error notsup Required: {"node":"^24"}
npm error notsup Actual:   {"npm":"10.9.8","node":"v22.23.2"}
```

`legacy-web/package.json` asks for Node 24 and all three workflows installed Node 22, so
`npm ci` died before a single test ran. Both **Test & Coverage** and **E2E Tests** were red on
PR #184 for that and nothing else. Bumping the runner to 24 would have made a frozen app's
suite green for as long as it takes to delete the app.

Removed, all three of them solely the web client's:

| File | What it did |
| --- | --- |
| `.github/workflows/legacy-web.yml` | lint, unit tests and the Codecov upload |
| `.github/workflows/playwright-e2e.yml` | the browser matrix and the accessibility scan, against the deployed Vercel URL |
| `.github/workflows/nx-migration.yml` | weekly Nx upgrade PRs; already reduced to a manual trigger |

And with them: the two npm hooks in `lefthook.yml`, the flags and components in `codecov.yml`
(whose statuses are now off — nothing uploads coverage, and a status with nothing to compare
against blocks or hangs rather than skipping), and the two README badges that pointed at a
deleted workflow and an upload that no longer happens.

Nothing in the Gradle build reads `legacy-web/`. The one mention is a comment in
`composeApp/build.gradle.kts` saying where the card art came from; `composeApp` carries its
own copies under `composeResources/drawable`.

### What deleting it actually costs

One thing, and it is worth being precise rather than reassuring about it. `fixtures/` is
committed at the repository root — 51 files, the 50 recordings and the PRNG vectors — and it
is read by `RecordingParityTest`, `PrngVectorsTest` and the generated constant in
`shared/shapes/build.gradle.kts`. So the Kotlin side keeps replaying the corpus after the
deletion, and `kmp-jvm` is unaffected.

What goes is the ability to *regenerate* it. `legacy-web/tools/generate-recordings.ts` is what
produced those recordings from the TypeScript engine, and `replay-fixtures.test.ts` is what
proved that engine still replays them. After the deletion the corpus is a frozen artefact:
still a real gate against the Kotlin engine drifting, no longer evidence that two
implementations agree today, and impossible to extend. That is the correct trade once the
TypeScript engine stops being shipped — but it is a trade, and the parity language elsewhere
in this document should be read with it in mind.

## 1f. BLOCKED — what cannot be finished from a container

Everything here has been attempted and stopped for a reason that is not a missing decision:
no credentials, no hardware, or no data yet. Each line names what would unblock it, so the
person who has that thing can pick exactly their share up rather than re-deriving the list.

Nothing in this section is waiting on design. Where a choice had to be made to get as far as
being blocked, it was made and recorded in the relevant `design.md`.

| Task | What it needs | How far it got |
| --- | --- | --- |
| analytics 1.1 — confirm Workers Analytics Engine allowances | The Cloudflare dashboard, signed in to the account that owns the Worker | The binding, the writer and the absent-binding path are all built and gated; what is unconfirmed is the *plan's* real writes/day, read allowance and retention. `design.md` §A1 carries published figures and says in as many words that they are not measured |
| analytics 5.1 — dashboard route | The three secrets in DEPLOYMENT.md §7 (`ANALYTICS_TOKEN`, `ANALYTICS_ACCOUNT_ID`, `DASHBOARD_KEY`), and a deployment with traffic | **Built**: `GET /counts?key=…` renders the six queries server-side, and `gate-dashboard.mjs` covers its refusals, its escaping and the queries' shape in 51 checks. What cannot be covered here is a single number — the WAE SQL API is the one part of Analytics Engine `wrangler dev` does not emulate. Not ticked |
| analytics 5.3 — Web Analytics on the Pages project | The Cloudflare dashboard for the `vinto` Pages project | A per-site switch that makes Cloudflare inject its own beacon; there is nothing in this repository to change and nothing here can verify it. DEPLOYMENT.md §7b is written for somebody who does not do this for a living. The page it injects into **did not exist** until this pass — see the `index.html` note in §7 |
| analytics 5.4 — revisit sampling and the cost model | A week of real traffic against a deployed room | Arithmetic on data that does not exist. It is the reason phase 5 is not a release gate |
| Deep links — verifying them | The two association files hosted on `vinto.kupalinka.app`, each naming a real credential | The app half is built and tested: intent filters, both iOS handlers, the browser path, and `roomCodeFrom` with 5 tests. What cannot be done here is publish **`/.well-known/assetlinks.json`** (needs the release keystore's SHA-256 fingerprint — `keytool -list -v -keystore …`) and **`/.well-known/apple-app-site-association`** (needs the Apple team id and bundle id, served as `application/json` with no extension). Until both exist the https links open the website instead of the app; the `vinto://` scheme works today and is why it is there |
| §6i step 1 — the eight goldens | A maintainer's machine, and a human looking at the images | `ScreenshotTest` writes them and CI deliberately does not run it: a fresh runner would write its own and assert nothing. Generated PNGs are not committed from here on purpose |
| §6i step 1 — the four sounds | Ears, and `./gradlew :composeApp:run` | The desktop target exists now, which is the part that was missing |
| §6i step 4 — the deploy, and flipping `ROOM_OPEN` | `wrangler login`, and the deliberate decision to open the room | Everything the flip guards is built and gated locally through `wrangler dev` |
| §6i step 5 — two devices, then four humans | Hardware and four people | Cannot be scripted; that is what 9.7's second verification is for |
| 8.2 — a native crash on iOS | Xcode, and a decision on the Sentry KMP SDK | The reporter is installed at process start on all four targets now (§6m) and catches a Kotlin exception reaching the top. A signal or a Swift trap is what an SDK would add, against weight in a 3.7 MB wasm bundle; `design.md` §A9 has it flagged rather than settled |
| Crash reporting, end to end | A DSN, and a build that carries it | The pipe is built and gated (`CrashReporterTest`, `CrashInstallTest`, `CrashReportTest`). What has never happened is a report arriving in a real Sentry project: the DSN is a build input now (`-Pvinto.sentryDsn=`), and nothing here has one |
| 9.10 — store releases | An upload key, store accounts, and a signed build | `assembleRelease` signs with the upload key when `keystore.properties` exists and with the debug key when it does not, so the path is exercised without the secret |
| `kmp-ios` beyond CI, and any `commonMain` change trusted on Apple | macOS with Xcode | The macOS leg of CI covers compilation; §5's warning stands — a `commonMain` change that breaks iOS cannot fail on a non-Mac host |
| 7.1 — the animation layer on real hardware | A physical phone, and a Mac to look at the simulator | The decision is made and the layer is built and running on an Android emulator. It has never been *watched* on a real device, and on Apple it is compiled and simulator-tested by `kmp-ios` but not looked at by a person |
| 8.1 — a release job on tags | An upload key, a Play track, an Apple developer account | No longer blocked on CI existing — six checks are green (2.3). `assembleRelease` already signs with the upload key when `keystore.properties` exists and the debug key when it does not, so the path is exercised without the secret. R8 and everything iOS sit behind the same accounts |
| 8.2 — the iOS privacy manifest and permissions review | Xcode | The reporter itself is done, breadcrumbs included: a crash report now carries the deal's `gameId`, the round and the turn, which is the same address the room's Sentry reports carry |
| 9.9 — a load test with 100 concurrent rooms | A deployment, and `wrangler login` | The rest of 9.9 landed: Sentry reports carry the deal's `gameId`, the round and the action index into it, and recordings are filed per round. A load test cannot go against `wrangler dev` — it enforces no CPU limit whatsoever (§6d), so it would measure the laptop rather than the platform |
| 4.8 — the corpus on an Android emulator | A machine that can resolve androidx, and an emulator in CI | **The iOS half is done**: `kmp-ios` runs `:shared:client:iosSimulatorArm64Test`, so since 6.7 a whole game is generated and replayed through the real harness on Kotlin/Native every run. The Android half wants an instrumented `connectedAndroidTest` reading the corpus from an asset, which needs `androidx.test` — dl.google.com answers 403 here (§1c) and androidx is not on Maven Central, so it cannot be compiled in this container at all, only pushed and hoped for. An hour's work on a machine that can build `composeApp` |

## 2. Prerequisites

| Tool        | Version used  | Notes                                                                                                      |
| ----------- | ------------- | ---------------------------------------------------------------------------------------------------------- |
| JDK         | 17 (Temurin)  | Every module sets `jvmTarget = 17` and none declares a toolchain, so Gradle must **run on 17** — a newer JDK fails with a Java/Kotlin target mismatch |
| Gradle      | 8.14          | Via the committed wrapper — do not install system Gradle                                                   |
| Node        | 24            | For the TypeScript side and `vite-node` tools                                                              |
| Xcode       | latest stable | **macOS only**; needed for the iOS targets                                                                 |
| wrangler    | 4.x           | `npx wrangler` — no global install needed                                                                  |
| Android SDK | platform 36   | For `composeApp`'s Android target. Point Gradle at it via `sdk.dir` in `local.properties` (gitignored) |

```bash
git clone <repo> && cd vinto
./gradlew --version              # bootstraps Gradle 8.14 on first run
(cd legacy-web && npm ci)        # the retired TypeScript workspace

# Android only: tell Gradle where the SDK is (local.properties is gitignored).
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

## 3. Module map

| Module          | Targets                                                 | Purpose                                                                                                    |
| --------------- | ------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| `shared:shapes` | jvm, js, (iosArm64, iosSimulatorArm64 on macOS)         | Types + `Prng`. The port starts here. Its tests run on every target above.                                 |
| `shared:engine` | jvm, android, js, wasmJs, (iOS on macOS)                | `GameEngine.reduce`, toss-in and scoring utils, the replay harness. Partly ported — see §6b.               |
| `shared:bot`    | jvm, android, js, wasmJs, (iOS on macOS)                | MCTS decision service, coalition planner and `BotRunner`. Reads only what a seat may see.                   |
| `shared:client` | jvm, android, js, wasmJs, (iOS on macOS)                | `GameSession` with both lives: `LocalGameSession` (solo, no socket — see §6d) and `RemoteRoom`/`RemoteGameSession` over the wire. |
| `shared:protocol` | jvm, android, js, wasmJs, (iOS on macOS)              | The wire, declared once: `ClientMessage`/`ServerMessage`, the room-facing types, `ProtocolJson`. See `PROTOCOL.md`.               |
| `shared:room`   | jvm, js                                                 | The room and registry cores, moved out of the worker so the JVM can test them. Envelope builders, recordings, pacing.             |
| `worker`        | js                                                      | Cloudflare Worker + `Room` Durable Object: `@JsExport` delegates over `shared:room`, under the thin JS shim in `worker/cloudflare/`. |
| `composeApp`    | android, wasmJs, (iosArm64, iosSimulatorArm64 on macOS) | Compose UI — one `commonMain` for all three clients: the solo game, the lesson, and the online lobby + table. |
| `iosApp`        | —                                                       | Xcode project embedding `composeApp`'s `ComposeApp` framework. macOS only.                                 |

The full intended layout is in design D1. Modules are added as they are ported rather than
scaffolded empty.

## 4. Commands

```bash
# --- Kotlin (run from the repository root) ---
./gradlew :shared:shapes:allTests             # PRNG parity on every target (JVM, JS, iOS sim)
./gradlew :shared:shapes:jvmTest              # just the JVM leg, when iterating
./gradlew :worker:jsNodeProductionRun         # PRNG self-check (prints the gate number)
./gradlew :composeApp:wasmJsBrowserDistribution   # build the Compose web bundle
./gradlew :composeApp:assembleDebug           # Android APK
./gradlew :composeApp:installDebug            # ...onto a connected phone or emulator
./gradlew :composeApp:assembleRelease         # release APK; debug-signed unless §6f says otherwise
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64   # just the iOS framework
./gradlew build                               # everything available on this host

# --- iOS app (run from iosApp/) ---
# The Xcode build invokes Gradle itself, via its "Build Kotlin framework" phase.
xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build

# --- Cloudflare Worker (run from worker/cloudflare/) ---
# Build the Kotlin bundle first: the shim imports it out of build/compileSync/.
(cd .. && ./gradlew :worker:jsProductionExecutableCompileSync)
npx wrangler dev --port 8787 --local          # local workerd; deploys nothing
node gate-two-clients.mjs                     # platform gate 2a.3
npx wrangler deploy --dry-run --outdir /tmp/w # measure the real Worker bundle

# --- TypeScript (run from legacy-web/) ---
npm test                                      # all 5 projects
npx nx run-many --target=typecheck --all --skip-nx-cache
npm run recordings:replay -- fixtures/recordings    # parity gate via CLI
npm run recordings:generate -- --games 5 --seed 1   # ~75 s per game
```

## 5. iOS bring-up

iOS targets are declared behind a host check in `shared/shapes/build.gradle.kts`, so
they activate automatically on macOS.

**Why this was deferred until a Mac existed**: Kotlin/Native cannot build Apple targets on
Windows at all. It is a hard toolchain limitation, not a configuration gap.

### Step 1 — first compile ✅ done

`./gradlew :shared:shapes:build` compiled `iosArm64` and `iosSimulatorArm64` for the first
time. `Prng` is pure Kotlin with no platform APIs and needed **no changes**, so the
toolchain is sound and nothing in the shared code was Windows-shaped.

### Step 2 — parity on iOS ✅ done

`PrngVectorsTest` now lives in `commonTest` and runs on JVM, JS/Node and the iOS simulator —
6 tests on each, all green. Verify with `./gradlew :shared:shapes:allTests`.

Getting the fixture onto a target with no filesystem needed a decision. The options were to
bundle `vectors.json` as a Kotlin/Native resource, or to embed it. **Embedding won**, but
not by transcribing the numbers into Kotlin — a hand-copied table can drift from the file
TypeScript reads, and the parity test would then pass while proving nothing.

Instead, the Gradle task `:shared:shapes:generatePrngVectorsSource` reads
`fixtures/prng/vectors.json` and emits it as a Kotlin string constant. So:

- there is still exactly **one** shared file, and it is a declared task input, so changing it
  regenerates the constant — no second copy to keep in sync
- the JSON is embedded **verbatim**; the test parses the same bytes with the same serializer
  it used before (a round-trip check confirmed the embedded literal is byte-identical to the
  file)
- the wiring was verified negatively: perturbing one `finalState` in the fixture by 1 made
  the iOS test fail, so the check is not vacuous

Resource bundling was rejected because Kotlin/Native test binaries have no straightforward
resource access without an extra plugin, and it would have bought nothing over this.

### Step 3 — `composeApp` on Android and iOS ✅ done

`composeApp` now has `androidTarget()`, `wasmJs` and (on macOS) both iOS targets, with the
UI in `commonMain` and one small entry point per platform:

| Platform | Entry point                                  |
| -------- | -------------------------------------------- |
| Android  | `androidMain/.../MainActivity.kt` + manifest |
| iOS      | `iosMain/.../MainViewController.kt`          |
| Web      | `wasmJsMain/.../Main.kt`                     |

`iosApp` is a plain Xcode project whose "Build Kotlin framework" phase shells out to
`./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`, so building in Xcode builds the
Kotlin side too. `ContentView.swift` wraps `MainViewController()` from the exported
`ComposeApp` framework — renaming either side breaks the other, and nothing checks that for
you.

Verified by **running**, not just compiling: the same `App()` renders on the iOS simulator
("Running on: iOS 26.5") and on an Android emulator ("Running on: Android 34"). The
`platformName()` `expect`/`actual` exists precisely to make that visible — it is the cheapest
proof the multiplatform wiring is real before storage and clocks depend on it.

Two things that will bite anyone repeating this:

- Compose Multiplatform's Android artifacts are AndroidX, so `android.useAndroidX=true` is
  required in `gradle.properties`; without it the build fails at `checkDebugAarMetadata`.
- A headless emulator (`-no-window`) returns an all-black `screencap` under the default GPU
  mode. Use `-gpu swiftshader_indirect` if you need a screenshot; the app itself was fine,
  only the framebuffer was.

### Step 4 — revisit the host check ✅ done

Left conditional, but no longer silent. Making the Apple targets unconditional would just
turn a non-Mac build into a hard toolchain failure, which helps nobody. Instead both
`shared/shapes` and `composeApp` now `logger.warn` when they skip the iOS targets, so a host
that cannot compile them says so rather than quietly building less than you asked for.

The underlying hazard is unchanged and worth restating: a `commonMain` change that breaks
iOS cannot fail on a non-Mac host. Build on macOS before trusting a shared-code change.

## 6a. How the ported `shapes` is verified

Three layers, weakest to strongest:

1. `Sha256Test`, `CanonicalJsonTest`, `PrngVectorsTest` — unit tests in `commonTest`, so they
   run on **all five targets** (jvm, android, js, wasmJs, iOS simulator). They pin the rules
   that the corpus cannot reach: the `botMemory` exclusion is never present in a real
   recording, so it is asserted directly.
2. `RecordingParityTest` (JVM only, it reads the 4.5 MB corpus from disk) — decodes each of
   the 50 recordings' `finalState` into the Kotlin model with `ignoreUnknownKeys = false`,
   so an unmodelled field is an error rather than a silent drop. It also round-trips all
   **13,900** recorded actions through `GameActionSerializer`, comparing canonical forms.
3. The same test re-encodes that state, canonicalises and hashes it, and compares against the
   `finalStateHash` **TypeScript wrote**. One number covers lossless decode, correct
   optional-versus-nullable handling, a byte-identical canonical form, and SHA-256 agreeing
   with WebCrypto. It cannot pass by accident.

Confirmed non-vacuous: reversing the canonical key sort fails it, dropping `turnActions`
from the exclusions fails it, and forcing an unset optional to encode as `null` fails the
action round-trip. (Dropping `botMemory` does **not**, which is why layer 1 exists.)

The corpus reaches 17 of the 25 action types. The other eight, and the `rank: 'A'` variant
of `SELECT_ACTION_TARGET` that appears in no recording, are pinned by layer 1 instead —
which is the general shape of this: the corpus proves agreement on what real games do, and
the unit tests cover what they happen not to.

One deliberate check worth keeping: the optional-field rule is carried by the
`@EncodeDefault(NEVER)` annotations alone, not by `VintoJson`'s configuration. Flipping
`encodeDefaults` to `true` leaves every parity test green, so a call site that builds its own
`Json` cannot start emitting `"declaredRank":null` where TypeScript writes nothing.

**Deviation from design D1**, recorded deliberately: canonical JSON, SHA-256 and `Prng` live
in `shared/shapes` rather than a `shared/recording` module, because that is where TypeScript
keeps them and the port is file-for-file (D3). Revisit when the `GameRecording` model lands.

## 6b. The engine port, and how it is being driven

The harness came first, not last. `CorpusReplayTest` replays TypeScript's recordings through
the Kotlin engine and compares the canonical state hash **after every action**, so a wrong
handler is localised to one action rather than showing up as "the final state differs".

While the port was in progress this ran as a ratchet that named the frontier on every run,
so the next handler to write was never a guess. It is now a **hard gate**: all 50 recordings,
all 13,900 actions, plus final-state verification.

Confirmed non-vacuous — deleting one line of knowledge tracking from the Jack swap (the owner
must _lose_ knowledge of a blind-swapped position) fails it.

### The validator needed a different kind of test

The corpus cannot check `ActionValidator` at all: every action in it was legal when recorded,
so a validator that returned `Valid` unconditionally replays all 13,900 identically. Replaying
with the real validator live therefore proves one direction only — that nothing legal is
rejected.

The other direction is the one that matters, since this is the anti-cheat boundary D9 rests on.
`ValidatorImpersonationTest` gets it from the corpus anyway: replay every recording and, at
each step, re-attribute the action that genuinely happened to **every other player at the
table**. Each is an attempt to act out of turn in a position that actually arose.
**18,066 attempts, none accepted.**

Rule-specific cases the sweep cannot reach — the coalition may not target the Vinto caller, a
failed toss-in ends participation for the round, setup peek limits — are posed against corpus
states in `ValidatorRulesTest` rather than hand-built ones: a fabricated state proves a branch
is reachable, a real one proves the rule bites where it matters. Deleting either rule fails its
test.

**Why the handlers mutate.** The TypeScript handlers deep-copy the state and then mutate it
freely. Rewriting each into immutable `copy()` chains would be better Kotlin and a worse
migration — the parity gate cannot tell a faithful restructuring from a subtly wrong one. So
`MutableGameState` is a working copy that handlers mutate exactly as their TypeScript
counterparts do, and `reduce` freezes it on the way out. `reduce` stays a pure function; the
mutation never escapes the call. Rewriting handlers idiomatically later is safe precisely
because the gate holds the behaviour still.

## 6c. Hosting on kupalinka.app

Vinto is hosted alongside the portfolio games in `~/sources/gulnya/games-portfolio-brief.md`.
Two hostnames, two deploy targets:

| host                       | what                                            | how                                            |
| -------------------------- | ----------------------------------------------- | ---------------------------------------------- |
| `vinto.kupalinka.app`      | the Compose/Wasm client                         | Cloudflare **Pages** project `vinto`           |
| `vinto-room.kupalinka.app` | the room Worker + Durable Object, and `/replay` | `wrangler deploy` from `worker/cloudflare` |

The Worker gets its own hostname rather than a path under `vinto.kupalinka.app`, because that
host is a Pages project and layering a Worker route over a Pages custom domain is a precedence
puzzle nobody should re-derive during an incident. It is still **same-site**, so the client's
CSP needs one `connect-src` entry on its own site. `px.kupalinka.app` is a separate Worker for
the same reason.

**Compose for Web is an exception to the portfolio convention**, which says plain DOM over
`:engine` and never Compose. Taken deliberately, with the 3.7 MB measurement in hand; the
reasoning is in design **D1a** and must not be copied to another game without reading it.

### What the exception does not excuse

These are properties of the zone and of the visitor, not of the module shape, and they apply to
`vinto.kupalinka.app` exactly as they do to every other game:

- **Every script the page reaches carries a content hash.** The `kupalinka.app` zone's Browser
  Cache TTL _overrides_ a weaker origin `Cache-Control`, so under fixed names a stale script
  keeps naming a wasm binary the next deploy replaced — a 404 and a dead page, not a stale one.
  This has taken portfolio sites down before; treat it as a hard invariant, not a preference.
- **A newly-deployed asset fetched at its canonical URL before the edge has it** returns the
  Pages SPA fallback: `index.html`, **200**, `text/html` — then cached `immutable` for a year by
  a path-matched `_headers` rule. Content-addressing makes that permanent rather than momentary.
  Probe with `?cb=`, and never point a headless browser at a fresh deploy.
- **Usage counting from the loader**, not the bundle, so a visitor whose browser cannot run
  WasmGC still counts. No cookie banner, an opt-out control, GPC/DNT honoured.
- **The §3b gate**: responsive at 1440px as well as 380px, keyboard-complete, focus and scroll
  surviving re-render, both themes at WCAG AA, `prefers-reduced-motion` honoured.

### The shared machinery does not currently reach Vinto

The first three of those live in `~/sources/gulnya/web-template/`, which exists precisely
because copying them by hand went wrong: the brief records Niva shipping a deploy script with
**no chain verification at all**, printing a green tick over a dead site, months after Vodar's
grew that check. Nothing was wrong with either file — "copy this verbatim" is an instruction to
a person, and people copy things once.

`sync.mjs` mirrors a `web/` module layout. Vinto's web build is `composeApp` with a Gradle
root one directory down, so it cannot participate as-is, and hand-copying `content-hash.js` and
`web-deploy.sh` into this repo would make Vinto the **third** copy — exactly the outcome the
brief warns about, and it names that as the moment to stop copying and move the verification
into versioned tooling instead.

Unresolved on purpose: it means editing shared tooling that two shipped games depend on, which
is not a change to make casually or as a side effect of hosting Vinto. Until it is resolved,
`vinto.kupalinka.app` has no deploy script — which is survivable only because the client is not
ready to publish anyway.

### The web client is not ready to deploy

`composeApp` is still the platform-gate tap counter. Publishing `vinto.kupalinka.app` today
would publish that. Phase 7 builds the real UI.

## 6d. Deploying the engine, with no UI

The Worker carries the real engine and exposes `POST /replay`: send a `GameRecording`, get
back `ok` or the exact action that diverged. That is enough to verify the engine on a real
deployment before any UI exists.

```bash
cd worker/cloudflare
(cd ../.. && ./gradlew :worker:jsProductionExecutableCompileSync)
npx wrangler dev --port 8787 --local
node gate-engine-replay.mjs            # 50/50, 13,900 actions

# against a deployment
npx wrangler deploy                    # needs `wrangler login` first
GATE_URL=https://vinto-room.kupalinka.app node gate-engine-replay.mjs
```

**The first deploy needs zone permissions**, not just Workers ones: `custom_domain: true`
makes wrangler create the `vinto-room.kupalinka.app` DNS record in the `kupalinka.app` zone.

### A deployment today is a self-test, and the code enforces that

`ROOM_OPEN` defaults to `"false"`, and a WebSocket upgrade against a closed room is refused
with **503** and the reason. This is a gate in `index.mjs`, not a note here, because the
consequence of forgetting is a deployed Durable Object accepting _any_ action from _any_
client — `ActionValidator` permits everything, and design D9 puts server-side validation at the
centre of the anti-cheat model. Flip it to `"true"` in the same commit that lands the
validator, never before.

What a deployment does answer:

| endpoint          |                                                                             |
| ----------------- | --------------------------------------------------------------------------- |
| `GET /health`     | `{"ok":true,"service":"vinto-room","engine":"kotlin","roomOpen":false}`     |
| `POST /replay`    | replays a `GameRecording` through the real engine; bodies over 1 MB refused |
| WebSocket upgrade | 503, with the reason                                                        |

`/replay` is a pure function of the posted document — it holds no state and mutates nothing —
which is what makes it safe to expose while the validator is missing. The 1 MB cap is there
because it is public and CPU-bound at roughly 1 ms per action; the largest recording in the
corpus is 141 KB.

Tearing it down is `npx wrangler delete`, so this is a cheap thing to try and an easy thing to
undo.

### What the first real deployment taught, that local could not

Deployed 2026-08-19 to `vinto-room.kupalinka.app`. All 50 recordings and 13,900 actions replay
on the live edge. Three things surfaced that `wrangler dev` had no way to show:

- **`/replay` belonged in the Durable Object, not the Worker.** A plain Worker gets ~10 ms of
  CPU per invocation; a Durable Object gets 30 s per request. Replaying one game costs ~250 ms,
  so production answered `error code: 1102` — the exact limit design D9 cites as the reason the
  room is a Durable Object at all. `wrangler dev` enforces no CPU limit whatsoever, and the
  production limit is applied on a rolling average, so single requests passed while a batch did
  not. D9 was right; the endpoint was in the wrong place.
- **A plain `GET /?room=anything` created a Durable Object and wrote it to storage**, for any
  name a stranger cared to invent, and read it back. Locally that was a reasonable inspection
  aid for the 2a.3 harness. The `ROOM_OPEN` gate now sits above it, so a closed deployment
  creates nothing and discloses nothing. The only thing that had changed about the code was
  that it was on the internet.
- **Propagation is not atomic, and it caught me twice.** Verifying seconds after `wrangler
deploy` returned the _old_ behaviour and sent me chasing a second bug that did not exist; then
  one probe seeing the new behaviour did not mean every edge node had it. Poll until the
  behaviour changes, then keep checking. Same hazard the portfolio brief documents for Pages
  deploys, in a different costume.

Run `GATE_URL=https://vinto-room.kupalinka.app node gate-engine-replay.mjs` after any deploy.
It backs off on 503 and **counts** the retries, so throughput pressure stays visible instead of
being quietly absorbed.

Why this is worth having rather than trusting the JVM gate: Kotlin/JS represents `Long` as a
pair of `Int`s and uses a different serialiser backend, so passing on the JVM does not imply
passing on Cloudflare. It now passes on both.

**What must be true before a deployment takes real client input.** `/replay` is safe to
expose — it is a pure function of the posted document. The WebSocket room is not yet, but the
reason has changed: `ActionValidator` is now ported in full and is the boundary design D9
depends on, so what is missing is that the `Room` Durable Object still runs placeholder logic
instead of calling `GameEngine.reduce`. `ROOM_OPEN` stays `"false"` until it does.

Deploying needs a Cloudflare account and `wrangler login`; `wrangler deploy` should be a
deliberate decision rather than a side effect of a build.

## 6e. The bot, and what a self-play gate is for

The bot is ported in full, and the verification is worth explaining because it is not the one
the other phases use.

The engine had a corpus: 50 recorded games with per-action hashes, so "did the port work" has
an exact answer. **A bot has no such thing.** Its output is a decision, and two reasonable
bots disagree constantly without either being wrong. Demanding decision parity would have
meant transcribing every heuristic literally including its bugs, and would still not have
produced it — MCTS is stochastic, and the two implementations sample different random streams.

So the requirement was set differently: *the bot need not follow the TypeScript exactly, but
it must follow the rules.* That is checkable. `SelfPlayGateTest` plays whole games with four
Kotlin bots through the real `GameEngine`, and every action a bot proposes goes through
`ActionValidator` before it is reduced — the same boundary a Durable Object runs, so anything
rejected here would be rejected in a live room.

Three things it asserts, each for a different failure:

| assertion            | catches                                                             |
| -------------------- | ------------------------------------------------------------------- |
| every action is legal | a bot that cheats, or that gets stuck holding an action the engine refuses |
| every game reaches `scoring` | a game where each action is legal but two states hand back and forth forever |
| some game ends on a Vinto call | an endgame that is unreachable in practice, so games only end when the deck dries up |

It earned its keep immediately, finding five defects that no unit test would have:

- **Memories outlive the hands they describe.** A toss-in removes a card and renumbers
  everything after it; the memory keeps its old index. A shrunken hand therefore "remembered"
  a card past its own end, and the move generator offered it as a target the engine rejects.
- **A tossed-in Jack or Queen was a dead end.** The validator allows `selecting` — where a bot
  sits while resolving a tossed-in action — for `CONFIRM_PEEK`, `DECLARE_KING_ACTION` and
  `SELECT_ACTION_TARGET`, and forbade it for exactly these two swaps. A bot could choose both
  targets and then had no legal move at all. The same hole is in
  `legacy-web/packages/engine/src/lib/action-validator.ts`.
- **Cached action plans could go stale**, since they are read a ply deep in the search tree.
- **Target selection answered the wrong question** once the engine had already committed a
  card: "would I rather swap?" is no longer on the table at that point.
- **The search could not see the deck run out** — `deckSize` was hardcoded to a full deck.

**One dead end is deliberately left open.** The draw pile is refilled when a turn ends with
one card on it, but a forced draw or a wrong-declaration penalty takes a card *without* ending
a turn, so it can reach zero — and a turn that starts with no deck and nothing takeable on the
discard has no legal move. Refilling at zero as well looks like a one-word fix and is not:
`reshuffleFrom` advances `rngState` whether or not it moves anything, so recording
`selfplay-moderate-18` diverges at action 322. Corpus parity is worth more than closing a rare
dead end; the engine is untouched and `BotRunner` reports the position rather than proposing an
illegal draw.

**Three validator rules are deliberately stricter than TypeScript's**, all around the final
round, all invisible to the parity corpus because no recorded game ever hits them:

- **The coalition may not target the Vinto caller even before a leader is chosen.** The
  TypeScript guard (and the first Kotlin port of it) only fired once `coalitionLeaderId` was
  set, but choosing a leader is optional — every recording happens to pick one immediately
  after the call, which is the only reason the hole never showed. The caller's protection now
  starts at the call itself.
- **The Vinto caller cannot toss in.** `getAutomaticallyReadyPlayers` always treated the
  caller as done and the docs said the caller "may not participate", but nothing rejected the
  action; now `PARTICIPATE_IN_TOSS_IN` from the caller is invalid.
- **Vinto during a toss-in window belongs to the turn owner.** After a toss-in queue drains,
  `currentPlayerIndex` can rest on the last toss-in actor, who then passed `requireTurn` for
  `CALL_VINTO` — an out-of-seat call the UI and `BotRunner` never produce but the engine
  accepted, and whose advance path could hand the round straight back to the caller. The
  validator now requires the caller to be `activeTossIn.originalPlayerIndex`'s seat.

**`DECLARE_CARDS` is Kotlin-only**, like `END_ROUND` above it: coalition table talk, where a
member claims out loud what they believe their own cards are (see `VISIBILITY.md`). It never
appears in a parity recording and its state (`PlayerState.declaredCards`) serialises as
absent until first used, so all fifty recordings hash unchanged. A Kotlin recording that
*does* contain it replays fine here — `Replay.kt` is action-agnostic — but cannot be fed to
the TypeScript replayer, which has no such action.

## 6. Decisions already made — do not silently reopen

| Decision                                                                    | Where recorded                            |
| --------------------------------------------------------------------------- | ----------------------------------------- |
| Cloudflare Durable Object per room; no JVM server                           | design D1, D9                             |
| Bots run server-side (a client would need other seats' hidden cards)        | design D9, online-multiplayer spec        |
| Compose Multiplatform for web, 3.7 MB gzipped accepted                      | design D1 risks, `PLATFORM-GATE.md`       |
| One bot engine (v1/MCTS); v2 deleted for reading hidden hands               | `docs/bot/BOT-ENGINE-DECISION.md`         |
| Canonical hash excludes history + `botMemory`, includes `opponentKnowledge` | `RECORDING.md` §4                         |
| Every game is exactly 4 players                                             | deterministic-engine spec                 |
| Bots call Vinto when hand is fully known and worth ≤ 0                      | `legacy-web/packages/bot/src/lib/vinto-call-rule.ts` |
| Bot verification is rule-following, not decision parity                     | §6e, tasks 5.5/5.6                        |
| One decision service **per bot**, not one shared across seats               | `BotRunner`; TypeScript wipes memory each turn |

## 6d. Single-player runs on the device

A solo game creates **no room, no token and no socket**. `shared:client` holds a `GameSession`
interface that a local game and an online one both implement, so a screen cannot tell which it
has — which is what keeps the free single-player mode free to host, rather than a Durable
Object running three MCTS searches a turn for one person.

`LocalGameSession` is the engine and `BotRunner` in-process. It reads the same redacted
`PlayerView` the server sends, validates through the same `ActionValidator`, and enforces the
same seat boundary from the same `GameAction.actorId` the Durable Object uses. A local game
that let the UI act for a bot would be teaching the UI a habit that fails online.

The claim is gated rather than asserted: `NoNetworkGuardTest` plays a whole round with a
`SecurityManager` installed that throws on any connect, listen or accept, and proves the guard
bites — three deliberate calls that must fail — before trusting the round that follows.

Two things that gate turned up, both faithful ports of TypeScript behaviour that only a UI was
keeping shut, and both fixed:

- the validator had no **phase** gate, only turn and sub-phase checks, so `DRAW_CARD` passed
  during setup and again after scoring. Never reachable from a button; entirely reachable from
  a socket.
- `PEEK_SETUP_CARD` validated the player it *named* rather than the one acting, so one player
  could spend another's setup peeks.

## 6f. Putting it on a phone

The solo game is playable on Android today — `./gradlew :composeApp:installDebug` and it is on
the device. What was missing was everything around the game rather than in it, and four of
those are now done. None of them is the Play release (task 8.1 proper): there is still no CI,
no upload key, no track.

**A launcher icon.** The web app's own orange V (`legacy-web/apps/vinto/public/favicon.png`), regenerated
into the three shapes Android has asked for over the years by
`tools/make-launcher-icons.py` — adaptive for API 26+, the legacy square/round pair for the
24–25 the app still supports, and a monochrome layer for themed icons on API 33+. The generated
PNGs are committed; nothing at build time runs the script. It is the same mark as the browser
deliberately: a different icon for the phone would make it a different game to anybody who has
played both.

**Both orientations** (task 7.6, done). The screen's shape picks the arrangement in
`TableLayout.forScreen(width, height)` (`CardScale.kt`): portrait keeps the rail under the felt
on a fixed share of the height; landscape stands the rail beside the felt on the same fixed
share of the *width*, because the portrait rail's own minimum height is most of a rotated
phone's screen. The felt is the same four-sided table in both — only the join moves, and the
final-round banner rides at the head of the side rail where the felt has no height to spare.

Landscape spans three very different machines, and two numbers change with the screen rather
than the platform: the cards **step up** to a third size (`Grand`) on the felt heights only a
tablet or desktop has — a portrait tablet lands on the same step — and the felt's width is
**capped** (by aspect, and absolutely) with the felt-and-rail group centred in what remains,
so a desktop table keeps a table's shape on the app's dark surround instead of stretching the
seats to opposite horizons. A rotated phone has no width to spare, so there the felt still
takes everything beside the rail.

The Android manifest no longer locks orientation; iOS always allowed rotation (it squeezed the
portrait design until this landed) and the browser was always free. `LandscapeTableTest` holds
the rotated phone to the same bar `CrowdedTableTest` holds the upright one to, and holds the
desktop window to the cap and the centring.

**A window theme of its own** (`values/themes.xml`). It was inheriting
`Theme.Material.Light.NoActionBar`, which meant dark status-bar icons over a dark rail and a
white flash before the first composition. `Theme.Vinto` is dark Material with the rail as its
window background, so the bars carry the light icon set by inheritance rather than by
overriding a per-API flag, and the cold-start frames are the colour of the app.

**A release variant that assembles anywhere.** `assembleRelease` signs with the upload key
named by `keystore.properties` when that file exists, and with the debug key when it does
not. The fallback is the point: a release build that fails on a missing secret is one that goes
untested until the day it has to work. A debug-signed release APK installs and plays; it cannot
be published, and cannot be upgraded in place by a properly signed build later, because Android
treats a change of signing key as a different app.

To sign it properly, create the key once and write `keystore.properties` (gitignored, and
it names the keystore rather than containing it):

```bash
keytool -genkeypair -v -keystore ~/keys/vinto-upload.jks -alias vinto \
  -keyalg RSA -keysize 4096 -validity 10000
```

```properties
storeFile=/Users/you/keys/vinto-upload.jks
storePassword=...
keyAlias=vinto
keyPassword=...
```

## 6g. The menu, the settings and the lesson

The app opened straight onto a title and two buttons, because single player was the only
thing there was. It now has a front door, and three things behind it.

**Home.** A fan of five real cards from the game's own deck deals itself in behind the
wordmark, and under it a panel holding the mode that is finished: single player, its
difficulty on show rather than buried, and one button to a table. Below that, the three
things that are not a game — online, the lesson, the settings.

The arrangement follows what the premium card apps have converged on rather than what a
settings-first Android app does. Two ideas were worth taking. Marvel Snap's UI team put it
as *the cards take precedence in the visual hierarchy and the interface exists to highlight
them*, which is why the menu is made of the deck instead of illustrated with it. The poker
lobbies (Zynga, PokerStars) are built so the table everybody came for is one tap away and
nothing is between you and it — hence the difficulty chips sitting *in* the play panel
rather than behind a settings screen.

**Online is a button that plays.** It was a dialog explaining what existed and what did not,
back when this app's half was the missing one; phase 9 built that half, and the button now
opens the real thing: a name, a room code, a public/private choice, and a browser for the
rooms that chose to be listed.

Three things were added once it was possible to sit at a real table over a real network, all
three of them about the gap between touching something and it having happened:

- **A spinner of the app's own** (`theme/Progress.kt`), because Material's ring on felt reads
  as a form submitting. It is drawn like everything else here — a track and a brighter sweep,
  round-capped, in the ink of whatever it sits on — and it honours reduced motion by *not
  moving*: the still version is a complete ring at the sweep's weight, which is visibly a busy
  indicator and never a frozen animation. `GameButton` takes `busy`, which swaps its label for
  one and swallows its own taps; "create room" pressed twice is two rooms.
- **Local before global.** A pending seat spins on *that seat* (`RemoteRoom.pendingSeats`,
  cleared by the next lobby broadcast or a five-second timeout), the connection badge spins
  instead of showing a settled dot while it is still trying, a move on the wire says so under
  the heading, and only a first load of the public list takes the middle of the screen — a
  refresh keeps the list and puts a small one beside the title, because taking a list away
  from somebody reading it is the rudest thing a lobby can do.
- **An invite, not a code to transcribe.** The lobby shows the code monospaced and spaced out,
  with Share and Copy over the `shareText`/`copyToClipboard` seam — which now has real
  implementations on the desktop clipboard, the browser's Web Share and clipboard, and iOS's
  pasteboard, where three of the four used to answer `false`. Where a platform can do neither,
  the code is still on the screen and a line says to read it out.

**Settings** are four choices, each written as what it *does*:

- **Bots**, the difficulty, shared with the home panel.
- **Pace** — calm, steady, brisk — a single multiplier over every duration in the animation
  layer, so the movements and the pauses keep their proportions at either end of the dial.
  It exists because the right speed is not the same for somebody learning the game and
  somebody on their twentieth round.
- **Theme**, because a phone's night setting is not always the one you want at a table.
- **Haptics**, and see below.

They live under their own key in the same vault as the saved game, deliberately not inside
`SavedGame`: a preference outlives the round it was set in, and abandoning a game must not
reset a speed the player has already decided they dislike.

**How to play is a real round, on a deck somebody arranged.** Not a page of rules, and not a
scripted walk with every button but one disabled — a tutorial that refuses your moves teaches
the sequence rather than the game, and has to say no the first time you deviate.

The design came out of a session with the `fable` model against the repo (`VINTO_RULES.md`,
`SCENARIOS.md`, the client sources); what shipped follows it closely. Five parts:

- **A stacked deal.** `initializeTeachingGame(deck)` takes a deck order instead of a seed, and
  refuses anything that is not a permutation of the real 54 cards — so the lesson cannot deal a
  hand that could not have been shuffled. `TeachingDeal` writes the order down: a 7 and a Joker
  to peek at, an 8 to throw in later, a plain 4 to draw first, then a Queen, a King and finally
  a Joker on the last turn. Seed search was considered and rejected: the constraints are joint
  over a dozen named positions, and a seed found today would be silently invalidated by the
  next bot or engine change. The recording contract is untouched, because `GameRecording`
  carries `initialState` in full and `replayRecording` starts from it.
- **A director for the bots.** The deck says what a bot *draws*, not what it does. A
  `BotDirector` on `LocalGameSession` may name a bot's move before the search is asked;
  whatever it names still passes `ActionValidator`, and a refused move falls through to MCTS —
  a script that has drifted costs the lesson its shape, not its playability. It does two
  things: bots put drawn cards down rather than swapping them into hand, and, once the round
  has taught what it can, **Don calls Vinto** — so the final round, the coalition and the
  scoring are played rather than described. Left alone a bot will not call inside a short
  round: the rule wants eight rotations, a fully known hand and a total of zero or less.
- **A coach derived from the position.** `lessonFor(view, table, taught)` is a pure function:
  it reads the table and says what is in front of the player. That is what lets every legal
  move stay legal — deviate and it simply talks about wherever you got to. Ordered talk beats
  are the exception (the object of the game, the tour, the Vinto call, the coalition, the
  scoring), because explaining scoring before the round is scored is not explaining.
- **A pointing hand.** One white outlined arrow, at one thing, from just outside it: a card, a
  seat plate, a rank chip, a button, the deck badge, the log box, the "?". White because every
  other colour on this table already means something. It points down from above by default —
  pointing up from below sits it on the next rank chip in the grid, naming the wrong card — and
  from inside the left end of a full-width button, where it cannot cover the button below.
- **The deck first, card by card.** Before the table is toured and before a card is dealt with,
  the lesson holds up every rank in the order they get harder: the plain 2–6, the two that look
  at one of yours, the two that look at one of theirs, then the three that nobody works out by
  watching — the Jack that swaps blind, the Queen that looks first, and the King. The King gets
  three beats, because it is the one nobody works out and it has two separate ideas in it.
  First what it does. Then **what you name is what you get to play** — name a 7 or an 8 for a
  look at one of your own, a Jack for its blind swap with *you* choosing both cards (which is
  how a Joker you have spotted comes to you and your worst card goes the other way), a Queen to
  look at two before trading. Then **whose card you name**: the named card leaves that hand, so
  your own 10 comes off your total and an opponent's Joker comes off theirs. Neither idea is
  derivable from the rules text; both come from reading `handleDeclareKingAction`.
- **Cards explained as they are met, with the card.** The first time a rank becomes visible,
  the coach gives its name, value and action in `CARD_CONFIGS`' own words — beside the actual
  picture, dealt from the same art the table uses. The help sheet's gallery does the same, as
  the web app's card reference already did: a player who learned "Q" from a list still has to
  match it against a picture on the felt, and showing the picture skips that step.

The director has one more job than "make the bots play their parts": the seat immediately
before the player **draws rather than takes**, so that an unused action card is still on the
pile when the player's turn comes round and the second way to start a turn can be shown. A bot
taking it first is correct play, not a fault, which is why the lesson teaches that rule in
words either way and points at it when the round allows. The Vinto call is timed on
`turnNumber`, which counts *turns* and not rotations — the first version called it on turn 4,
which is the third bot's **first** turn, and ended the lesson before the player had taken a
second one.

It also makes **one bot throw a card into a toss-in window**, once, the first time a bot is
holding a match it has actually seen. The window is the one moment in Vinto that belongs to
the whole table at once, and a player whose window only ever contains themselves learns it as
"a prompt I dismiss"; when it happens the coach names whoever did it. "Has actually seen" is
the bots' own rule rather than a convenience — guessing costs a penalty card and bars you from
the rest of the round, so a bot tossing a card it had not read would be demonstrating bad play.

The coach also answers, once and without reproach, the first time somebody presses a button
other than the one being pointed at. That press is a player quietly testing whether this is a
real game or a rail, and it deserves an answer.

Three things learned by tapping through it on a phone, all of them invisible in code review:
the coach's box is a **fixed** height while it talks, so "Go on" does not move under a thumb
that has twelve beats to press; the cards sit directly under the title rather than after the
words, so a long paragraph cannot push them below the fold; and `**markdown**` in the copy
reaches the player as literal asterisks, because Compose's `Text` is not a renderer.

Two things are deliberately not free: the lesson runs at no less than **calm** pace whatever
the setting says, and **Call Vinto is hidden until a bot calls it** — the one tap that ends the
lesson before it starts, cannot be undone, and means nothing yet to the person pressing it.

**The coach floats above the rail rather than living in it.** It began inside the control
panel, which was wrong in a way only a phone shows: the panel became as tall as a lesson and
the felt as short as whatever was left, so four hands, two piles and three name plates ended up
crushed into a third of the screen with the side seats' cards re-flowing into rows. A tutorial
that deforms the game it is teaching is teaching a different game. It is now a card over the
top of the felt, and the table underneath keeps exactly the layout it has in a real round.

It is also **shut while the game is being played** — one line and the progress dots, one tap to
open — because everything under it is something the player has to see and touch. A talk beat
opens it, since the table is held for it anyway.

The old note about the panel's reserved height, kept because it is why the coach was ever put
there:
Stacking it above cost the felt 150 dp and the side seats' hands re-flowed into rows — the
lesson was being taught on a table that was not the one being learned. It is bounded and
scrolls inside its own box, so a King's fourteen rank chips plus a three-line prompt plus a
lesson cannot squeeze the felt out of existence.

**A wrong toss-in shuts you out of that card, not the round.** It used to be the whole deal in
every phase, which is what the rules note in `VINTO_RULES.md` recorded — and it meant one wrong
read on the second seat's discard cost a player every window until scoring, including windows
opened by cards they could not have known about when they guessed. Reported by the product owner
from a real round: the toss-in is the one moment that belongs to the whole table at once, and a
player shut out of it for ten minutes stops touching it. The **final round** keeps the long
version, because there the coalition plays one hand against the caller and there is no later
window to earn it back in.

It is a rule in `ActionValidator` and `projectView`, not in state: `isBarredFromTossIn` chooses
between `activeTossIn.failedAttempts` and `roundFailedAttempts` by phase. `roundFailedAttempts`
still records every failure for the whole round — it is history, and it is inside the canonical
hash — so the frozen parity corpus is untouched, and `CorpusReplayTest` stayed green without a
fixture being regenerated. A validator that refuses *less* can never reject a recorded action.
`TossInBarTest` pins both halves, including the case that prompted it: failing on one bot's
discard does not bar you from the next bot's.

**Haptics.** Three kicks and no more: something touched, a move committed, a rule bitten —
that last only for the hand it happened to, since a buzz for a bot's penalty is a buzz for
something that is not your problem. Off is one setting away, which is what keeps the three
that remain meaningful.

**Settings are reachable from the table, not only from the front door.** The header's gear sits
beside the "?" and the bug, and it carries the way back with it: `Screen.Settings` holds the
screen it was opened from, so closing it returns to that exact table — the same `LocalGame`,
mid-round, nothing re-dealt and no socket re-opened. It is the same for the lesson and for an
online room, and the system back button follows the same route.

The reason is one setting: **pace** is the thing somebody wants to change *while* a round is too
slow to sit through, and it lived where changing it meant abandoning that round. Nobody pays
that price; they put the phone down instead. Theme and haptics are the same shape of want.
`HeaderControlsTest` pins both halves — the gear opens the settings, and coming out of them
lands back on the table rather than at the front door, which is the half that is easy to get
wrong and impossible to see in a diff.

**Back works.** `SystemBack` is an `expect`/`actual` around Android's `BackHandler`; the other
targets no-op and use the on-screen button. Without it, back from the settings screen closed
the app, which looks exactly like a crash.

### What the lesson covers, against the rules

Audited beat by beat against `docs/game-engine/VINTO_RULES.md`:

| Rule | Where it is taught |
| --- | --- |
| Objective — lowest hand wins | opening beat |
| Four players, five cards each, face down | opening beat |
| Peek at two of your own, once | setup lesson, pointed |
| Every rank's value and action (2–6, 7·8, 9·10, J, Q, K, A, Joker) | eight card beats, each holding up the cards |
| King: names any card, right takes it out of that hand and gives you its action | three beats, with the worked example |
| Option A — draw from the deck | turn lesson, pointed |
| Keep it, throw it, or play its action now | keep-or-throw lesson |
| Declare the rank you put down; right plays its action, wrong costs a card | declaration lesson, pointed at a rank you have seen |
| Option B — take an unused action card off the pile and play it at once | turn lesson (in words; pointed when the round offers it) |
| Toss-in: anybody may match the rank; wrong costs a card and bars you | toss-in lesson, plus a bot demonstrating it |
| Calling Vinto at the end of your own turn | the "you can call it too" beat, after a bot has called |
| Final round — one more turn each | the call beat |
| Coalition — best single hand counts, caller's cards untouchable | the coalition beat |
| Scoring — +3 / −1 / level counts as the caller's | the scoring beat, pinned by a test |
| A session is rounds; 5 / 3 / 2 game points by rank | the session beat |
| The deck running out and the pile going back into it | help sheet ("what the table is telling you") |

Re-audited against the **official composite PDF** (the 4-page rules document) rather than only
against the repo's markdown, which turned out to be wrong in places. Three more rules were
added to the lesson as a result: the coalition **may confer and pool what they know**, the
**?** is the reminder card the boxed game ships one of per player, and a session is played to
**a clock agreed beforehand** before the 5 / 3 / 2 game points are awarded.

Every difference between the engines and the official text has now been decided, and the table
recording those decisions is at the foot of `docs/game-engine/VINTO_RULES.md`. Three closed as
"the PDF is loose and the engines are right" (Jack/Queen targeting, tossing in on your own turn,
an Ace off the discard); one was a real bug and is fixed in **both** engines — a wrong toss-in
no longer clears itself after one lap of the table.

That bar's *lifetime* has since been decided again, against play rather than against the text:
it is the window you guessed wrong in, and the whole round only in the final round. The reasoning
is under **Haptics** above and in `VINTO_RULES.md`, whose decision table records the reversal
rather than quietly replacing the old line.

Two things worth knowing about that table:

- The scoring line **was wrong** until it was audited: it said a caller who finishes lower
  takes +3 "while the rest take nothing", when the rules and `calculateRoundPoints` both charge
  the others a point each — nothing is what a *tie* costs them. `TeachScriptTest` now pins all
  three outcomes, because a tutorial that teaches a scoring rule incorrectly is worse than one
  that skips it: the player believes it.
- An earlier pass recorded a deviation here that was not one: the repo's markdown said the deal
  places a card face up to start the discard pile, and the engine does not. The **official PDF
  agrees with the engine** — "The Discard Pile is formed by the first card played or discarded"
  — so the markdown was the bug, and it has been corrected.

**A hand too wide for one row wraps, and steps down a size first.** Five cards is the deal and
not the limit — a wrong guess, a wrong toss-in and an Ace each add one, and only the end of the
round takes any away — so eight in front of a player is an ordinary way to be losing. Eight did
not fit, and what the line did about it was slide them over each other until they did. Past about
seven that stops reading as a hand of cards: the backs are a repeating pattern, so the seam
between two overlapping cards is invisible, and a player with eight of them cannot count their own
hand, let alone aim at a card in it. It was reported from a phone, with a screenshot, which is the
only way this kind of thing is ever found.

It now does what the web client did (`legacy-web/.../horizontal-player-cards.tsx`: `flex-wrap`,
with the card size chosen from the count) — and both halves are needed. Wrapping alone was tried
before and taken out, because a second row of full-size cards doubles the seat's height and
squeezes the felt until the side seats have a single card's height to lay nine cards in. So the
cards step down one size first, to `CardScale.crowded()`, and one size only: a hand that resized
by a few percent every time a penalty card landed would be a table that never looks the same
twice.

The step is **the tap floor**, 44dp, and that is not an arbitrary choice of a smaller number.
`CardFace` reserves 44dp of footprint whatever size it draws the picture — `TouchTargetTest`
measures it — so shrinking the art below that buys no room at all; it just draws a small card in
a box that did not shrink. That is also the whole reason wrapping is the answer rather than more
shrinking.

The three seats opposite keep overlapping. Their cards are counted rather than read, and the
felt's *width* is the scarcest thing on a phone, so a second column costs more than it buys.
`CrowdedTableTest` holds both: nine cards a seat, every card on screen and tappable, and your own
hand with no card lying over another.

**The rail is a fixed share of the screen** — `railHeight(screen)`, a third of it, clamped
between 240 and 300 dp — and the felt is exactly what remains. Not a floor the contents can
push past, which is what it was: a King's fourteen rank chips arrived and the table shrank,
they went and it grew back. Animating that made it a slide rather than a jump, which is a nicer
way to move something that should not move at all.

What adapts now is the panel's contents rather than its height: the box of recent moves stands
aside when a rank grid needs the room, and the column scrolls as a last resort so a large
system font cannot push a button off the bottom. Measured on the device across five panel
states — a toss-in prompt, a two-line prompt with a two-line log, bots playing, a turn, a drawn
card — the felt's bottom edge stayed at the same pixel in every one.

**The jump.** The felt took what the control panel left

## 6h. Words, and where they live

Every string the **UI module** says is now in
`composeApp/src/commonMain/composeResources/values/strings.xml`. A translation is a file
beside it — `values-be/strings.xml`, `values-uk/strings.xml` — and nothing else changes, which
is what the `translate-game` skill expects to find.

Two rules for that file, written at the top of it as well: name a string for what it *says*
rather than where it sits, and never build a sentence out of two of them. Word order is not
universal, and a translator handed half a sentence cannot fix the half they were not given.

Accessibility descriptions went in too. A screen reader announcing "a face-down card" in a
Belarusian game is the same failure as an untranslated button.

The choice labels moved off the enums. `Difficulty.serialName` is a **wire value** — it is in
every saved game and every recording — and capitalising it to put on a button worked exactly as
long as the app was English. `Labels.kt` maps each enum to a resource instead.

### What was still English, and why it was harder — now done

Roughly two hundred strings remained in `shared/client` when this was written, and they could
not simply move: that module has no Compose, and its copy was not written as sentences to
translate but *assembled* from grammar. All of it is converted now (slice 8 above); the
original reasoning is kept because it is why the answer was a typed message rather than a
string table.

- `Narration.kt` conjugates verbs — `youForm`/`theyForm`, "You draw" against "Raph draws". In
  Belarusian or Ukrainian that is not a suffix swap; it is a different sentence.
- `TableModel.kt` builds prompts and button labels with interpolation ("A 7 went down — toss in
  a match?"), and the pointer keys off those labels.
- `TeachScript.kt` is the lesson, which is mostly prose.
- `CardConfig.kt` in `shared/shapes` carries the card names and descriptions, ported verbatim
  from TypeScript so both clients teach the same game.

The fix is the same in each case and it is **not** a string table in a non-UI module: those
functions should return a *typed message* — the id and its arguments — and the UI should render
it from resources. `Table.prompt: String` becomes something like `Table.prompt: Say`, and the
tests get better rather than worse, because asserting `Say.YouDrew(SEVEN)` says what is meant
where asserting an English sentence says what it currently reads.

That is what landed. The app is no longer half-translated: menus, settings, the score sheet,
the help sheet, the spoken descriptions, the table's prompts, the move log **and the lesson**
all follow the phone's language. What is not yet translated is a different thing and an easier
one — there is still only a `values/strings.xml`, so adding `values-be/` and `values-uk/` is
now a file each and no code at all, which is what this whole exercise was for.

### How big it actually is, measured

Counted rather than estimated, because "roughly two hundred strings" is the kind of figure
that turns out to be either 40 or 900 and the difference decides whether this is one sitting
or several:

| | |
| --- | --- |
| Human-facing string literals | **238** — `Narration.kt` 26, `TableModel.kt` 68, `TeachScript.kt` 144 |
| Functions returning a `String` a person reads | 36 |
| Call sites in `composeApp` that render one | 17 |
| Test assertions written against English text | ~24 |
| Entries already in `strings.xml`, for comparison | 183 |

So the description was accurate — this is about as big as it says, not secretly ten times
bigger. The work is real all the same: a `Say` hierarchy with something over a hundred cases,
238 new resource entries, and 36 functions plus their call sites and tests rewritten.

**Do it as vertical slices, not as one commit.** Each leaves the app compiling and the tests
green, and the tests get *better* as it goes: `assertEquals(Say.DrewKnown(You, SEVEN), …)` says
what is meant where an English sentence only says what it currently reads.

### Slice 1 — `Narration.kt`, done

The move log. `narrate` returns a `Say` instead of a sentence, `GameSession.log` is
`StateFlow<List<Say>>`, and `composeApp`'s `said()` renders one from `strings.xml`. 26
resource entries, and the pattern works.

Three things the slice settled, recorded here so the next two do not re-decide them:

- **A person's name is a `Speaker`, not a `String`.** `Speaker.You`, `Speaker.Named(nickname)`
  and `Speaker.Nobody`. It has to be a type because the difference is *grammatical* rather than
  cosmetic — it picks the verb — and a renderer that had to compare a string against the word
  "You" would be broken by the first translation. `Nobody` exists for the things that happen
  to the table rather than to a player, like the deal ending.
- **Conjugated lines are written out twice, in full.** `log_draw_you` and `log_draw_they`, not
  a stem plus a suffix. English makes the suffix trick look reasonable; Belarusian and
  Ukrainian want two different sentences, and a translator handed the fragment `"draws"` cannot
  fix a half they were never given. Only the four verbs that actually conjugate need the pair
  — the past-tense lines ("took", "tossed in", "called Vinto") take one string with a name in
  it.
- **A rank is not translated.** It travels as `Rank` and renders as its symbol. A `7` is a `7`
  in every language this will ever ship in, and a card's *name* is a different string that the
  help sheet already owns.

### Slice 2 — `Choice.label`, done, and it was carrying a bug

The buttons. `Choice.label` is a `Label` — a closed type — rather than a String, and
`Target.Button` names one the same way.

**This slice was not really about translation.** Two things read that label: the UI, to draw a
button, and `TeachScript`, to decide which button the lesson should point at. Identifying a
control by the English it happens to display is a coupling no test sees and no compiler checks,
and it had already failed twice:

- `label.startsWith("Take the")` — the model produces `"Use Queen"`. So the beat that teaches
  the *second way to start a turn* never fired, on a lesson whose director goes to deliberate
  trouble to leave an unused action card on the pile for it (§6g). Silent since the code was
  written.
- `label.contains("Pass")` — no button has said "Pass" for some time. Dead clause in
  `tossWindow`, harmless only because two other disjuncts did the work.

Both are now type checks. A translation cannot break them and neither can a rewording, which
is the property that matters more than the 13 strings this moved.

Two halves of one lookup to keep in step: `ChoiceButton` marks a button with `keyOf(label)` and
`Pointer` looks it up with the same function. When those disagree the arrow points at nothing
and says nothing — which is exactly how the missing beat stayed missing.

The tests converted with it, and got better: `it.label == Label.CallVinto` instead of
`it.label == "Call Vinto"`, and `send(Label.DrawCard)` instead of `send("Draw")`.

### Slice 3 — `Table.prompt`, done

The line above the buttons. `Ask`, 30 cases, rendered by `asked()` from 30 resource entries.

A third piece of English assembly went with it: the toss-in prompt joined its ranks with
`" or "`, hard-coded in a module with no way to translate it. `Ask.TossIn` carries the ranks
and the renderer joins them with `ask_or`.

And the thing slice 1 had to leave in the UI came back to the model, better than it left.
Dropping a log line that only repeats the prompt used to compare two *rendered strings* — which
worked by coincidence, `Ask.YouDrew` and `Say.DrewKnown` being different types that happen to
produce the same English. `Ask.echoedBy(Say)` says the relationship instead, and survives a
language where those two sentences differ.

### The boundary: `shortDescription` is data, and cannot be translated

Found while deciding what to do about `CardConfig`, and it is the non-obvious constraint on
this whole piece of work.

`CardConfig.shortDescription` is copied into **`Card.actionText`** when a toss-in resolves
(`TossInHandlers`). `Card`s live in `GameState`. The canonical hash excludes exactly three
fields — `turnActions`, `roundActions`, `botMemory` — so `actionText` is **inside the hash**
that all 50 fixtures pin against the value TypeScript computed. Translate those four strings
and every recording diverges.

So the line is drawn there:

| Field | | |
| --- | --- | --- |
| `shortDescription` | **data** | Reaches `Card.actionText`, hashed. Must stay exactly what TypeScript wrote |
| `name`, `longDescription`, `helpText` | presentation | Translatable; they never enter a state |

`CardCopyIsDataTest` in `shared/shapes` pins the four strings, the shape of the rule (an
action card has text, a plain one does not), and the exclusion list itself — so if `actionText`
ever *does* leave the hash, one test says the constraint is lifted rather than fifty saying
something is wrong.

The right fix is that `actionText` should never have been a string in the state: it is derivable
from the rank at the point of display. That is a rules-shaped change needing the corpus
regenerated, which §1d says is on its way to being impossible — so it is recorded here as a
deviation rather than queued as a task.

### Slice 5 — `Table.detail`, done

Nine cases, and the King's borrowed-action line was built from
`getCardShortDescription` — the hashed field. It now takes `longDescription`, which is the
same information said at greater length and is presentation rather than data.

One improvement came free. `worthSaying` counts how often a player has seen a hint, to fade it
after the second or third time, and it keyed that count on the hint's **words**. It keys on the
message now: the count survives a translation, and two hints that merely read alike in English
no longer share a tally.

### Slice 6 — `Table.help`, done. `TableModel` is finished

The "?" text. `Explains`, six cases, and the card paragraph's *order* — name, value, what it
does, how to do it — is now the resource's business rather than the model's. A language that
wants the value first can have it.

Worth noting how the test moved rather than went away. `everyStateExplainsItself` asserted on
the assembled paragraph ("starts with Queen", "contains swap", "contains 10"), which was the
right claim in the wrong module once the model stopped assembling anything. It split: the
model's half asserts *which* explanation and about which card, and `CardHelpTest` in composeApp
asserts the words. Converting an assertion into something weaker and calling the tests green is
the easy mistake when a refactor moves a responsibility, and it is worth being deliberate about.

**`TableModel.kt` is now fully converted** — labels, prompts, details and help.

### Slice 7 — `Chapter`, and nine strings that were never drawn

The first cut into `TeachScript`, and it turned up a third thing that was not a translation
problem.

`Chapter` carried a `label: String` — "The table", "Your two peeks", nine of them — and
**nothing rendered it**. Meanwhile the progress dots those words were written for had no
accessible name at all: nine unlabelled circles conveying how far somebody had got by *colour
alone*, which is precisely the information a screen reader cannot get.

So the words moved to `Labels.kt` (where `Difficulty`, `Pace` and `Theme` already keep theirs)
and the dots now use them: "Your two peeks — covered", "Calling Vinto — still to come".
`ChapterDotsTest` keeps them connected, so a tenth chapter cannot be added as a silent dot.

Three for three: every slice into this area has found something that had nothing to do with
language — two dead English matches, a hashed field about to be translated, and now display
text that was never displayed. Assembling sentences in a module that cannot render them turns
out to be a reliable marker for code nobody has looked at.

### Slice 8 — the lesson itself, and §6h is finished

**Done.** 28 beats, ~135 literals, one commit, exactly as the design said it had to be — `Lesson`
holds `title: String?` and `body: String` as *fields*, so changing their type moves every call
site at once and only the resource entries can be added incrementally.

What is left in `shared/client` after it: **five string literals, none of them words a player
sees** — two storage keys, an internal animation id, and two `require`/`error` messages for
whoever is debugging. The module says what happened and the UI says it in words, everywhere.

Four things worth keeping from doing it.

**The name `Beat` was taken, and that is not a triviality.** `Choreography.kt` has had a `Beat`
since the animation layer was built and it means something completely different — a step in a
card's movement. The design in this section named the new type `Beat` and it would have
collided on the first compile. It is `Teaches`, which reads correctly beside the five types it
joins (`Say`, `Label`, `Ask`, `Detail`, `Explains`), all named for what the module is *doing*.

**The design's fourth step was wrong, and the section it was written in says why.** It proposed
turning `Taught.talked: Set<String>` into a `Set<Teaches>` "while you are there". That breaks
for exactly the two beats this section had already identified as irregular: `TossIn` and
`VintoCalled` carry arguments, so two instances of the same beat are *unequal* — a toss-in
window with one thrower and the same window with two would be two entries in the set, and the
lesson would say itself twice. The id is the beat's identity; the arguments are what varies
within it. `Teaches.id` stays, and the eighteen existing `talkId` strings stay with it, so a
lesson somebody is halfway through still means what it meant.

**Two resource conventions caught it, and one of them has its own test already.**
compose-resources does **not** process `\'` — the backslash is drawn on the screen — which
`StringEscapeTest` exists to catch and did. And its format arguments are positional (`%1$s`),
not bare `%s`; a bare one renders literally. Both were caught by tests rather than by review,
which is the argument for the split below.

**The tests split, and the split is the careful part.** `TeachScriptTest` asserted things like
"the body contains +3" — the right claim in the wrong module once the script stops assembling
words. The easy, wrong move is to let those claims go and call the suite green. So the script's
half now asserts *which* beat and in *what order* (a fact about the script), and every content
claim moved intact to `LessonCopyTest` in composeApp, which renders the resource and asserts on
it — the same split `CardHelpTest` got in slice 6. That includes the one that exists because
the copy got a rule **wrong** once: a caller who finishes lower takes +3 *and the others each
lose one*, where nothing is what a tie costs them.

`TeachingRoundTest` now collects prompts and details separately (`asked` and `said`), which is
the shape the remaining slices want anyway.

`Say` lives in `shared/client` rather than `shared/protocol` because it is not wire — nothing
sends one anywhere, and putting it in the protocol module would imply a compatibility promise
that does not exist.

## 6j. Finding a table, getting somebody to it, and saying so while you wait

Three things that the online client had no answer for, and one bug found while giving it one.

### Every wait now says where it is

The app had no progress indicator of any kind — a `grep` for one found nothing — so a tap
that crossed a network looked exactly like a tap that missed. `theme/Progress.kt` draws one
the way the rest of the table is drawn: a track at low opacity with a brighter round-capped
sweep riding it, in the ink of whatever it sits on. Material's own ring on felt reads as a
form submitting.

**Reduced motion is honoured properly**, which means "no movement, same information": the
still version is a *complete* ring at the sweep's weight, visibly a busy indicator rather
than a frozen animation, and the animated branch is a separate composable so the frame clock
is never started for somebody who asked for stillness.

Local before global, wherever the wait belongs to one thing. Every call that crosses the wire,
and what it draws:

| what is waiting | what it draws |
| --- | --- |
| `POST /rooms` — opening a room | the button, busy: label swapped for a spinner, taps swallowed |
| `GET /rooms` — the public list, first load | the middle of the screen |
| `GET /rooms` — a refresh | a small one beside the title; **the list stays** |
| the socket opening or re-opening | the connection badge spins instead of showing a settled dot |
| the lobby before its first broadcast | the space the seats will fill, held open, with a spinner in it |
| adding or removing a bot | on *that seat*, and the add button goes busy |
| a move over the socket | one line under the prompt |
| agreeing to the next round | the strip under the felt, saying what it is waiting for |

`GameButton` grew `busy`, which swaps the label and swallows its own taps. That is not
politeness: "open a room" pressed twice is two rooms, "add a bot" pressed twice is a table
with a bot nobody asked for — and the second press is the fault of the first having looked
like nothing happened.

### The bug that turned up while drawing the waits

`GameHolder.act` had no in-flight state. Locally that never mattered, because the reducer
answers in the same frame. Over a socket it was wrong: `RemoteGameSession` holds a **single**
waiter for the answer it expects, so a second move sent while the first was in flight replaced
that waiter and the first hung until it timed out. A player who hurried made their own move
stall. One move at a time now, with the second dropped rather than queued.

### A browser for public rooms

`GET /rooms` already existed and nothing used it. `DiscoverScreen` is the four states it
needs — asking, unable to ask, asked-and-empty, and a list — and `OnlineScreen` asks
public-or-private *before* the room exists, defaulting to private: a listing cannot be taken
back once a stranger has read it, so the safe answer is the one already chosen.

The rows are a pure function of the service's answer (`shared/client/Discovery.kt`, eight
tests) and they **keep the service's order**. A client that re-sorts makes two people looking
at one lobby see two different lists, and one of them taps the row the other was reading.

Four security decisions on that path, three of them fixes:

- **The listing is an allow-list, not the record minus a field.** It used to answer with the
  registry's own row minus `sourceId`, which already published `roomId` — the Durable
  Object's name — and would have published whatever anybody added next. `PublicRoom` names
  what is public; a new field on `RegisteredRoom` stays private until somebody adds it there
  on purpose.
- **`hostNickname` is cleaned by the registry**, not by the field that types it. The UI caps
  it at sixteen characters, which stops the honest caller and nobody else; a direct POST put
  whatever it liked in front of every stranger browsing.
- **`ROOM_OPEN` closes the door as well as the table.** It used to guard only the socket, one
  layer down, so a closed deployment still minted codes and still named its public rooms.
  `/health` and `/replay` stay open above the gate — one is a liveness answer, the other a
  pure function of its own argument.
- **A code that could never have been issued is refused in the Worker**, by
  `looksLikeRoomCode`, before the one Durable Object that knows every live room is asked
  anything. Not the security boundary — `resolveRoomCode` is — but a scan should have to send
  plausible codes to cost the registry a round trip. The refusal is the same 404 an unknown
  code gets, so it is not an oracle either.

Also: the listing sends `no-store` (occupancy a second ago, cached, sends people to a table
that filled while the answer sat in a proxy), a countdown travels as a *duration* resolved
against the service's clock rather than a deadline rendered through a phone's, and the
response is capped at 50 rooms.

### An invitation worth sending

The lobby shows the code monospaced and letterspaced — it is the one string in this app
somebody reads aloud down a telephone — with Share and Copy under it, and the line that says
it can simply be read out.

`shareText` is platform code, because a share *sheet* genuinely is a platform thing: Android's
chooser, the browser's Web Share. Where a platform has none it returns false and the button
falls through to the clipboard, rather than doing nothing the player can see. The clipboard
itself is **not** a second `expect`: Compose carries one on all four targets, and four
hand-written platform implementations would be four APIs to get right for a job the framework
has already done.

## 6k. How good is the bot, and did that just change?

`SelfPlayGateTest` asks whether the bot follows the rules. It says nothing about whether it
plays *well*, and until now nothing did — the original task 5.6 compared against a TypeScript
baseline generated by `npm run recordings:generate`, which goes with `legacy-web/` (§1d).
Comparing an engine against a copy of itself that is being deleted is not a measurement
anybody can repeat next year.

So the bot is now measured against **itself**. Twelve seeds, played out at all three
difficulties, tallied into integers, and the table committed at
`fixtures/bot/self-play-baseline.json`. A heuristic change that moves any of those numbers has
to move them deliberately: regenerate the file, look at the diff, and say in the commit which
way the bot got better. The value is not that 13.20 is a good mean hand — it is that nobody
can change it by accident.

```sh
./gradlew :shared:bot:jvmTest --tests '*TournamentTest*' -Ptournament        # compare
./gradlew :shared:bot:jvmTest --tests '*TournamentTest*' -Ptournament=write  # regenerate
```

**It is a manually-run gate, and that is a decision rather than an oversight.** Thirty-six
whole MCTS games take **6m 39s** on this machine, against 1m 26s for the whole of the rest of
`:shared:bot:jvmTest`. That is worth paying when a heuristic changes and is not worth paying on
every push, so it is excluded from `jvmTest` unless `-Ptournament` is passed — the same shape
as `-Pscreenshots`. The legality gate keeps running every time, because that is the one a
release depends on.

Two rules about what goes in the file, both of them about a baseline being worth having:

- **Every committed number is an integer.** A mean carried as a `Double` differs in its last
  digit between JVMs and the gate becomes a coin toss; means are carried in hundredths, so
  `meanHandTotalCentis = 1320` reads as 13.20 and compares as an `Int`.
- **Latency is measured, printed, and never committed.** A millisecond figure is a fact about
  the machine, not about the bot, and pinning it would turn a busy runner into a red build.

The run as it stands, which is the first time any of this has been measured:

```
difficulty  games  vinto  caller  coalition   mean hand  mean actions  best  worst   ms/decision
easy           12     12       5          7       13.60        377.66    -1     35          4.13
moderate       12     12       8          4       13.20        268.41    -1     36         19.58
hard           12     12       7          5       14.68        248.50    -2     41        100.81
```

Reproduced exactly across three runs; only `ms/decision` moved, which is the argument for
where the line was drawn. Two things in it are worth somebody's attention, and neither is a
defect to fix blind:

- **`hard` costs 24× `easy` per decision** and does not obviously buy 24× anything. It reaches
  the same place in fewer actions (248 against 378), which is what more search should look
  like, but its mean hand is the *worst* of the three.
- **A homogeneous table cannot rank the difficulties.** All four seats play at the same level
  in every game here, so a higher mean hand says the table was harder to sit at, not that the
  bot was worse — three good opponents take the good cards. Ranking them means playing them
  *against each other*, which is a different tournament and a bigger one. Recorded rather than
  guessed at.

The loop itself lives in `Tournament.kt`, shared with `SelfPlayGateTest`: legality and strength
are two questions about one table and should not be asked of two subtly different games.

## 6l. The round trip, now across targets

The corpus round trip used to be across *languages*: TypeScript generated `fixtures/recordings`
and Kotlin replayed them. Once one engine ships, that check has nothing on the other side of it
— the corpus becomes a frozen artefact rather than evidence that two implementations agree
today (§1d). What replaces it is a round trip across **targets**, and it is not the weaker
property. It is the one that can still fail:

- a `Long` is a pair of `Int`s on Kotlin/JS, and `seed` and `rngState` are `Long`s
- the serializer backend differs between JVM, JS and Wasm
- canonical JSON and SHA-256 are hand-rolled and have to agree byte for byte everywhere, or a
  recording made on a phone cannot be replayed on a server

`RecordingRoundTripTest` lives in `shared/client`'s **`commonTest`**, so it plays a whole game
to `scoring`, exports the report, and replays it through the real `replayRecording` harness
**reached through text** — on whichever target is running. `kmp-jvm` runs it on the JVM and
`kmp-web` on Kotlin/JS and Wasm, so task 6.7's "CI job" needed no new CI job: the three legs
already existed and the test was the missing half. Measured: 9.2 s on the JVM, 24.5 s on Wasm.

Nothing is committed and nothing goes stale, because the recording is **generated on the target
that replays it**. Three assertions, and the second and third are what make the first worth
having: a corrupted hash has to be caught *at the action that carries it* (or the harness is
accepting anything), and one seed has to produce one document byte for byte (or two targets
cannot be compared at all).

### The one thing 6.5 turned out to be

The TypeScript `BotAIAdapter` is 1,500 lines because it drives a UI. Split in two here: the
*deciding* is `BotRunner`, a pure function of the state shared with the Durable Object, and the
*pacing* reaches the UI as frames rather than as `await delay(...)` inside the bot driver. What
was left that is genuinely a coroutine question is one line — `LocalGameSession` runs the search
on an injected `botDispatcher`, `Dispatchers.Default` in the app, null in tests — and nothing
checked it was live. A new path reaching the runner without going through `onBotDispatcher`
would move up to 1.6 s of search back onto the drawing thread *silently*, because the game would
still be perfectly correct; it would just stutter on a device, in a build nobody tests.

`BotDispatcherTest` asks the dispatcher, from inside the block that does the thinking, and also
pins that the whole run of bot turns rides on **one** hop rather than one per bot. It compiles
for JS and Wasm, which is where `java.lang.Runnable` was caught: `CoroutineDispatcher.dispatch`
takes `kotlinx.coroutines.Runnable` in common code, and the import is the whole difference.

### What it found on its first run

**A player's exported bug report could not be replayed by anything.** `Recording.formatVersion`
carries a default, `VintoJson` has `encodeDefaults` off — which is right, and is what keeps an
unset optional absent rather than `null` where TypeScript writes nothing — so the field was
silently missing from every report the table's bug-report control produced. And
`GameRecording.formatVersion` is **required**: `CorpusReplayTest` refused to parse one, and so
did the Worker's `POST /replay`. `Recorder.kt`'s own comment promised a report "can be dropped
straight into" that harness, and it could not.

It is one `@EncodeDefault(ALWAYS)`. What is worth keeping is why nothing caught it: `RecorderTest`
replays the recorder's output too, and passes, because it replays *the object it just built in
memory*. A bug report arrives as bytes. Reaching the harness through text is the difference,
and it is the reason this test does the JSON hop rather than calling `replayRecording(report)`.

## 6m. Crashes, and what a failed network call is allowed to look like

Two things the app claimed to have and did not, both reported from a phone.

### The crash reporter existed and was never installed in time

`installCrashHandler` was called from a `LaunchedEffect` inside a composable inside `App()`.
So the handler came into existence **after the first composition** — and the crash worth having
most is the one that stops the app on the launcher, which happens while the vault is being
opened, the deep link is being read and the resources are being resolved. All of that is before
`App()` draws anything. Nothing failed; there was simply nobody listening.

Worse, `SENTRY_DSN` was `private const val SENTRY_DSN = ""` in source, so **every build there
has ever been** reported nowhere, the ones that shipped included.

Both are fixed and the shape is worth knowing:

- `Crashes` (`composeApp/.../crash/Crashes.kt`) is a process-level object with an idempotent
  `install`, called by each of the four entry points **before** the call that composes —
  `MainActivity.onCreate` before `setContent`, `main()` before `application {}` and
  `ComposeViewport`, `MainViewController()` before `ComposeUIViewController {}`. `App()` still
  calls `install` as a last resort, for a host that embeds it directly, and its real remaining
  job is `Crashes.watching { … }`: *where* the app is, read at the moment of a crash.
- The DSN is a **build input**, generated into `BuildInfo.kt` by `:composeApp:generateBuildInfo`
  from `-Pvinto.sentryDsn=` or `VINTO_SENTRY_DSN`. It defaulted to empty for one commit and now
  defaults to **the project's own DSN**, at the product owner's direction: defaulting to empty
  meant every build any of us made still reported nowhere, which is how a crash on opening an
  online game came and went with nothing to look at. The trade is small and real — a DSN's key
  is write-only, so what a stolen one buys is the ability to spend the project's quota — and it
  stays overridable, with an empty string switching reporting off entirely.
- **`App()` does not install the reporter**, and that is now load-bearing. It used to, as a
  fallback for "a host that embeds `App()` directly", and the only such host is the test suite:
  with a real DSN that fallback would arm a live reporter inside every Compose test and post a
  CI runner's failures into the project's Sentry.
- The app scope carries `Crashes.handler()`, so a coroutine that fails on it is **reported**
  rather than printed to a console nobody is reading. That is the failure players describe as
  "it just sat there": the app is alive, the room never loads, and no fatal handler will ever
  see it.
- The per-process report budget went from **one** to five distinct failures. One was right while
  the fatal handler was the only caller and wrong the moment background failures started
  arriving too — the first thing to go wrong would have silenced the crash that ended the app.
  Repeats are still deduplicated by type and message, so a retry loop cannot run up a bill.

`CrashInstallTest` reads the four entry points and asserts the *ordering*, which is the only way
to check it: a runtime test composes `App()` and so installs the handler either way, and the
window that matters is the one before a harness has control. It failed on its own first run —
`import androidx.activity.compose.setContent` is a mention of `setContent` above every line of
the body, so a naive search finds it at character zero and every ordering check passes.

**Still not covered**, and recorded rather than done: a genuine native crash on iOS (a signal, a
Swift trap) is what the Sentry SDK would add; `setUnhandledExceptionHook` catches a Kotlin
exception reaching the top and nothing below it. Task 8.2 and `design.md` §A9 carry it.

### A network call that failed said so in a serialization error

All four connectors **discarded the HTTP status**. The room service answers 404 for a code it
never issued, 503 when it is closed, 429 for a room that is full — and every one of those bodies
went straight into a JSON parser, so a player who mistyped a room code was shown *"Unexpected
JSON token at offset 0"*. That is not a cosmetic fault: it is the difference between retyping
the code and deciding the app is broken.

And `RemoteRoom`'s socket loop caught every exception and backed off, for ever. A mistyped code,
a closed service and a phone in a tunnel produced the same screen — "Reaching the room…",
indefinitely — with nothing that could say which, or whether waiting would help.

What replaces it:

| | |
| --- | --- |
| `RoomTrouble` | Six things that can go wrong, in one vocabulary for four transports |
| `RoomServiceException.permanent` | Whether trying again can change the answer |
| `requireOk(status, body)` | Every connector's REST calls go through it, so a status means the same thing everywhere. The service's own words are carried through; an HTML error page is not |
| `RemoteRoom` | A permanent trouble closes the room at once; a room that has **never** answered gives up after three tries. A socket that drops *mid-game* still reconnects for as long as the app is open — the seat is held by its token |
| `LobbyWord.UNREACHABLE`, `LobbyUi.canRetry` | The lobby tells "this room ended" from "we never got there", and offers another go at the second |
| `RemoteRoom.notices` | Now actually read. It carried every lobby refusal the room sent and **nothing consumed it**, so a refused "add a bot" spun a seat for five seconds and then said nothing at all |

Two platforms can name the refusal and two cannot. Android's OkHttp hands the refusing response
to `onFailure` and the JDK wraps it in a `WebSocketHandshakeException`, so a 404 becomes "no such
room"; a **browser deliberately hides** the HTTP response of a failed WebSocket upgrade from the
page, and `NSURLSessionWebSocketTask` reports it through a session delegate this connector does
not have. Giving up after three tries is what turns those two into a sentence rather than a
spinner — a vaguer sentence, and a sentence.

Held by `RoomTroubleTest` (in `commonTest`, so the mapping is identical on all four targets) and
`UnreachableRoomTest`, which drives the real `RemoteRoom` against a refusing connector on virtual
time.

## 6n. The endgame, which was being skipped

Calling Vinto took the player straight from the button to "Round over". The bots' final turns
happened — they are in the log — and none of them was drawn.

`AnimationQueue` drops a whole batch that costs more than its budget, which is the right rule for
a client catching up after a reconnect and was the wrong number for this. A Vinto call submits
the call **and all three bots' entire last turns** as one batch: measured at **14** moves in an
ordinary deal, against a budget of 8. So the queue cleared it and the table landed on the final
state, exactly as designed, having skipped the endgame the whole hand was played for.

The budget is 24 now, and the doc says what it is measuring: *how far behind the client is*, not
how much happened. Those were the same number until the final round proved they are not. It does
not weaken the reconnect guard — `RemoteGameSession` already collapses a sync to a single frame
before the queue sees it (design C4).

`FinalRoundIsWatchedTest` plays a real local game to a Vinto call and asserts the batch reaches
the queue whole. It fails on the old budget, which is what makes it worth having.

### And the final round now says who is playing whom

Two things taken from the web client, which does this better, in this app's idiom rather than
its own:

- **The strip above the felt draws from the call onwards.** It used to `return` when
  `coalitionLeaderId` was null, so the window between the call and the coalition choosing showed
  no banner, no turn counter and nothing else — silence at the single most surprising moment in
  the game.
- **A roster of faces**, coalition on the left, caller on the right, the leader ringed. The web
  draws two named columns, which is a panel's worth of height; this is one line of portraits,
  which is the same information in a tenth of the room and reads faster besides — three of the
  four players are bots the person knows by face long before they know by name. It carries one
  spoken description for the whole row, because four portraits read out one at a time are four
  names with no relationship between them.

### And the score sheet answers the question first

It opened with "Round 3" and a table, leaving the player to derive the winner from a column of
+3 and −1 at the exact moment they wanted an answer. It now opens with the verdict — the call
held, level, the others beat it, or nobody called — and the two totals it turned on. The row that
**decided** the round is ringed and named, which is the number both the +3 and the −1 were worked
out from and which nothing used to identify. Portraits went into the rows for the same reason
they went into the roster.

No confetti and no exclamation mark, unlike the web's "🎉 Coalition Victory!": the same screen
has to carry a loss, and a player who has just lost a round does not need it celebrated at them.

`RoundOutcome` and `bestCoalitionHands` are pure and live in `shared/client` beside `roundPoints`,
tested by `RoundOutcomeTest`; the words are tested by `ScoreSheetTest` in composeApp. Same split
as `CardHelpTest` and `LessonCopyTest` — the model says *which* verdict, the resources say it in
a language.

## 6o. Errors as values, after a crash nobody could look at

An online game was opened and the app died. There was no report, because reporting had never
been switched on in any build — which is the first thing §6m is about and the reason the DSN is
now the project's own by default rather than an empty string. So the honest answer to *why*
that crash happened is: **nobody knows, and that was the bug behind the bug.**

What could be done was to go and find every way that path can end the app. Two were real, and
both are the same mistake in different clothes.

### The model handled it and the screen crashed on it

`tableFor` opens with `players.firstOrNull { it.id == viewerId } ?: return Table(Ask.Watching)`.
One function later, `FeltTable` reached the same seat with `players.first { it.id == viewerId }`
— which throws. So a view whose viewer has no seat produced a considered "you are watching" from
the model and a `NoSuchElementException` from the felt, with nothing between it and the launcher.

A solo game always seats you. It could only ever have fired online, where a room decides who is
seated, and online is the one place nothing catches it.

The fix is the type: `PlayerView.mySeat` is nullable, so the compiler asks the question at every
call site. The felt now draws four seats either way — a watcher's fourth player takes the chair
the viewer's own hand would have used, because the felt has exactly four places and a player
with nowhere to sit disappears from the game.

### The catch listed the exceptions somebody had thought of

`RemoteGameSession.dispatch` caught `TimeoutCancellationException` and `IllegalStateException`.
That covers a socket that is gone and a room that does not answer. It does not cover the write
*failing on a socket that is there* — an `IOException` on Android, a `CompletionException` on
the JVM, a wrapped `NSError` on iOS, a `DOMException` in a browser. Three of those four reach
the top of a coroutine and end the app.

### So the boundary answers instead of throwing

| | |
| --- | --- |
| `RoomConnector` | Returns `RoomAnswer<T>` — `Ok` or `Failed(trouble, reason)`. Nothing in the interface throws |
| `answering { }` | The one place allowed to catch broadly. Each connector wraps its own transport in it, so nothing above the seam sees an exception |
| `SendOutcome` | `Sent` or `Failed(reason)`, for a message handed to the wire |
| `permanent(trouble)` | A `when` with no `else`, so a seventh trouble cannot be added without somebody deciding whether it is worth retrying |

The point is not tidiness. A `when` over a sealed type is **exhaustive or it does not compile**,
so a call site that forgets the failure is a build error rather than a crash a player finds.
That was proved on the way in: changing `RoomConnector`'s return types broke `OnlineScreen` and
`DiscoverScreen` immediately, at exactly the two places that had been `catch (e: Exception)` and
would have gone on compiling for ever if either had been deleted.

Deliberately **not** `kotlin.Result`: it carries a `Throwable`, which is the thing being got rid
of, and `getOrNull` makes ignoring the failure a character shorter than handling it.

### And where the type system cannot help, the build does

`List.first {}` returns `T`, not `T?`, and throws. Kotlin has nothing to say about it, so
`PartialFunctionTest` does: it reads the files that read wire data — the online screens, the
session, the lobby model — and fails the build on `first {}`, `first()`, `last()`, `single()`,
`getValue(` and `!!`. It caught a fresh one on its own first run, in code committed an hour
earlier, whose defence was that the caller had already done a `firstOrNull` two frames up. That
is precisely the reasoning that put a `first {}` on the felt.

Scope is the client's view of the wire and stops there. The engine is not covered and should not
be: it owns its own state, `first {}` on a list it has just built is total in fact, and a rule
that cried wolf there would be switched off within a week.

## 6p. Why a crash on the online screen reached nobody

Reported twice: open the online screen, the app exits, and Sentry has nothing. The second half
of that turned out to have two causes, both certain, and both invisible to every gate this
repository has.

### The app had no `INTERNET` permission

`androidApp/src/main/AndroidManifest.xml` declared no permissions at all. Android then refuses
every socket the process opens — so **online play could not work** and **no crash report could
ever leave the device**: the handler fired, the envelope was built, and the platform denied the
POST. Two failures wearing one face, from a line nobody had written.

Nothing in the build could have caught it. `assembleDebug` produces a well-formed APK, every
JVM suite passes, and the Compose tests run in a process with no permission model at all. It
took a phone, and then it took reading the merged manifest. `ManifestTest` reads the manifest
now — and also asserts the list stays *short*, because a permission is a question asked of a
player and this game has no business asking most of them.

### And the report was fire-and-forget on a process being killed

`CrashReporter.report` did `scope.launch { post(...) }` and returned. The handler then chained
to the platform's, which on Android ends the process at once. A DNS lookup, a TLS handshake and
a POST do not fit in the microseconds between those two lines, so a correct reporter with a
correct envelope delivered nothing.

Two changes, and the second is the one that actually guarantees it:

- **The crashing thread waits.** `awaitCrashReport` is `runBlocking` with a four-second ceiling
  on the JVM, Android and Apple; in a browser it is a no-op, because an unhandled rejection
  does not tear the page down and there is nothing to block for. Short on purpose: an app that
  has already crashed must not sit there because a network is not answering.
- **The envelope is written down before the network is touched.** `Crashes` stores it in the
  vault under one key and clears it only when a send actually succeeds, so a POST cut off
  halfway is retried by the next launch. One slot, because a phone in a crash loop would
  otherwise fill the vault with copies of one bug and the newest is the one worth having.

`CrashReporter` is split into `envelopeFor` and `send(onSent)` to make that ordering possible,
and `CrashReporterTest` pins it: a failed send leaves the stored copy alone, a successful one
clears it.

### And the reason online play does not work at all is a flag

Checked against the deployment rather than reasoned about:

```
$ curl https://vinto-room.kupalinka.app/health
{"ok":true,"service":"vinto-room","engine":"kotlin","roomOpen":false}
$ curl -o/dev/null -w '%{http_code}' https://vinto-room.kupalinka.app/rooms          # 503
$ curl -X POST .../rooms -d '{"isPublic":false,"hostNickname":"probe"}'
The room is closed: server-side action validation is not implemented yet (see
ActionValidator, task 4.4). POST /replay to exercise the engine.                     # 503
```

So browsing and creating both fail at the service, exactly as designed — `ROOM_OPEN` is
`"false"` in `wrangler.jsonc` and §6i step 4 has never been walked. Two things follow, and the
second is the one nobody would notice:

- **The flag's own comment is now out of date.** It says "flip this to `true` in the same commit
  that lands the validator, never before"; the validator landed in phase 4 and the flag did not
  move. Opening the room is a deliberate act with credentials, so it stays a maintainer's step —
  but it is no longer *blocked* on anything in the code.
- **The deployment is stale.** Its refusal names task 4.4 as unfinished, which dates it to
  before most of this branch. Flipping the flag on what is deployed would open an old room; the
  Worker has to be rebuilt and redeployed either way.

What the client does about it: the **trouble** picks the sentence and the service's words go
underneath in small type. A player who taps Browse now reads "Online play is not open yet.
Single player and the lesson work as normal" rather than "server-side action validation is not
implemented yet (see ActionValidator, task 4.4)" — which is true, is addressed to somebody who
works on this, and tells a player nothing. `troubled()` is a `when` with no `else`, so a seventh
`RoomTrouble` is a compile error rather than a screen that says nothing.

### What is still unknown

**Why the app exits.** That is not diagnosed, and saying otherwise would be a guess dressed up.
What is now true is that the next one reports itself: the permission is there, the report
blocks until it is away, and an envelope survives the process dying. A crash during composition
reaches the default handler like any other, so this covers it.

## 6i. Taking the room live — the maintainer's runbook

The online client is code-complete: protocol, room cores with JVM tests, per-event views,
recordings, pacing, `RemoteGameSession`, lobby screens, and a two-client harness that plays a
full round through all of it (`TwoClientGameTest`). What remains is the part only a person
with credentials and hardware can do, in this order:

**1. Verify locally, on the machine that can.** This container cannot compile `composeApp`
(androidx lives behind dl.google.com), so the UI-adjacent work ships verified by
`:composeApp:detekt` plus everything the shared modules prove. Run the rest:

```sh
./gradlew :shared:shapes:jvmTest :shared:engine:jvmTest :shared:bot:jvmTest \
          :shared:client:jvmTest :shared:protocol:jvmTest :shared:room:jvmTest
./gradlew :composeApp:jvmTest       # the compose suites, FullGameUiTest included
./gradlew :composeApp:jvmTest --tests game.vinto.app.ScreenshotTest          # writes goldens
./gradlew :composeApp:jvmTest --tests game.vinto.app.ScreenshotTest --rerun  # proves them stable
```

Commit the eight goldens `ScreenshotTest` writes (`composeApp/src/jvmTest/goldens/`). CI does
**not** run that suite — on a fresh runner it would write its own goldens and pass, asserting
nothing, and a maintainer's images would not survive a different JVM's glyph rasterization
anyway (the exclusion, and its reasoning, is on the test task in `composeApp/build.gradle.kts`;
`-Pscreenshots` forces it back on). Run the desktop app once and listen: four sounds — a deal, a landing, a thud on a penalty, a chime at
the round's end — and none anywhere else.

**2. Exercise the rewired worker against `wrangler dev`.** `index.mjs` now sends prebuilt
per-seat envelopes and files recordings; the gate scripts import the *unchanged* exports and
still pass, but `gate-real-room.mjs` and `gate-sessions.mjs` walk the rewired paths:

```sh
./gradlew :worker:compileKotlinJs
cd worker/cloudflare && npx wrangler dev &   # then, against it:
node gate-real-room.mjs && node gate-sessions.mjs && node gate-lobby.mjs \
  && node gate-lifecycle.mjs && node gate-limits.mjs && node gate-room-codes.mjs
```

**3. Land the analytics gate — before the deploy, not after it.**
`openspec/changes/add-live-analytics`, phases 1–4. This is a blocking step and the reason is
arithmetic rather than principle: an event not collected on launch day is a question that can
never be asked about launch day. There is no backfill for "how many people who opened it ever
pressed Play online", and that number is the one that says whether phase 9 was worth building.

Two of the four phases are cheap because the server already knows the answers — the room is
authoritative, so its lifecycle and round events cost nothing over the wire — and one of them
(2.3) is what finally answers what a room *costs*, which is the number that decides whether
online play can stay free. The fourth is the privacy gate: `§6c` already binds this zone to
no cookies, no identifiers and GPC honoured, and task 4.4 turns that paragraph into a test
that plays three rounds and asserts nothing identifying left the device.

```sh
cd worker/cloudflare && npx wrangler dev &
node gate-analytics.mjs      # the event sequence, and the empty-binding case
./gradlew :shared:client:jvmTest --tests '*Analytics*'
```

Phase 5 (the dashboard, the Web Analytics beacon, revisiting sampling against real volume) is
deliberately **not** blocking — it reads data that does not exist until this has shipped.

**4. Deploy and open the room — one deploy, both halves.** `ROOM_OPEN` stays `"false"` until
the client that speaks to it ships; flip it in the same deploy that publishes the client
builds, never before (the flag's comment in `wrangler.jsonc` says the same):

```sh
npx wrangler deploy          # then poll:
curl https://vinto-room.kupalinka.app/health   # expect roomOpen: true after the flip
```

**Or from a phone.** `.github/workflows/deploy-room.yml` is the same deploy run by GitHub —
*Actions → Deploy room → Run workflow* — which works in the GitHub mobile app. It runs the
room's gates first, deploys with `--var ROOM_OPEN:<your answer>` rather than editing
`wrangler.jsonc`, and then **polls `/health` until the edge agrees**, failing if it never does:
propagation is not atomic and §6d records that catching the maintainer out twice. It is
`workflow_dispatch` only and the flag defaults to `false` on every run, because deploying is a
decision and opening a room to strangers is a bigger one. Setting it up is two web pages and no
computer — DEPLOYMENT.md §6a.

**5. Prove it with people.** Two devices (or a device and the desktop app): create a room,
join by code, add two bots, play a round through — kill one app mid-round and watch the seat
go bot and come back on relaunch. Then the four-human table (9.7's second verification),
which needs four hands and cannot be scripted.

**Still open by design**: 9.9 (Sentry on the worker, a load test with 100 rooms) and 9.10
(store releases with multiplayer enabled) — operational work that starts after this runbook
has been walked once. Sentry stays separate from step 3 on purpose: crash reporting and
analytics answer different questions and should not share a pipe.

## 7. Traps and known issues

**Repo-wide**

- `npx tsc` and `npm run typecheck` are **denied by project policy**. Use
  `npx nx run-many --target=typecheck`.
- **Nx caches typecheck results**, which will happily report success for code that no longer
  compiles. Pass `--skip-nx-cache` when you want a real answer. This masked a genuine
  failure during this work.
- **`nx build @vinto/game` is broken** (pre-existing): `<Html> should not be imported outside
of pages/_document` while prerendering `/404`. Ruled out: missing `not-found.tsx`, the
  Sentry wrapper, root-page prerender, stale `.next`, version mismatch, `global-error.tsx`.
  Needs a bisect of `src/app`. It blocks CI's `build` job.
- **The Kotlin CI has run now, and four of its five checks are green.** It was written in a
  container that cannot compile `composeApp` at all, so every one of its jobs was unverified
  guesswork until the first push; ten runs later `kmp-detekt`, `kmp-jvm`, `kmp-worker` and
  `kmp-ios` pass and `kmp-android` compiles and fails four tests (§1b, §1c). Of the three
  failures predicted here, none happened: the action versions were fine, `java.awt.headless`
  was enough for the Compose suites, and `wrangler dev` came up inside the poll window every
  time. What actually broke was in the code the container could not compile — which is the
  argument for the workflow existing, not against the prediction.
- **A CI log is readable, but only if you ask for enough of it.** `get_job_logs` defaults to
  a tail short enough to land inside `setup-gradle`'s post-action cache report, which is
  longer than most failures; the compiler's `e:` lines sit above it and never appear. Pass a
  large `tail_lines` (2000 is comfortable) and grep the result. Two round trips were spent
  re-deriving this, and the `What the compiler said` step in `kmp.yml` exists because of it —
  worth keeping either way, since it is what makes a failure legible from a phone.
- **`nx build @vinto/game` no longer has a CI job.** It has been broken since before this
  branch (below), the workspace is frozen, and a check that has never been green teaches
  people to ignore red. Restoring it is part of picking the web client back up.
- **At least two tests are flaky**, both driven by MCTS, which uses `Math.random`:
  `bot/…/mcts-coalition-cooperation.test.ts` ("should use coalition evaluation when in final
  round") and `local-client/…/bot-tossin.test.ts` ("should require all 3 bots to mark
  ready"). Each was observed to fail once and then pass three consecutive reruns. Expect
  intermittent red CI until the bot's randomness is made injectable — which the Kotlin port
  plans anyway (design D4: injected `Random`, fixed iteration budget in tests). Treat a
  single failure in these two as suspect before assuming a regression; rerun first.
- **Coverage thresholds are inert**: all five `vite.config.ts` files put `lines: 70` outside
  `coverage.thresholds`, which Vitest 4 ignores. Coverage gates nothing today.
- `legacy-web/tools/*.ts` need `vite-node` (`npm run recordings:*`); `ts-node` cannot load
  the workspace `.ts` sources through `node_modules` symlinks. They write to the repository
  root's `fixtures/`, resolved from the script rather than from the working directory.
- lefthook pre-commit runs lint `--fix` and `nx format:write`, so files change under you
  during commits.

**Kotlin**

- **The Compose/Wasm production compile needs more heap than the Kotlin daemon's default.**
  On an 8 GB Mac `:composeApp:compileProductionExecutableKotlinWasmJs` failed with "Not
  enough memory to run compilation" — while the same task had succeeded on a larger machine
  with no `gradle.properties` at all. `gradle.properties` now pins
  `kotlin.daemon.jvmargs=-Xmx3g` and `org.gradle.jvmargs=-Xmx2g` so the build does not
  silently depend on how much RAM the developer happens to have. If it still OOMs, raise the
  Kotlin daemon figure first, and run `./gradlew --stop` after changing either value —
  a running daemon keeps its old heap.
- **A local detekt CLI run is weaker than the gate unless you say so.** The Gradle task sets
  `buildUponDefaultConfig = true`, so `config/detekt/detekt.yml` is an *overlay* on detekt's
  defaults; a bare `java -jar detekt-cli.jar --config config/detekt/detekt.yml` runs only the
  rules that file names and reports clean on code CI then rejects. It also has to be pointed
  at source directories rather than module roots, because the Gradle task sets
  `source.setFrom(files("src"))` and never sees a `build.gradle.kts`. The invocation that
  matches the gate, for a host that cannot run Gradle against androidx:

  ```bash
  java -jar detekt-cli.jar --build-upon-default-config \
    --config config/detekt/detekt.yml --baseline config/detekt/baseline.xml \
    --input "$(ls -d composeApp/src worker/src shared/*/src | paste -sd,)"
  ```

- **"Plain Kotlin stdlib" is safer than an Objective-C binding, and is not the same as safe.**
  `CrashHandler.ios.kt` was written to use `setUnhandledExceptionHook` precisely to dodge the
  binding traps below — and still failed `kmp-ios`, because the hook is `@ExperimentalNativeApi`
  and a missing opt-in is a compile *error*. Three `iosMain` files, three CI round trips. The
  only reliable rule is that nothing here can check an Apple source set at all.
- **An Objective-C class factory method is renamed `create`.** Kotlin/Native renames a class
  method whose selector begins with its own class's name, so `+[NSData dataWithBytes:length:]`
  is `NSData.create(bytes:length:)` here — an extension on the companion, so it also has to be
  imported by name. `Beacon.ios.kt` called it by its header name and broke `kmp-ios`; it is the
  same shape as the `NSMutableURLRequest` setters above, and the third time this family of
  mistake has been made in this tree. A non-Mac host cannot catch any of them.
- **The web client had no `index.html`, and nothing noticed.** `wasmJsBrowserDistribution`
  produced two `.wasm` files, a `.js` and no page to load them, so the Compose web client
  compiled and could not be served — for the whole life of the branch. Nothing caught it
  because nothing *serves* it: `kmp-web` compiles the client, `kmp-android` and `kmp-ios`
  build the other two, and no job opens a browser. The shell is now
  `composeApp/src/wasmJsMain/resources/index.html`. A compile gate is not a serve gate, and
  the distinction is worth remembering for the next target.
- **Kotlin/JS's standard library is not Kotlin/Wasm's**, and `kotlin.js.Date` is the one that
  catches people: it is in the JS stdlib and absent from Wasm's, so `Storage.wasmJs.kt` had
  been unable to compile since it was written. Nothing said so, because nothing built it —
  `kmp-android` builds `composeApp` for Android and `kmp-ios` for Apple, and the browser
  target had no gate anywhere. `kmp-web` now runs `:composeApp:compileKotlinWasmJs`. Reach a
  browser global from Wasm with a one-expression `js("...")` function.
- **A default is not written down, and a required field is not optional.** `VintoJson` sets
  `encodeDefaults = false` deliberately, so any `@Serializable` field with a default vanishes
  from the output unless it carries `@EncodeDefault(ALWAYS)`. That is correct for optionals and
  silently wrong for a format version: `Recording.formatVersion` was omitted from every exported
  bug report, and `GameRecording.formatVersion` is required, so nothing could parse one. The
  general shape — a round trip that never leaves memory proves less than it looks like it does —
  is in §6l.
- **Nothing may follow `composeCompiler { }` in `composeApp/build.gradle.kts`.** Statements
  after that block are never executed, and silently: the build succeeds, a `logger.lifecycle`
  after it prints nothing, and a `tasks.register` after it leaves a task Gradle then reports as
  *"not found in project ':composeApp'"* — which reads exactly like a typo in the task name.
  Bisected with probes on a run with the configuration cache off: every block before it runs and
  every statement after it does not. Half an hour went into finding that out; the comment above
  the block in the script says the same thing, and new configuration goes **above** it.
- **A missing serialization runtime is invisible until Kotlin 2.4, and it surfaces on wasm
  first.** `composeApp` reads `@Serializable` enums declared in `shared:*` — `Surface`,
  `FunnelStep`, `Difficulty`, `Pace`, `ThemeChoice` — and never declared
  `kotlinx-serialization-json` itself, because those modules keep it as an `implementation`
  dependency and do not expose it. That compiled for the life of the branch. From Kotlin 2.4
  the serialization plugin makes a `@Serializable` type's *companion* implement
  `kotlinx.serialization.internal.SerializerFactory`, and reading `Surface.SOLO` then needs
  the runtime on the reader's classpath — so the missing dependency stopped being invisible
  and became forty "Cannot access 'SerializerFactory' ... check your module classpath" errors.
  It broke **`:composeApp:compileKotlinWasmJs` only**: the JVM and Android classpaths happen
  to carry the runtime by another route, so `assembleDebug` and every JVM suite stayed green
  and CI's `kmp-web` was the one job that noticed. The fix is one `implementation` line; the
  lesson is that "it compiles on the JVM" says nothing about a classpath on another target.
- **An `UncompletedCoroutinesError` from a Compose test is usually arithmetic, not a deadlock.**
  `SwapAnimationTest` failed on the Compose Multiplatform 1.12 upgrade with no assertion
  message at all, which reads exactly like a hang — and two plausible deadlock fixes (handing
  the clock back before `waitForIdle`, pausing it only around the animation) did nothing,
  because there was no deadlock. Instrumenting the body settled it in one run: it reached the
  assertion **89 seconds** in, past `runTest`'s sixty-second wall clock, so the deadline fired
  mid-body and the failure named the symptom rather than the cause.
  The cost was `SETTLE_MS = 4_000`, a number nobody had measured, multiplied by the seven
  `settle()` calls the test makes — with the clock paused, `settle()` renders **every frame**
  in that span, and Compose 1.12 renders more per frame than 1.8 did. Measured: 4,000 ms → 99 s,
  2,000 ms → 55 s, 1,000 ms → 34 s, linear as you would expect. It is 2,000 now, which keeps
  twice the margin of the smallest value that works and halves the test, plus an explicit
  `runComposeUiTest(testTimeout = …)` so the budget is stated rather than inherited.
  Two things worth keeping. The parameter is **`testTimeout`**, not `timeout` — the name is in
  `ComposeUiTest.skiko.kt` and nowhere convenient. And `advanceTimeBy` is *virtual* time, so
  this is deterministic: a slower runner takes longer per frame but renders the same frames.
- **A green build can ship an APK with no resources in it, and only a phone finds out.**
  Compose Multiplatform 1.12 registers `copyAndroidMainComposeResourcesToAndroidAssets` for a
  module using `com.android.kotlin.multiplatform.library` and never configures its
  `outputDirectory` — so the task cannot run, nothing depends on it, **the build succeeds**,
  and the APK contains not one string, card, sound or font. The app then dies on the launcher
  at the first `Res.string` lookup. Every gate this repository has stayed green through it:
  the JVM suites read resources off the classpath, `assembleDebug` produces a well-formed APK,
  `:composeApp:jvmTest` was 124/124. It took installing the thing.
  `androidApp/build.gradle.kts` assembles `assets/composeResources/<packageOfResClass>/…` by
  hand from the prepared resources. **The check that it is still needed is one line** —
  `unzip -l …/androidApp-debug.apk | grep -c assets/composeResources` should be 28, not 0 —
  and the workaround should be deleted the day the plugin sets its own output. Two smaller
  traps inside it: the Android source-set API refuses a `Provider` (`.get().asFile`), and
  `tasks.withType<CopyResourcesToAndroidAssetsTask>().configureEach { outputDirectory.set(…) }`
  does *not* take, which is why the task is bypassed rather than repaired.
- **The client cannot report a crash that happens before the first composition.**
  `installCrashHandler` is called from a `LaunchedEffect` inside `ReportCrashes`, which is
  inside `App()` — so a startup crash, which is the one you most want, happens before the
  handler exists. `SENTRY_DSN` is empty in source besides, so `CrashReporter.enabled` is false
  and nothing is installed at all in a debug build. Fixable in two small pieces (install it in
  `MainActivity.onCreate` before `setContent`; set the DSN for release builds, DEPLOYMENT.md
  §7a) and recorded on task 8.2 rather than done.
- **AGP 9 will not let an Android application be a KMP module, and there is no property that
  changes its mind.** `com.android.application` beside `org.jetbrains.kotlin.multiplatform`
  is refused outright with *"move the usage of 'com.android.application' into a separate
  subproject"*. The `android.builtInKotlin=false` / `android.newDsl=false` bypass AGP offers
  covers `com.android.library` **only** — and does not reach an included build's precompiled
  script plugins either, so `build-logic` needs the real migration regardless. What the real
  migration is: libraries use `com.android.kotlin.multiplatform.library` with a
  `kotlin { android { } }` block replacing `androidTarget()` and the top-level `android { }`;
  the application becomes its own module. Hence `androidApp/`, which is `iosApp`'s counterpart
  and holds only what an application is — manifest, `MainActivity`, launcher icons, theme,
  applicationId, version, signing. Every Android `actual` stayed in `composeApp`.
  Three things bite on the way through, in order: the two modules may not share a namespace
  (`composeApp` is `game.vinto.app.ui` now); `org.jetbrains.kotlin.android` is *refused* by AGP
  9, which has built-in Kotlin; and Kover before 0.9.9 does not recognise the new extension.
- **The toolchain's gate is Compose Multiplatform, not detekt.** Believing otherwise cost an
  afternoon's worth of wrong advice, so: `androidx.compose.animation:*:1.12.0` fails
  `checkDebugAarMetadata` with *"requires compileSdk 37"* and *"requires Android Gradle plugin
  9.1.0 or higher"* — and AGP 9 requires Gradle 9. The chain is **Compose MP -> AGP 9.1 ->
  Gradle 9 -> compileSdk 37**, and it is one change rather than four. 1.12.0 also moves the
  rendering (9,374 pixels on the home screen against a 304-pixel tolerance) and fails
  `SwapAnimationTest`, both of which are consequences worth budgeting for rather than
  surprises.
  **detekt is fine**: 1.23.8 analysed 129 files under Kotlin 2.4.10 with zero findings,
  because this build runs it without type resolution (`source.setFrom(files("src"))`, no
  classpath) — it parses rather than resolves, so it does not care which compiler wrote the
  source. Kotlin therefore moves *independently* of all of the above, and has.
- **This container cannot build the Kotlin/JS or Wasm targets from cold.** `kotlinWasmToolingSetup`
  fetches karma from `codeload.github.com`, which the egress proxy answers 403 (§1c lists
  `github.com` beyond this repository). It only bites when the Kotlin version changes and the
  toolchain has to be re-resolved; a warm `kotlin-js-store/` hides it. That store is
  gitignored, so CI resolves it fresh every run and is unaffected — but it does mean a Kotlin
  bump cannot be proven here on the three targets it most affects, and CI has to answer.
- **`runTest` has a sixty-second wall-clock timeout, and the iOS simulator is where you find
  out.** It is generous on the JVM and not generous on Kotlin/Native: `RecordingRoundTripTest`
  runs in 9 s on the JVM and 24.5 s on Wasm, and `kmp-ios` still failed it with
  `UncompletedCoroutinesError` — the one case that plays *two* whole MCTS games to prove a seed
  is a seed. Pass an explicit `timeout` for anything that plays a game out, and say in the
  comment that the number is a CI budget on the slowest target rather than a claim about the
  code. `SelfPlayTest`, `FinishesTest` and `OnlineScoreTest` already did; a `commonTest` that
  reaches Apple needs it more, not less.
  **This rule was written here and then not applied to the next test written in the same
  session** — `BotDispatcherTest` went over the line on the Kotlin 2.4 bump, in the same way,
  on the same job. So the budget now has a home rather than a habit: `WHOLE_GAME` in
  `shared/client/src/commonTest/.../TestBudget.kt`, used by every suite that plays a game out.
  A rule with nowhere to live gets re-learned.
- **`android.useAndroidX=true` is mandatory**, not a preference: Compose Multiplatform's
  Android artifacts are AndroidX, and without it the build fails at `checkDebugAarMetadata`.
  It lives in `gradle.properties`.
- **A headless emulator (`-no-window`) screenshots all black** under the default GPU mode —
  the app is running fine, the framebuffer just is not. Boot with `-gpu swiftshader_indirect`
  when you need `adb exec-out screencap` to show anything.
- **Backticked test names with spaces are JVM-only.** Kotlin rejects them for JS and Native,
  so anything in `commonTest` must use camelCase names. This is why `PrngVectorsTest` reads
  `reproducesEveryPublishedShuffle` and not `` `reproduces every published shuffle` ``.
- `gradlew` **must stay LF** or it fails on Linux/CI with "bad interpreter". Pinned in
  `.gitattributes`; don't undo it.
- Kotlin/JS **tree-shakes to the exported surface** — a library build with nothing
  `@JsExport`ed produced 781 bytes. Measure with `binaries.executable()`, not `library()`.
- JVM-only stdlib functions (e.g. `toSortedSet()`) fail on other targets. The compiler
  catches them, but only for targets that are actually configured — see §5 point 4.
- `Prng` traps, both real and both covered by tests: the state is a **uint32** so it is
  carried as `Long` (a signed `Int` corrupts values ≥ 2^31), and `nextInt` must take the
  modulo in **unsigned** space because Kotlin's `%` can return negative.

**This Windows machine specifically** (may not apply on the Mac)

- Antivirus **deletes `gradlew.bat`** shortly after it is written. It is committed, so
  `git checkout gradlew.bat` restores it; add a repo exclusion if it recurs.
- No `python` on PATH; heredocs invoking `python` hang.

## 8. Verification checklist for a new machine

```bash
# The Kotlin build, from the repository root.
./gradlew :shared:shapes:allTests              # expect 6 PRNG tests per target
./gradlew :worker:jsNodeProductionRun          # expect "gate ok: rngState=2583707619"
./gradlew detekt                               # expect no issues; maxIssues is 0

# The retired TypeScript workspace, which is still the parity reference.
cd legacy-web
npm ci
npm test                                       # expect ~608 passing across 5 projects
npx nx run-many --target=typecheck --all --skip-nx-cache   # expect 5 green
npm run recordings:replay -- ../fixtures/recordings        # expect 50/50 clean
```

That last number is the useful one: `rngState=2583707619` after shuffling 54 cards with
seed 42 is produced by **both** implementations. If it differs, the cross-language contract
has broken and nothing above it can be trusted.

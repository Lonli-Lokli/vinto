# Kotlin workspace — setup, state and handoff

Everything needed to pick this migration up on another machine. The iOS targets have now
been built and tested on a Mac (§5); Android is the remaining untried platform.

- **Plan of record**: `openspec/changes/archive/migrate-to-kotlin-multiplatform/` — archived (§1);
  what is still open is `openspec/changes/ship-and-operate/`
- **Cross-language contract**: `docs/game-engine/RECORDING.md`
- **The release gate, in one command**: `docs/kotlin/RELEASE-GATE.md`
- **Platform measurements**: `docs/kotlin/PLATFORM-GATE.md`
- **Bot decision**: `docs/bot/BOT-ENGINE-DECISION.md`

---

## 0. Where each section lives

This was one file for the whole migration and reached 200 KB, which is past what a tool will
read in one go — so the half of it that a reader needs *first* stayed here and the long-form
sections that explain how each part was built and proved moved next door. This file is the
**index, the state and the setup**.

**The section numbers did not change.** Every existing reference of the form
`docs/kotlin/README.md §6c` — and there are dozens of them, in source comments, in workflows,
in the other docs and in archived OpenSpec changes — still names the paragraph it always
meant. The live ones now name the file directly; the table says which file that is.

| Section | Lives in | What it is |
| --- | --- | --- |
| §1, §1a, §1e, §1f | **here** | Where the work stands, the repository move, what is left, what is blocked |
| §2, §3, §4, §5 | **here** | Prerequisites, the module map, the commands, the iOS bring-up |
| §6 | **here** | Decisions already made — do not silently reopen |
| §8 | **here** | The verification checklist for a new machine |
| §1b, §1c, §1d | [`CI.md`](CI.md) | The six checks; a host that cannot compile `composeApp`; retiring the web client |
| §6a, §6b, §6e, §6k, §6l | [`GATES.md`](GATES.md) | How `shapes`, the engine, the validator and the bot are each proved |
| §6c | [`HOSTING.md`](HOSTING.md) | The website on `vinto.kupalinka.app` — its shell, its caching, its CORS |
| §6d, §6r, §6i, §6q | [`ROOM.md`](ROOM.md) | The room Worker, the solo game that needs none of it, and the runbook |
| §6f, §6g, §6j, §6n | [`UI.md`](UI.md) | The phone, the menu and the lesson, the lobby, the endgame |
| §6h | [`WORDS.md`](WORDS.md) | Every string a player sees, and where it lives |
| §6m, §6o, §6p | [`RELIABILITY.md`](RELIABILITY.md) | Crashes, errors as values, and the reports that reached nobody |
| §7 | [`TRAPS.md`](TRAPS.md) | Traps and known issues — the ones that have each cost somebody an afternoon |

Two numbering notes, both older than this split. **§6 is not part of the §6a–§6r series**:
it is the table of settled decisions, and it is in this file. And **§6d was used twice** —
for deploying the engine and for single-player. The deployment section keeps the letter; the
solo one is **§6r** now, and `ROOM.md` says so where it sits.

## 1. Where the work stands

**Merged.** The Kotlin rewrite landed on `master` on 2026-08-31 as 52dcc20 (#184), with all
six CI checks green. That commit also published both services, because the push triggers
CI.md §1b describes stopped being inert the moment those paths existed on the default branch:
`Deploy room` and `Deploy web` both ran and both succeeded.

So the migration is over, and `migrate-to-kotlin-multiplatform` is archived
along with `add-live-analytics` and `retire-legacy-web`. What was left in the first two was
never code — an upload key, a Mac, a phone, a dashboard, a week of traffic — and it lives in
`openspec/changes/ship-and-operate` now, one item at a time with its blocker named. §1f below
is the same list for somebody reading on a phone.

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
  the Kotlin daemon's default heap (TRAPS.md §7)
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
  TypeScript, and Kotlin reproduces every one of them (GATES.md §6a)

- **`GameAction` is ported** (task 3.2): all 25 action types as a sealed hierarchy, with the
  `{ type, payload }` wire shape built by hand — kotlinx's own polymorphism puts its
  discriminator beside the payload's fields rather than above them. Every one of the 13,900
  recorded actions round-trips to the same canonical form

- **The engine is ported and passes the parity gate** (phase 4): all 25 handlers, and
  **all 50 recordings / 13,900 actions replay with canonical state hashes matching
  TypeScript's**, per action, plus final-state verification (GATES.md §6b)
- **detekt** runs over every Kotlin module with `maxIssues: 0`
- **`ActionValidator` is ported**, and tested by re-attributing every seat-bound action in the
  corpus to all three other players — 18,066 attempts, none accepted (GATES.md §6b)
- **The engine runs correctly in the Cloudflare runtime**, not just on the JVM: the Worker
  exposes `POST /replay` and all 50 recordings replay through it in workerd (ROOM.md §6d)

- **The bot is ported and follows the rules** (phase 5): all of `legacy-web/packages/bot` — memory,
  opponent modeller, heuristics, evaluators, determinization, rollout policy, move generator,
  state transition, outcome simulator, Vinto round solver, coalition planner, MCTS decision
  service — plus `BotRunner`, which turns decisions into actions for a server that has no UI.
  **Decision parity with TypeScript was not required and was not attempted**; rule-following
  was, and is gated: four Kotlin bots play whole games through the real engine with every
  proposed action passing `ActionValidator` first, and games must reach `scoring`
  (GATES.md §6e)

- **The room runs the real game**: the `Room` Durable Object deals from a seed, validates
  every action, checks the seat boundary above it, sends each socket its own redacted view,
  and plays the bots server-side. Verified two ways — `gate-real-room.mjs` in plain Node for
  the game questions, and `gate-two-clients.mjs` through workerd for sockets, hibernation and
  reconnect (ROOM.md §6i step 2)

- **The platform gate is closed.** 2a.1b was the last open item and it passes: the worst
  request observed costs 1.6 s of a Durable Object's 30 s budget (`PLATFORM-GATE.md`)

- **CI runs, and four of its five checks are green.** `kmp-detekt`, `kmp-jvm`, `kmp-worker`
  and `kmp-ios` all pass; everything compiles on every target, including the two source sets
  that had never been compiled anywhere. What is red is `kmp-android`, and only its *tests*:
  four of the Compose suite's 73 (CI.md §1b, §1c)

**Next**

1. **Analytics, before the room opens** — `openspec/changes/archive/add-live-analytics`, phases 1–4.
   A blocking release gate rather than a nice-to-have: nothing in this game is measured today,
   so the online funnel phase 9 built is unknowable, the cost of a room is unknown, and every
   client failure is something a player experiences and nobody hears about. ROOM.md §6i step 3
2. ~~The four failing Compose UI tests~~ — **done**. All four are fixed and
   `:composeApp:jvmTest` is 79 tests green; CI.md §1c keeps the diagnosis because three of
   the four were not what reading the log suggested
3. Walk the runbook in ROOM.md §6i — the goldens, the sounds, the deploy that opens the
   room, and the proof with people. It is the part only a person with credentials and
   hardware can do, and no network policy unblocks it
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
`npm run recordings:generate` and `tools/replay-recording.ts` — both gone with `legacy-web/`
(CI.md §1d) — so they are rewritten around what still exists: bot strength measured against a
committed self-play baseline, and the recording round trip run across *targets* (JVM, JS,
wasmJs) rather than across languages. That is the property that still matters once one engine
ships, because a `Long` is two `Int`s on Kotlin/JS.

**There was no desktop app**, though ROOM.md §6i step 1 told the maintainer to run one and
listen for the four sounds. `compose.desktop.currentOs` was a test dependency only: no
`main()`, no `application` block, no `run` task. There is now — `./gradlew :composeApp:run` —
and it is the fastest way to look at a UI change, with no emulator to boot.

### The order

**Tier 0 — done in this pass.** The desktop run target, the OpenSpec corrections, the
archiving, and the retired tasks above.

**Tier 1 — the release gate.** Nothing ships before these.

1. Analytics phases 1–4 (ROOM.md §6i step 3, `openspec/changes/archive/add-live-analytics`)
   — **done**
2. Sentry (8.2 client, 9.9 server) — a separate pipe from analytics on purpose
3. The goldens, the sounds, and walking ROOM.md §6i end to end

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
5. ~~**Finish the translation.**~~ **Done** (WORDS.md §6h, eight slices). Every string a
   player sees now comes from `strings.xml`: `Narration`, `TableModel` and `TeachScript`
   return typed messages — `Say`, `Label`, `Ask`, `Detail`, `Explains`, `Teaches` — and the
   UI renders them. Five string literals are left in `shared/client` and none of them is a
   word a player sees. Adding `values-be/` or `values-uk/` is now a file and no code, which
   is what the exercise was for. Seven of the eight slices turned up a defect that had
   nothing to do with language
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

`legacy-web/` is gone. `fixtures/` stays at the root and the Kotlin engine still replays it;
what went is the ability to *regenerate* it, which is the point of CI.md §1d.

## 1f. BLOCKED — what cannot be finished from a container

**These are now tracked as `openspec/changes/ship-and-operate`**, one task each with its
blocker named, since the two changes that used to carry them are archived (§1). This section
stays because it is the version somebody reads on a phone; if the two disagree, the change file
is the plan and this is the summary.

Two rows below have moved on since they were written and are corrected in that change rather
than here: **analytics 5.3** named "the `vinto` Pages project", which is the *leftover* from
HOSTING.md §6c serving a copy nothing links to — switching Web Analytics on there would count
nobody and look like it worked (DEPLOYMENT.md §7b is corrected). And **a language selector**
is no longer blocked at all: `values-ru/` exists now, with 403 of the 404 strings, so the
thing it was waiting for arrived.

**Much of the table below has since been done**, in the pass that prepared both stores. Rather
than rewrite every row, here is what changed — `openspec/changes/ship-and-operate/tasks.md` is
the authoritative version and carries the detail:

* **The upload key exists** (`keystore/vinto-upload.jks` + `keystore.properties`, both gitignored
  and on one machine — *back them up*), so `bundleRelease` produces a signed .aab and the
  assetlinks fingerprint is no longer hypothetical.
* **Both `.well-known` files are written.** `apple-app-site-association` is complete;
  `assetlinks.json` carries the upload key's fingerprint and still needs Google's app-signing one
  added beside it once the Play Console shows it (`docs/kotlin/APP-LINKS.md`).
* **The iOS app is a real submission target now** — an icon, an `Info.plist`, entitlements, a
  privacy manifest and a generated `project.yml`, where before it had none of them. It archives
  only once Xcode is signed into the developer account on the build machine.
* **A language selector had nothing to select; it has nineteen alternatives now.** Every locale in
  `Language.kt` has a `values-<loc>/strings.xml`.
* **"Rate this app" ships**, pointing at listings that are not live yet — the reasoning that kept
  it out is reversed in `ship-and-operate` 2.5.
* **Both store listings are pushed.** Apple holds 1.0 with its copy, categories, content rights
  and age rating; Play holds the listing and the icon. Neither has a build or screenshots yet.

Everything below has been attempted and stopped for a reason that is not a missing decision:
no credentials, no hardware, or no data yet. Each line names what would unblock it, so the
person who has that thing can pick exactly their share up rather than re-deriving the list.

Nothing in this section is waiting on design. Where a choice had to be made to get as far as
being blocked, it was made and recorded in the relevant `design.md`.

| Task | What it needs | How far it got |
| --- | --- | --- |
| analytics 1.1 — confirm Workers Analytics Engine allowances | The Cloudflare dashboard, signed in to the account that owns the Worker | The binding, the writer and the absent-binding path are all built and gated; what is unconfirmed is the *plan's* real writes/day, read allowance and retention. `design.md` §A1 carries published figures and says in as many words that they are not measured |
| analytics 5.1 — dashboard route | The three secrets in DEPLOYMENT.md §7 (`ANALYTICS_TOKEN`, `ANALYTICS_ACCOUNT_ID`, `DASHBOARD_KEY`) — addable from a phone through the Cloudflare dashboard — and traffic. ~~A deployment~~: the room is live and open (ROOM.md §6q) | **Built**: `GET /counts?key=…` renders the six queries server-side, and `gate-dashboard.mjs` covers its refusals, its escaping and the queries' shape in 51 checks. What cannot be covered here is a single number — the WAE SQL API is the one part of Analytics Engine `wrangler dev` does not emulate. Not ticked |
| analytics 5.3 — Web Analytics on the Pages project | The Cloudflare dashboard for the `vinto` Pages project | A per-site switch that makes Cloudflare inject its own beacon; there is nothing in this repository to change and nothing here can verify it. DEPLOYMENT.md §7b is written for somebody who does not do this for a living. The page it injects into **did not exist** until this pass — see the `index.html` note in TRAPS.md §7 |
| analytics 5.4 — revisit sampling and the cost model | A week of real traffic — which can now start, since the room is open (ROOM.md §6q) | Arithmetic on data that does not exist. It is the reason phase 5 is not a release gate |
| ~~The website's custom domain~~ | **No longer blocked** | `vinto.kupalinka.app` was blocked on a Cloudflare dashboard visit for as long as the site was a Pages project, because `wrangler pages` cannot attach a custom domain. The site is a Worker now, which claims its hostname from `routes` in `composeApp/cloudflare/wrangler.jsonc` — so `deploy-web.yml` creates the record itself and this is a workflow run rather than a browser. Held by `WebShellTest`, which fails if that route stops matching `INVITE_HOST` |
| Deep links — verifying them | The two association files hosted on `vinto.kupalinka.app`, each naming a real credential | The app half is built and tested: intent filters, both iOS handlers, the browser path, and `roomCodeFrom` with 5 tests. What cannot be done here is publish **`/.well-known/assetlinks.json`** (needs the release keystore's SHA-256 fingerprint — `keytool -list -v -keystore …`) and **`/.well-known/apple-app-site-association`** (needs the Apple team id and bundle id, served as `application/json` with no extension). Until both exist the https links open the website instead of the app; the `vinto://` scheme works today and is why it is there. **Was also blocked on the row above** — there was no website to serve them from; there is now, and they belong in `composeApp/src/wasmJsMain/resources/.well-known/`, which every deploy publishes |
| ROOM.md §6i step 1 — the eight goldens | A maintainer's machine, and a human looking at the images | `ScreenshotTest` writes them and CI deliberately does not run it: a fresh runner would write its own and assert nothing. Generated PNGs are not committed from here on purpose |
| ROOM.md §6i step 1 — the four sounds | Ears, and `./gradlew :composeApp:run` | The desktop target exists now, which is the part that was missing |
| ~~ROOM.md §6i step 4 — the deploy, and flipping `ROOM_OPEN`~~ | **Done** (§6q) | Deployed and opened from a phone through `deploy-room.yml`; verified against the live edge by `gate-engine-replay` and `gate-two-clients` |
| ROOM.md §6i step 5 — two devices, then four humans | Hardware and four people | The **scripted** half is now done against the live deployment (§6q): two clients, one Durable Object, sockets, hibernation and reconnect. What is left is what cannot be scripted |
| 8.2 — a native crash on iOS | Xcode, and a decision on the Sentry KMP SDK | The reporter is installed at process start on all four targets now (RELIABILITY.md §6m) and catches a Kotlin exception reaching the top. A signal or a Swift trap is what an SDK would add, against weight in a 3.7 MB wasm bundle; `design.md` §A9 has it flagged rather than settled |
| Crash reporting, end to end | A DSN, and a build that carries it | The pipe is built and gated (`CrashReporterTest`, `CrashInstallTest`, `CrashReportTest`). What has never happened is a report arriving in a real Sentry project: the DSN is a build input now (`-Pvinto.sentryDsn=`), and nothing here has one |
| 9.10 — store releases | An upload key, store accounts, and a signed build | `assembleRelease` signs with the upload key when `keystore.properties` exists and with the debug key when it does not, so the path is exercised without the secret |
| Settings — "rate this app" | A published store listing | Deliberately absent rather than built and hidden. A review button that opens nothing reads as the app being broken, to the one person most inclined to say so in public. It is four lines and a `market://` URL the day 9.10 ships |
| Settings — a language selector | One translated `strings.xml` | The app has `values/` and nothing else. WORDS.md §6h's eight slices existed so that adding a language would be a file and no code, and no file has been added — so a selector today is a control with a single option in it, which is worse than no control. The unblocking step is a translation, not screen work |
| `kmp-ios` beyond CI, and any `commonMain` change trusted on Apple | macOS with Xcode | The macOS leg of CI covers compilation; §5's warning stands — a `commonMain` change that breaks iOS cannot fail on a non-Mac host |
| 7.1 — the animation layer on real hardware | A physical phone, and a Mac to look at the simulator | The decision is made and the layer is built and running on an Android emulator. It has never been *watched* on a real device, and on Apple it is compiled and simulator-tested by `kmp-ios` but not looked at by a person |
| 8.1 — a release job on tags | An upload key, a Play track, an Apple developer account | No longer blocked on CI existing — six checks are green (2.3). `assembleRelease` already signs with the upload key when `keystore.properties` exists and the debug key when it does not, so the path is exercised without the secret. R8 and everything iOS sit behind the same accounts |
| 8.2 — the iOS privacy manifest and permissions review | Xcode | The reporter itself is done, breadcrumbs included: a crash report now carries the deal's `gameId`, the round and the turn, which is the same address the room's Sentry reports carry |
| 9.9 — a load test with 100 concurrent rooms | ~~A deployment~~ — now only the decision to run one against a live room | The rest of 9.9 landed: Sentry reports carry the deal's `gameId`, the round and the action index into it, and recordings are filed per round. A load test cannot go against `wrangler dev` — it enforces no CPU limit whatsoever (ROOM.md §6d), so it would measure the laptop rather than the platform |
| 4.8 — the corpus on an Android emulator | A machine that can resolve androidx, and an emulator in CI | **The iOS half is done**: `kmp-ios` runs `:shared:client:iosSimulatorArm64Test`, so since 6.7 a whole game is generated and replayed through the real harness on Kotlin/Native every run. The Android half wants an instrumented `connectedAndroidTest` reading the corpus from an asset, which needs `androidx.test` — dl.google.com answers 403 here (CI.md §1c) and androidx is not on Maven Central, so it cannot be compiled in this container at all, only pushed and hoped for. An hour's work on a machine that can build `composeApp` |

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
./gradlew --version              # bootstraps Gradle on first run

# Android only: tell Gradle where the SDK is (local.properties is gitignored).
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

## 3. Module map

| Module          | Targets                                                 | Purpose                                                                                                    |
| --------------- | ------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| `shared:shapes` | jvm, js, (iosArm64, iosSimulatorArm64 on macOS)         | Types + `Prng`. The port starts here. Its tests run on every target above.                                 |
| `shared:engine` | jvm, android, js, wasmJs, (iOS on macOS)                | `GameEngine.reduce`, toss-in and scoring utils, the replay harness. Partly ported — see GATES.md §6b.               |
| `shared:bot`    | jvm, android, js, wasmJs, (iOS on macOS)                | MCTS decision service, coalition planner and `BotRunner`. Reads only what a seat may see.                   |
| `shared:client` | jvm, android, js, wasmJs, (iOS on macOS)                | `GameSession` with both lives: `LocalGameSession` (solo, no socket — see ROOM.md §6r) and `RemoteRoom`/`RemoteGameSession` over the wire. |
| `shared:protocol` | jvm, android, js, wasmJs, (iOS on macOS)              | The wire, declared once: `ClientMessage`/`ServerMessage`, the room-facing types, `ProtocolJson`. See `PROTOCOL.md`.               |
| `shared:room`   | jvm, js                                                 | The room and registry cores, moved out of the worker so the JVM can test them. Envelope builders, recordings, pacing.             |
| `worker`        | js                                                      | Cloudflare Worker + `Room` Durable Object: `@JsExport` delegates over `shared:room`, under the thin JS shim in `worker/cloudflare/`. |
| `composeApp`    | android, wasmJs, (iosArm64, iosSimulatorArm64 on macOS) | Compose UI — one `commonMain` for all three clients: the solo game, the lesson, and the online lobby + table. A KMP **library**, so it has no `assembleDebug`. |
| `androidApp`    | android                                                 | The Android **application**: manifest, `MainActivity`, launcher icons, applicationId, signing. `:androidApp:assembleDebug` is the APK. |
| `iosApp`        | —                                                       | Xcode project embedding `composeApp`'s `ComposeApp` framework. macOS only.                                 |

The full intended layout is in design D1. Modules are added as they are ported rather than
scaffolded empty.

**`composeApp` is a library and `androidApp` is the app**, which is worth stating because the
obvious guess is wrong and fails late: `./gradlew :composeApp:assembleDebug` is not a task that
exists, and Gradle answers "task 'assembleDebug' not found in project ':composeApp'" rather
than pointing anywhere useful. The split is not a preference — **AGP 9 refuses to let
`com.android.application` share a module with the Kotlin Multiplatform plugin**, with no
property to bypass it, so the whole UI and every Android `actual` stay in `composeApp` and
`androidApp` holds only what an application is. `androidApp/build.gradle.kts` carries the
reasoning. It also makes the two sides symmetrical: `iosApp` was always a thin project
embedding `composeApp`'s framework, and this is its counterpart.

## 4. Commands

```bash
# --- Kotlin (run from the repository root) ---
./gradlew :shared:shapes:allTests             # PRNG parity on every target (JVM, JS, iOS sim)
./gradlew :shared:shapes:jvmTest              # just the JVM leg, when iterating
./gradlew :worker:jsNodeProductionRun         # PRNG self-check (prints the gate number)
./gradlew :composeApp:wasmJsBrowserDistribution   # build the Compose web bundle
./gradlew :androidApp:assembleDebug           # Android APK — the *app* module (§3)
./gradlew :androidApp:installDebug            # ...onto a connected phone or emulator
./gradlew :androidApp:assembleRelease         # release APK; debug-signed — see UI.md §6f
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

## 6. Decisions already made — do not silently reopen

| Decision                                                                    | Where recorded                            |
| --------------------------------------------------------------------------- | ----------------------------------------- |
| Cloudflare Durable Object per room; no JVM server                           | design D1, D9                             |
| Bots run server-side (a client would need other seats' hidden cards)        | design D9, online-multiplayer spec        |
| Compose Multiplatform for web, 3.7 MB gzipped accepted                      | design D1 risks, `PLATFORM-GATE.md`       |
| One bot engine (v1/MCTS); v2 deleted for reading hidden hands               | `docs/bot/BOT-ENGINE-DECISION.md`         |
| Canonical hash excludes history + `botMemory`, includes `opponentKnowledge` | `RECORDING.md` §4                         |
| Every game is exactly 4 players                                             | deterministic-engine spec                 |
| Bots call Vinto when the search values calling above playing on — no threshold, no full-hand gate | `MctsBotDecisionService.shouldCallVinto`, `docs/bot/MCTS-REVIEW.md` §5 |
| Bot verification is rule-following, not decision parity                     | GATES.md §6e, tasks 5.5/5.6                        |
| One decision service **per bot**, not one shared across seats               | `BotRunner`; TypeScript wipes memory each turn |

## 8. Verification checklist for a new machine

```bash
# The Kotlin build, from the repository root.
./gradlew :shared:shapes:allTests              # expect 6 PRNG tests per target
./gradlew :worker:jsNodeProductionRun          # expect "gate ok: rngState=2583707619"
./gradlew detekt                               # expect no issues; failOnSeverity is Info
./gradlew :shared:engine:jvmTest               # expect 50/50 recordings, 13,900 actions
```

`rngState=2583707619` after shuffling 54 cards with seed 42 is the useful number: it was
produced by **both** implementations, back when there were two, and the value is what the
committed `fixtures/prng/vectors.json` still pins on every target. If it differs, something
below the engine has moved and nothing above it can be trusted.

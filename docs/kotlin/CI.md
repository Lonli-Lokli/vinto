# Continuous integration, and the hosts that can run it

The six checks and what each one proves; what their first runs found, most of which had been
wrong for a while; what a container that cannot compile `composeApp` can still verify; and the
web client whose own CI went before the client did.

Split out of [`README.md`](README.md) — this workspace's index, state and setup — when that
file grew past the size a tool will read in one go. The section numbers are unchanged, so an
older reference of the form `docs/kotlin/README.md §6c` still names the paragraph it meant.

---
## 1b. Continuous integration

`.github/workflows/kmp.yml`, six checks, split by what each needs. It is the only workflow
that *checks* anything — the web client's three were removed with its CI (§1d). Beside it sits
two workflows that check nothing and publish: `deploy-room.yml` for the room Worker
(ROOM.md §6i step 4) and `deploy-web.yml` for the website (HOSTING.md §6c). Both deploy a
Worker — the website was a Pages project for one deploy and is not any more, for the reason
in HOSTING.md §6c. Both are `workflow_dispatch` only, so
neither runs on a push — deploying is a decision, not a consequence of merging — and both are
how a thing gets published without a desktop.

**Both now also deploy on a push to `master`**, filtered by path: `worker/**` and `shared/**`
publish the room, `composeApp/**` and `shared/**` publish the site, and a docs-only commit
publishes nothing. `shared/**` is in both lists deliberately — the engine is one module, and a
change to it changes the client and the server together. Verified by replaying the path filters
over this branch's own history with GitHub's glob semantics (`*` does not cross `/`, `**`
does): the two web fixes matched the web only, the four build changes matched both, and the
five docs commits matched neither.

**That reverses the rule these files were written with** — "deploying is a decision, not a
consequence of merging" — on request. What it buys is that `master` and the running services
cannot drift, and one instance of that drift had already happened and was dangerous:
`wrangler.jsonc` said `ROOM_OPEN: "false"` for months while the live room was open, because
the flag had only ever been passed as `--var` from a manual run. **A push trigger on top of
that would have closed a room people were playing in**, on the next unrelated commit to touch
`shared/`, with nothing in the workflow that looked wrong.

So the flag was fixed first. The committed config now says `"true"`, a push deploys it as
written with no `--var` at all, and a `workflow_dispatch` run may still override it — the
override is an explicit choice on a run, and closing the room permanently is a reviewable
change to one line. The workflow decides that once, in a `door` step, so the deploy and the
`/health` check that follows cannot disagree about what they expected.

**The triggers are live.** They were inert only while none of those paths existed on `master`;
the merge on 2026-08-31 created all of them, and that same commit deployed the room and the
website. `ROOM_OPEN` was already `"true"` in the committed config, which is the fix that had to
land before a push could deploy — see the paragraph above for what would otherwise have
happened to a room people were playing in.

**Both files have to exist on `master` as well as on the branch**, because GitHub only offers
"Run workflow" for a `workflow_dispatch` workflow that is on the *default* branch. `deploy-room.yml`
was written, pushed to a feature branch, and simply did not appear in the dropdown; that is how
the rule was found, and `deploy-web.yml` needs the same one-file trip.

| Check         | Runner | What it proves                                                                   |
| ------------- | ------ | -------------------------------------------------------------------------------- |
| `kmp-detekt`  | Linux  | Static analysis and formatting over every module and source set, `maxIssues: 0`   |
| `kmp-jvm`     | Linux  | The six shared modules' JVM suites — the corpus replay, the validator, the bot     |
| `kmp-web`     | Linux  | The same `commonTest` suites on Kotlin/JS and Kotlin/Wasm — 538 tests on each — and the Compose web client's own compile, which nothing else covers |
| `kmp-android` | Linux  | `assembleDebug`, plus the Compose suites headless (goldens excluded — see ROOM.md §6i)     |
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
source sets that had never been compiled anywhere — ROOM.md §6i says as much, that composeApp
ships "verified by `:composeApp:detekt` plus everything the shared modules prove" — so CI asked
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

**detekt 2, and what `allRules` is actually worth.** The gate runs `dev.detekt` 2.0.0-alpha.6
with **every rule detekt ships**, including the ones it leaves off by default. Four things are
worth knowing before touching it.

- **It is an alpha, deliberately and reversibly.** There is no detekt 2 stable and no RC —
  `2.0.0-alpha.6` is the only 2.x that exists. The catalog previously recorded a decision
  *against* gating a release on an alpha; that was overridden on request, and the revert is a
  version number plus three identifiers (`dev.detekt` → `io.gitlab.arturbosch.detekt`,
  `dev.detekt.gradle.Detekt`, and `detekt-rules-ktlint-wrapper` → `detekt-formatting`).
- **The config format moved in three ways**, all of them silent if you get them wrong. The
  `build:` block is gone — `maxIssues: 0` is now `failOnSeverity = Info` on the Gradle
  extension. `threshold: N` became `allowed…: N` and **the meaning inverted**, so every number
  in `detekt.yml` is one lower than it was; copying them across would have loosened every
  limit by one. And the `formatting:` ruleset is now `ktlint:`.
- **`allRules = true` found 6,726 findings across 51 rules, and 5,900 of them were three
  families that contradict decisions this project has already made** — two of which
  contradict *each other*. `UndocumentedPublic*` wants KDoc on every public member;
  `DocumentationOverPrivate*` wants comments on private members deleted and the members
  renamed instead. `FunctionNameMaxLength` flags 632 test names that are sentences because
  TRAPS.md says they have to be. And ktlint's *experimental* formatting rules are a whole-codebase
  reformat rather than analysis. All are declined in `detekt.yml` with the reason written
  beside each, rather than baselined: a baseline is a list of things to fix, and none of
  these will ever be fixed.
- **The baselines are per module now, and that is a correctness fix.** A single shared file
  cannot be generated at all: every module's `detektBaseline` task writes the whole file, so
  with 25 modules the last to finish overwrites the other 24. Under 1.x nobody noticed because
  the file had been assembled by hand from a CI log. The first real regeneration produced
  **19 entries for 265 findings**, which is how it was found. There are eight files now, one
  per module with debt, and the debt is attributable to the module that owns it.

What is left after all that is **219 baselined findings** — real debt, newly visible, in
`config/detekt/baseline-*.xml`. The old rule still applies and matters more at this size: fix
an entry and delete its line; never regenerate a file to make a *new* violation go away.

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

- The goldens, the sounds, the deploy and the four-human table: ROOM.md §6i, unchanged. None
  of them is unblocked by a network.
- 9.9 (Sentry on the Worker, a load test) and 9.10 (store releases).
- The stale checkboxes in phases 2, 3 and 6 of `tasks.md`.

## 1d. Retiring the web client

**Done.** `legacy-web/` is deleted, and with it the ability to regenerate the parity corpus.
The corpus itself stays — 50 recordings, still replayed on every run — and is now frozen by
`CorpusIsFrozenTest` against `fixtures/recordings/MANIFEST.sha256`. Why the generator was
deliberately *not* ported is in `fixtures/recordings/README.md`.

Its CI went first, because it was failing on every pull request for a reason nothing on the
Kotlin side could fix:

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

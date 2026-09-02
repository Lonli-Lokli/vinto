# Traps and known issues

Things that have each cost somebody an afternoon. Repo-wide first, then Kotlin, then one
machine's own problems.

Split out of [`README.md`](README.md) — this workspace's index, state and setup — when that
file grew past the size a tool will read in one go. The section numbers are unchanged, so an
older reference of the form `docs/kotlin/README.md §6c` still names the paragraph it meant.

---

**Repo-wide**

- **The Kotlin CI runs on `master` now, and all six checks are green** — most recently on the
  merge commit itself. The paragraph below was written when there were five and four passed;
  it is kept because what it says about *why* the workflow existed is the part that aged well.
  It was written in a
  container that cannot compile `composeApp` at all, so every one of its jobs was unverified
  guesswork until the first push; ten runs later `kmp-detekt`, `kmp-jvm`, `kmp-worker` and
  `kmp-ios` pass and `kmp-android` compiles and fails four tests (CI.md §1b, §1c). Of the three
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
- **The Python generators read a mark that used to live in the web client.**
  `tools/brand/vinto-mark.png` is the orange V, and it is the source of every launcher icon,
  the favicon, the manifest icons and the share card. It was rescued out of
  `legacy-web/apps/vinto/public/` when that directory was deleted, because two scripts read it
  — a dependency a `grep` for the directory name finds and a build does not, since nothing
  runs them automatically and the PNGs they write are committed.

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
- **`immutable` on a path with no content hash corrupted the app's text, and only for
  returning visitors.** Reported from a phone against the live site: the home screen perfect,
  everything behind "Play online" truncated, garbled or blank. A clean-cache load of the *same
  deployment* was flawless, which is what pointed at caching rather than at the build.

  `_headers` served `/composeResources/*` as `immutable, max-age=31536000`. Nothing under that
  path carries a content hash — the paths are fixed names baked into the wasm — so the promise
  was one the URL could not keep. What turned a stale asset into corruption is that **Compose
  does not look strings up by key at runtime**: the generated accessors carry a byte offset and
  length into each locale's `strings.commonMain.cvr`, and those numbers live in the wasm, which
  *is* content-hashed and therefore always the current build's. New offsets against a year-old
  table read every string after the first changed entry from the wrong place. Entries before it
  are untouched, which is exactly why the home screen was fine — its keys sort earlier than the
  lobby's.

  Reproduced twice against the deployed bundle, which is what made it certain rather than
  plausible: deleting one entry near the top gave blank buttons, "JOI" for "How to play" and
  mojibake for the version; deleting one positioned *between* the home and online keys
  reproduced the reported split exactly.

  Fixed by making the tables `no-store` per locale by exact path — a wildcard that silently
  fails to match is the same failure wearing a hat — and everything else bounded rather than
  forever. That reaches nobody already affected, because `immutable` means their browser will
  not ask again for a year, so `index.html` (which is `no-store`, and the only always-fresh
  file) refetches each table once per build with `cache: "reload"`. Held by two tests in
  `WebShellTest`: a path may be `immutable` only if it carries a content hash, and every
  `values-*` locale needs both a rule and a repair entry — because adding a language is meant
  to be a file and no code, and a missing one is this bug returning in that language alone.

  The general lesson is the one HOSTING.md §6c already states, and this file did not apply to
  its own resources: **`immutable` is a claim about a URL, not about a file.**

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
  is in GATES.md §6l.
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
- **The wasm build is noisy by default, and three of the four causes are one line each.** A
  deploy log carried 44 warnings, which is enough that nobody reads any of them. What they
  were, and why each is worth knowing:
  - **33 `ExperimentalWasmJsInterop` opt-ins.** Every call into JavaScript from Kotlin/Wasm
    needs it, across all seven `*.wasmJs.kt` actuals. Annotating each would be 33 copies of a
    decision made once, by having a browser target at all — there is no non-experimental way
    to reach `fetch` or `localStorage`. It is an `optIn` on the **wasmJs target**, not on the
    project: the JVM, Android and iOS actuals do not touch that API and should not be quietly
    opted in to anything.
  - **9 build-script deprecations.** Compose Multiplatform 1.12 deprecates its own
    `compose.runtime` / `compose.ui` accessors in favour of the version catalog. Moving them
    turned up the reason the accessors exist: **`material3` is on a different version line**,
    and `org.jetbrains.compose.material3:material3:1.12.0` does not exist — the 1.12 plugin
    resolves **1.9.0**. So the catalog needs a second version ref, and it has to be re-checked
    on every Compose bump. `compose.desktop.currentOs` stays an accessor: it is not deprecated
    and it resolves a different artifact per host, which a fixed coordinate cannot do.
  - **2 `LocalClipboardManager` deprecations**, which are `@Suppress`ed rather than fixed, and
    the note above them now says why with evidence instead of by assertion. The replacement
    still is not reachable from common code in 1.12: `ClipEntry.withPlainText` is declared per
    platform. Checked with a one-line probe in `commonMain` — it compiles for wasmJs and fails
    for the JVM with *Unresolved reference*. The comment used to cite 1.8 and had never been
    re-tested.
  - **3 dead safe calls and 6 `js(IR)` deprecations.** The safe calls are K2 reading a
    smart cast through a boolean `val` (`isTossInPhase` carries `activeTossIn != null`), which
    the older compiler did not do — so these appeared without anybody writing anything.

  Two things this is worth remembering for: `./gradlew :composeApp:compileKotlinWasmJs` **does
  work in this container** — only `wasmJsBrowserDistribution` is blocked, and only because its
  toolchain setup fetches from `codeload.github.com`. And detekt's
  `SpacingBetweenDeclarationsWithAnnotations` fires the moment you add a `@Suppress` above a
  declaration that has a comment block on it, which is a formatting failure arriving from a
  change that had nothing to do with formatting.

- **This container cannot build the Kotlin/JS or Wasm targets from cold.** `kotlinWasmToolingSetup`
  fetches karma from `codeload.github.com`, which the egress proxy answers 403 (CI.md §1c lists
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
  catches them, but only for targets that are actually configured — see README.md §5 point 4.
- `Prng` traps, both real and both covered by tests: the state is a **uint32** so it is
  carried as `Long` (a signed `Int` corrupts values ≥ 2^31), and `nextInt` must take the
  modulo in **unsigned** space because Kotlin's `%` can return negative.

**This Windows machine specifically** (may not apply on the Mac)

- Antivirus **deletes `gradlew.bat`** shortly after it is written. It is committed, so
  `git checkout gradlew.bat` restores it; add a repo exclusion if it recurs.
- No `python` on PATH; heredocs invoking `python` hang.

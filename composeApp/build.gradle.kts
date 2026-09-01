import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

/**
 * Apple targets only exist on macOS — Kotlin/Native cannot build them elsewhere. Same host
 * check as `shared/shapes`; see that file and `docs/kotlin/README.md` §5.
 */
val isMacOs = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)

if (!isMacOs) {
    logger.warn(
        "composeApp — building WITHOUT the iOS targets on ${System.getProperty("os.name")}. " +
            "Android and web are unaffected; the iosApp Xcode project cannot be built here.",
    )
}

kotlin {
    /**
     * A JVM target that ships nowhere.
     *
     * It exists so the Compose tree can be tested without a device: `runComposeUiTest` renders
     * the real composables headlessly, which catches the failures the presenter tests cannot
     * see — a composition that throws, a screen that draws nothing, a button whose label the
     * player never gets. An emulator would catch those too, on a machine that has one.
     */
    jvm()

    /**
     * The Android half of a library, not of an application.
     *
     * AGP 9 refuses `com.android.library` beside the Kotlin Multiplatform plugin and names
     * `com.android.kotlin.multiplatform.library` as the replacement; it folds the whole
     * Android configuration into `kotlin { }`, which is why the `android { }` block that used
     * to be at the foot of this file is gone. What was in it that is genuinely an
     * *application's* — the applicationId, the version, the signing config, the launcher icons
     * and the manifest — moved to `:androidApp`, along with `MainActivity`.
     */
    android {
        // Not `game.vinto.app`: that belongs to `:androidApp`, which is the *application*,
        // and a namespace has to be unique across every module in the merge. The Kotlin
        // package is untouched — a namespace only names the generated R class and the
        // library's own manifest, and nothing here has either.
        namespace = "game.vinto.app.ui"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()

        // Every call into JavaScript from Kotlin/Wasm now needs this opt-in, and there are
        // 33 of them across the seven `*.wasmJs.kt` actuals — sockets, storage, the share
        // sheet, the crash hook, the beacon, the sounds. Annotating each one would be 33
        // copies of a decision that was made once, by choosing to have a browser target at
        // all: there is no non-experimental way to reach `fetch` or `localStorage` from
        // Wasm, so a per-use opt-in carries no information a reader could act on.
        //
        // Scoped to this target rather than set globally, because that is exactly the scope
        // of the claim — the JVM, Android and iOS actuals do not touch this API and should
        // not be quietly opted in to anything.
        compilerOptions {
            optIn.add("kotlin.js.ExperimentalWasmJsInterop")
        }
    }

    if (isMacOs) {
        // A static framework is what `iosApp` embeds. `baseName` is the module name Swift
        // imports, so changing it means editing the Xcode project too.
        listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = "ComposeApp"
                isStatic = true
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)

            // The game itself. `shared:client` brings the engine and the bots with it, so a
            // single-player game needs nothing else — no network dependency appears here,
            // and `NoNetworkGuardTest` is what keeps that true.
            implementation(project(":shared:client"))
            // The room code's shape, so a mistyped invite link fails on the device rather
            // than costing the registry a round trip — and so the client and the Worker
            // cannot disagree about what a code looks like. `shared:room` cannot be used
            // here: it targets only jvm and js.
            implementation(project(":shared:protocol"))
            implementation(libs.kotlinx.coroutines.core)

            // The serialization *runtime*, for types this module does not itself serialize.
            //
            // `Surface`, `FunnelStep`, `Difficulty`, `Pace`, `ThemeChoice` and the rest are
            // `@Serializable` enums declared in `shared:*`, which keep the runtime as an
            // `implementation` dependency and so do not expose it. Reading one of their
            // *companions* — which is what `Surface.SOLO` compiles to — needs the runtime on
            // this module's classpath, because from Kotlin 2.4 the serialization plugin makes
            // those companions implement `kotlinx.serialization.internal.SerializerFactory`.
            //
            // It compiled before because the older plugin generated no such supertype, so the
            // missing dependency was invisible rather than absent. On Kotlin 2.4 it is a wall
            // of "Cannot access 'SerializerFactory' ... check your module classpath", and it
            // surfaces on **wasmJs first** — the JVM and Android classpaths happen to carry
            // the runtime by another route.
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            // The Android socket actual (net/Net.android.kt): the platform’s de-facto
            // WebSocket — java.net.http never shipped in the Android SDK.
            implementation(libs.okhttp)
            implementation(libs.androidx.activity.compose)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            // Named directly rather than through `compose.uiTest`, which is deprecated and
            // whose accessor additionally required an `ExperimentalComposeLibrary` opt-in
            // that was itself deprecated. Two warnings, one line.
            implementation(libs.compose.ui.test)
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

/**
 * One JVM per test class.
 *
 * These are Compose *desktop* tests: each one stands up a real Skia surface on the AWT event
 * thread, and they share that thread with each other when they share a JVM. Sharing it turned
 * out to be a coin flip — with the whole suite in one process, a later test would deadlock
 * about two runs in three, the worker waiting inside `EventQueue.invokeAndWait` while the AWT
 * thread sat blocked on a Compose lock, and no test ever failed: the suite simply stopped.
 * Measured at 4 hangs in 6 runs, in a suite that is otherwise green in under half a minute.
 *
 * Forking per class costs a JVM start apiece and buys a suite that finishes every time, which
 * is the only kind that can guard anything. It is not a diagnosis of the underlying race —
 * that is Compose's to answer — it is a refusal to share the thread that races.
 */
tasks.withType<Test>().configureEach {
    forkEvery = 1
}

/**
 * The card art and the four player portraits. One deck, one set of faces: a second, plainer
 * deck for a second client would make them look like a different game rather than the same one.
 *
 * The `drawable/card_…xml` faces are **generated** — `python3 tools/make-card-faces.py` writes
 * both those and the SVG preview in `tools/card-faces/` from one source, so an edit made here is
 * lost on the next run. Change the generator.
 */
compose.resources {
    publicResClass = true
    packageOfResClass = "game.vinto.app.art"
    generateResClass = always
}

/**
 * The golden screenshots are excluded on CI, and the exclusion is not laziness.
 *
 * `ScreenshotTest` writes any missing golden from the live rendering and passes — the
 * bootstrap protocol described in `src/jvmTest/goldens/README.md`. On a runner that starts
 * from a clean checkout every golden is missing, so the suite would write eight PNGs into a
 * container that is about to be deleted and report success: a green check that asserted
 * nothing. Committing goldens would not fix it either, because glyph rasterization differs
 * between JVMs and hosts, so the maintainer's images and the runner's would disagree by
 * more than the tolerance allows.
 *
 * Every other Compose suite — the composition tests, `FullGameUiTest`, the contrast and
 * font-coverage checks — runs on CI, because those assert behaviour rather than pixels.
 *
 * Run them locally with `-Pscreenshots`, or on any machine where `CI` is unset.
 */
tasks.withType<Test>().configureEach {
    val screenshotsRequested = providers.gradleProperty("screenshots").isPresent
    val onCi = providers.environmentVariable("CI").isPresent
    if (onCi && !screenshotsRequested) {
        filter {
            excludeTestsMatching("game.vinto.app.ScreenshotTest")
            isFailOnNoMatchingTests = false
        }
    }
}

/**
 * The web client's shell is an input to the JVM test suite.
 *
 * `WebShellTest` reads `src/wasmJsMain/resources` off the disk — the page, its icons, the
 * manifest and the two Pages config files — because nothing else in this build ever looks at
 * them: `kmp-web` compiles the client and no job serves it. Without this, editing the page
 * leaves `jvmTest` UP-TO-DATE and the check silently does not run, which is a worse failure
 * than not having the check: a green tick over an unread file. Found by breaking the page on
 * purpose and watching the suite pass in 766 ms.
 */
tasks.withType<Test>().configureEach {
    inputs.dir(layout.projectDirectory.dir("src/wasmJsMain/resources"))
        .withPropertyName("webShell")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // And the hosting config, for the same reason and caught the same way: `WebShellTest`
    // asserts the deployed route matches `INVITE_HOST`, and without this the probe that
    // breaks the route passes in under a second because nothing invalidated the task.
    inputs.dir(layout.projectDirectory.dir("cloudflare"))
        .withPropertyName("webHostingConfig")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // And the browser source set, which `WasmFetchOptionsTest` reads. Third time this trap
    // has appeared: a suite that reads files off disk is UP-TO-DATE when those files change,
    // so it silently does not run and its probe "passes" in under a second. Any test that
    // reads a directory has to declare it.
    inputs.dir(layout.projectDirectory.dir("src/wasmJsMain/kotlin"))
        .withPropertyName("wasmSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // And the three files that carry the app's store identity, which `AppIdentityTest` reads
    // and none of which this module owns: the Android application's build script, the Xcode
    // project, and the release tooling's config. Fourth time — and this one was caught the
    // same way as the others, by reverting an iOS bundle id and watching the suite report
    // success in one second.
    listOf(
        "androidApp/build.gradle.kts" to "androidIdentity",
        "iosApp/iosApp.xcodeproj/project.pbxproj" to "appleIdentity",
        "vydanne.config.mjs" to "releaseIdentity",
    ).forEach { (path, name) ->
        inputs.file(rootProject.layout.projectDirectory.file(path))
            .withPropertyName(name)
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
}

/**
 * The desktop app, which is a development tool rather than a product.
 *
 * There is no desktop release and none is planned — this exists so a UI change can be looked
 * at in seconds instead of after an emulator boot, and because the sounds cannot be checked
 * from a headless test suite (§6i step 1 asks for exactly that and, until now, named
 * something that did not exist).
 *
 *     ./gradlew :composeApp:run
 */
compose.desktop {
    application {
        mainClass = "game.vinto.app.MainKt"
    }
}

/**
 * Where a crash is reported, as a build input rather than a source constant.
 *
 * It was `private const val SENTRY_DSN = ""` in `App.kt`, which meant crash reporting was off
 * in **every** build there has ever been — including the one that shipped. A DSN is not a
 * secret in the usual sense (its key is write-only: it can submit an event and cannot read
 * one back, and it has to be inside the app for the app to report at all), but it is still not
 * something to commit: what somebody could do with a stolen one is spend the project's quota.
 *
 * **The project's own DSN is the default**, at the product owner's direction. Defaulting to
 * empty meant every build any of us made reported nowhere, which is how a crash on opening an
 * online game came and went with nothing to look at. The trade is real and small: a DSN's key
 * is write-only — it can submit an event and cannot read one back — so what somebody could do
 * with this one is spend the project's Sentry quota, and Sentry's own guidance is that a
 * client DSN is not a secret. It is still overridable, so a fork or a separate environment can
 * point somewhere else without touching source:
 *
 *     ./gradlew :androidApp:assembleRelease -Pvinto.sentryDsn=https://key@host/1
 *     VINTO_SENTRY_DSN=https://key@host/1 ./gradlew :androidApp:assembleRelease
 *
 * Setting it to an empty string switches reporting off entirely, which is what a test that
 * must not talk to the network passes.
 *
 * DEPLOYMENT.md §7a is the maintainer's copy of this. The Worker's half is a wrangler secret
 * and is deliberately a different pipe with a different lifetime.
 */
// The project's Sentry project, for every client. See the note above for why this is in
// source and what it would cost somebody to misuse it.
val DEFAULT_SENTRY_DSN =
    "https://b72aaadb269f6ba420258c6e930b6f8f@o473632.ingest.us.sentry.io/4510118789251072"

abstract class GenerateBuildInfo : DefaultTask() {

    @get:Input
    abstract val sentryDsn: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val packageDir = outputDir.get().asFile.resolve("game/vinto/app")
        packageDir.mkdirs()
        packageDir.resolve("BuildInfo.kt").writeText(
            """
            |// Generated by :composeApp:generateBuildInfo — do not edit.
            |package game.vinto.app
            |
            |/**
            | * The Sentry DSN this build reports to, or empty for a build that reports nowhere.
            | *
            | * Supplied by `-Pvinto.sentryDsn=` or `VINTO_SENTRY_DSN`; see the task in
            | * composeApp/build.gradle.kts for why it is not a source constant.
            | */
            |internal const val SENTRY_DSN: String = "${sentryDsn.get().replace("\"", "\\\"")}"
            |
            """.trimMargin(),
        )
    }
}

val generateBuildInfo =
    tasks.register<GenerateBuildInfo>("generateBuildInfo") {
        description = "Writes the build-time constants a common source set cannot get from BuildConfig."
        sentryDsn.set(
            providers.gradleProperty("vinto.sentryDsn")
                .orElse(providers.environmentVariable("VINTO_SENTRY_DSN"))
                .orElse(DEFAULT_SENTRY_DSN),
        )
        outputDir.set(layout.buildDirectory.dir("generated/build-info/kotlin"))
    }

kotlin.sourceSets.commonMain.get().kotlin.srcDir(generateBuildInfo)

/**
 * What the Compose compiler thought of each composable, when asked.
 *
 * Off by default and switched on with `-PcomposeMetrics`, because it writes a file per
 * module per compilation and there is no reason to pay for that on every build.
 *
 * It is here because this client's web bundle is 3.7 MB gzipped — a number the product owner
 * accepted rather than liked (`PLATFORM-GATE.md`, design D1a) — and because a table that
 * animates a whole round recomposes a great deal. Both of those are guesses until something
 * measures them. The reports say which composables are skippable and which are not, and
 * which parameters are unstable; the usual answer for a codebase like this one is a handful
 * of types that Compose cannot prove stable across a module boundary.
 *
 *     ./gradlew :composeApp:assembleDebug -PcomposeMetrics
 *     # then read build/compose/reports/*-composables.txt
 *
 * Deliberately measurement only: no stability configuration file yet. Declaring a type stable
 * is a promise the compiler then trusts without checking, and making that promise about the
 * engine's state classes before reading a report would be guessing with the recomposition
 * correctness of the whole table as the stake.
 */
// NOTHING MAY FOLLOW THIS BLOCK. Statements after `composeCompiler { }` in this script are
// never executed — silently: the build succeeds, a `logger.lifecycle` after it prints
// nothing, and a `tasks.register` after it leaves a task Gradle then reports as "not found in
// project ':composeApp'". Bisected with probes on a run with the configuration cache off;
// every block before it runs, and every statement after it does not. Half an hour went into
// finding that out, so put new configuration **above** here.
composeCompiler {
    if (providers.gradleProperty("composeMetrics").isPresent) {
        reportsDestination.set(layout.buildDirectory.dir("compose/reports"))
        metricsDestination.set(layout.buildDirectory.dir("compose/metrics"))
    }
}

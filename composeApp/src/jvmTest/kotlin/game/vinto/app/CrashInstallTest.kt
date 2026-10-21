package game.vinto.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Crash reporting starts before the app draws, on every target.
 *
 * This reads source rather than behaviour, which is unusual and is the only thing that can
 * check the property. What it protects is an *ordering* across four entry points and four
 * runtimes: install the handler, then compose. It was the other way round for the life of the
 * branch — `installCrashHandler` sat in a `LaunchedEffect` inside a composable inside `App()`
 * — so every failure before the first frame happened with nothing listening, which is exactly
 * the window a crash on the launcher lives in. Nothing failed; there was simply no report.
 *
 * A runtime test cannot see this. Composing `App()` in a test installs the handler either way,
 * and the window that matters is the one before the harness gets control at all.
 *
 * The Android and iOS entry points are the two that most need it and the two that cannot be
 * run here — dl.google.com and a Mac respectively — which is the other half of the argument
 * for reading the file.
 */
class CrashInstallTest {

    /**
     * The file's statements, with its imports dropped.
     *
     * `import androidx.activity.compose.setContent` is a mention of `setContent` above every
     * line of the body, so a naive search finds the call site at character zero and every
     * ordering check passes. This test failed on exactly that the first time it ran, which is
     * a fair advertisement for its own usefulness.
     */
    private fun source(path: String): String {
        val file = File(path)
        assertTrue(file.exists(), "expected $path — an entry point moved and this went stale")
        return file.readLines().filterNot { it.trimStart().startsWith("import ") }.joinToString("\n")
    }

    /**
     * Each entry point, and the call it must make before the call that composes.
     *
     * Positions rather than mere presence: `Crashes.install` *somewhere* in the file is what
     * the old code effectively had.
     */
    @Test
    fun everyEntryPointInstallsTheReporterBeforeItComposes() {
        listOf(
            Entry("src/jvmMain/kotlin/game/vinto/app/Main.kt", "application {"),
            Entry("src/wasmJsMain/kotlin/game/vinto/app/Main.kt", "ComposeViewport"),
            // Compose's controller, inside the parent that turns the status bar (SystemBars.ios.kt).
            Entry("src/iosMain/kotlin/game/vinto/app/MainViewController.kt", "GameUIViewController {"),
        ).forEach { entry ->
            val text = source(entry.path)
            val installed = text.indexOf("Crashes.install")
            val composed = text.indexOf(entry.composes)

            if (installed < 0) fail("${entry.path} never installs the crash reporter")
            if (composed < 0) fail("${entry.path} no longer calls ${entry.composes}; this test is stale")
            assertTrue(
                installed < composed,
                "${entry.path} composes before it installs the reporter, so a crash on the " +
                    "way to the first frame is never reported",
            )
        }
    }

    /**
     * Android starts it earlier still: in the `Application`, which runs before any activity.
     *
     * There it starts `sentry-android`, and `MainActivity` — the entry point the list above used
     * to name — no longer installs anything. The SDK's own auto-init providers run before the
     * `Application` and are removed in the manifest; left in, they would start the SDK first, with
     * no DSN, on an emulator, with its defaults, and `VintoApplication` would be the second start.
     * The manifest the build merges is checked once, by `:androidApp:bundleRelease`; this is the
     * half a JVM can read.
     */
    @Test
    fun androidInstallsTheReporterInTheApplicationBeforeAnyActivity() {
        val application = source("../androidApp/src/main/kotlin/game/vinto/app/VintoApplication.kt")
        assertTrue(
            application.contains("Crashes.install"),
            "VintoApplication never installs the crash reporter",
        )

        val manifest = File("../androidApp/src/main/AndroidManifest.xml").readText()
        assertTrue(
            manifest.contains("android:name=\".VintoApplication\""),
            "the manifest does not name VintoApplication, so Android never runs it",
        )
        listOf("SentryInitProvider", "SentryPerformanceProvider").forEach { provider ->
            val declared = Regex("""<provider\s[^>]*io\.sentry\.android\.core\.$provider"[^>]*>""")
                .find(manifest)
                ?.value
            assertTrue(
                declared?.contains("tools:node=\"remove\"") == true,
                "the SDK's $provider is not removed, so it starts the SDK before VintoApplication does",
            )
        }

        val activity = source("../androidApp/src/main/kotlin/game/vinto/app/MainActivity.kt")
        assertTrue(!activity.contains("Crashes.install"), "MainActivity installs the reporter a second time")
    }

    /**
     * And the app scope carries a handler, so a background failure is reported rather than
     * printed.
     *
     * The fatal handler cannot see these: a coroutine that fails on a supervised scope leaves
     * the app running and doing nothing, which is the failure players describe as "it just sat
     * there" and the one that reaches nobody.
     */
    @Test
    fun theAppScopeReportsWhatFailsOnIt() {
        val app = source("src/commonMain/kotlin/game/vinto/app/App.kt")
        assertTrue(
            app.contains("rememberCoroutineScope { Crashes.handler() }"),
            "the app scope has no exception handler, so anything failing on it is silent",
        )
    }

    /**
     * And `App()` itself installs nothing.
     *
     * It used to, as a fallback for "a host that embeds `App()` directly" — and the only such
     * host is this test suite. Now that the DSN is a real one by default, a fallback there
     * would arm a live reporter inside every Compose test and post a CI runner's failures into
     * the project's Sentry. The four entry points are the contract; the case above is what
     * keeps them honest.
     */
    @Test
    fun composingTheAppDoesNotArmAReporter() {
        val app = source("src/commonMain/kotlin/game/vinto/app/App.kt")
        assertTrue(
            !app.contains("Crashes.install"),
            "App() installs the crash reporter, so the Compose suites report to Sentry",
        )
    }

    /**
     * And the capture build sends nothing, on a real phone included (CLAUDE.md, "The capture build").
     *
     * It is optimised like release, so on Android it would count as a player's build and on a phone
     * it would report. `VintoApplication` installs the reporter only under `BuildConfig.CRASH_REPORTS`,
     * which the `capture` build type alone turns off, and neither capture entry installs one of its own.
     */
    @Test
    fun theCaptureBuildNeverReports() {
        val application = source("../androidApp/src/main/kotlin/game/vinto/app/VintoApplication.kt")
        assertTrue(
            application.contains("if (BuildConfig.CRASH_REPORTS) Crashes.install"),
            "VintoApplication starts crash reports in the capture build too",
        )

        val gradle = File("../androidApp/build.gradle.kts").readText()
        val captureType = gradle.substringAfter("create(\"capture\")", "").substringBefore("\n        }")
        assertTrue(
            captureType.contains("""buildConfigField("boolean", "CRASH_REPORTS", "false")"""),
            "the capture build type does not turn CRASH_REPORTS off",
        )

        listOf(
            "../androidApp/src/capture/kotlin/game/vinto/app/CaptureActivity.kt",
            "src/captureIos/kotlin/game/vinto/app/CaptureViewController.kt",
        ).forEach { path ->
            assertTrue(!source(path).contains("Crashes.install"), "$path installs the crash reporter")
        }
    }

    private data class Entry(val path: String, val composes: String)
}

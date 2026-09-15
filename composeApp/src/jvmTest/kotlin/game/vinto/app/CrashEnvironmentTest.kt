package game.vinto.app

import game.vinto.app.crash.DEVELOPMENT
import game.vinto.app.crash.PRODUCTION
import game.vinto.app.crash.sentryEnvironment
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * A crash of ours is not a crash a player had.
 *
 * `Crashes.install` said `environment = "production"` whatever built it, so a crash from a
 * simulator, from the desktop window `:composeApp:run` opens, or from a CI job arrived in Sentry
 * indistinguishable from one a person on a phone had. It was proved by the first report that ever
 * arrived: a debug build on a simulator, filed under `production`.
 *
 * It matters now rather than in the abstract. TestFlight and the Play internal track are about to
 * put builds in other people's hands, and the first crash a stranger has wants to be findable
 * rather than buried among ours.
 *
 * **Which way an unknown build is filed is the whole decision**, and it goes the way that keeps
 * the players' bucket honest: only a build that can say it is a release is one. A release that
 * somehow could not tell would be filed with ours — findable, in the wrong place — where the
 * other way round quietly refills the bucket this exists to keep clean.
 */
class CrashEnvironmentTest {

    @Test
    fun onlyAReleaseBuildReportsAsProduction() {
        assertEquals(PRODUCTION, sentryEnvironment(release = true))
        assertEquals(DEVELOPMENT, sentryEnvironment(release = false))
    }

    /** The desktop window is a tool for looking at a UI change; nobody plays it. */
    @Test
    fun theDesktopBuildIsNeverAPlayers() {
        assertFalse(isReleaseBuild(), "a JVM build claimed to be a release")
        assertEquals(DEVELOPMENT, sentryEnvironment(), "a desktop crash is filed as a player's")
    }

    /**
     * Read off the source, because three of the four actuals cannot run here — the Android one
     * needs a device, the iOS one a Mac, the web one a browser — and a missing actual is a
     * compile error only on the target that misses it.
     */
    @Test
    fun everyTargetSaysWhetherItIsARelease() {
        listOf(
            "src/androidMain/kotlin/game/vinto/app/Platform.android.kt",
            "src/iosMain/kotlin/game/vinto/app/Platform.ios.kt",
            "src/jvmMain/kotlin/game/vinto/app/Platform.jvm.kt",
            "src/wasmJsMain/kotlin/game/vinto/app/Platform.wasmJs.kt",
        ).forEach { path ->
            val file = File(path)
            assertTrue(file.exists(), "expected $path — a platform file moved and this went stale")
            assertTrue(
                file.readText().contains("actual fun isReleaseBuild"),
                "$path does not say whether its build is a release",
            )
        }
    }

    /** And nothing says it for them. */
    @Test
    fun theReporterIsNotToldItIsProduction() {
        val crashes = File("src/commonMain/kotlin/game/vinto/app/crash/Crashes.kt").readText()
        assertFalse(
            crashes.contains("\"production\""),
            "Crashes.kt still names an environment rather than asking the build",
        )
    }

    /**
     * On Android the answer needs the application context, and the context arrives with storage.
     * `MainActivity` attaches it one line before installing the reporter; this is what makes that
     * ordering a property rather than a habit, since the wrong order files a player's crash as
     * one of ours and nothing would say so.
     */
    @Test
    fun androidAttachesItsStorageBeforeItInstallsTheReporter() {
        val path = "../androidApp/src/main/kotlin/game/vinto/app/MainActivity.kt"
        val text = File(path).readLines()
            .filterNot { it.trimStart().startsWith("import ") }
            .joinToString("\n")
        val attached = text.indexOf("AndroidStorage.attach")
        val installed = text.indexOf("Crashes.install")
        if (attached < 0) fail("$path no longer attaches storage; this test is stale")
        if (installed < 0) fail("$path never installs the crash reporter")
        assertTrue(
            attached < installed,
            "$path installs the reporter before the context it needs to know what built it",
        )
    }
}

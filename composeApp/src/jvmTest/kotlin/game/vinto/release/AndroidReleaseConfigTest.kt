package game.vinto.release

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The Android obligations that live only in build files and a manifest**, held to the same audit
 * as every other game in the portfolio (release-hardening A.4, 2026-10-05).
 *
 * Vinto does not use games-core, so it never inherited the checks the other four run, and the cost of
 * that is on the record: 1.1 (656) reached the closed track with a `java.time.Instant.now()` in the
 * crash reporter's timestamp, which ends the app on Android 7 the first time a report is written —
 * a lost socket is enough. Desugaring is on now (`androidApp/build.gradle.kts`), and this is what
 * keeps it on, together with R8, the backup rules, force-dark, the per-app language list, the
 * activity's `configChanges` and edge to edge without what Android 15 deprecated.
 *
 * The audit itself is [AndroidConfigAudit], a copy of games-core's (its header says why it is copied
 * and how to keep it in step). It reads files rather than behaviour, so a run after editing only a
 * build file or the manifest can be served UP-TO-DATE: before a release, run this with `--rerun`.
 * What it cannot see, a JVM never can — that is the API 24 gate's job (`npm run play:legacy`).
 */
class AndroidReleaseConfigTest {

    @Test
    fun theReleaseBuildIsShrunkDesugaredBackedUpAndSafeOnOldAndNewAndroid() {
        val complaints = AndroidConfigAudit.complaints(ANDROID_APP, PREFS, manifestDir = "src/main")
        assertTrue(complaints.isEmpty(), "Android release configuration:\n" + complaints.joinToString("\n"))
    }

    /**
     * The per-app language list matches the languages the game actually ships.
     *
     * The audit looks for those languages under the app module's own `src/commonMain/composeResources`,
     * which is where they are in every other game. Vinto's app module is `androidApp` and its strings
     * live in `composeApp`, so inside [theReleaseBuildIsShrunkDesugaredBackedUpAndSafeOnOldAndNewAndroid]
     * the comparison finds nothing to compare and passes in silence. Here it is pointed at the two halves
     * separately: the strings from `composeApp`, the manifest and `locales_config.xml` from `androidApp`.
     */
    @Test
    fun thePerAppLanguageListIsTheLanguagesComposeAppShips() {
        val strings = File(COMPOSE_APP, "src/commonMain/composeResources/values/strings.xml")
        assertTrue(strings.isFile, "${strings.path} moved, so the language comparison would check nothing")

        val complaints = AndroidPlatformAudit.localeConfigComplaints(
            COMPOSE_APP,
            manifestDir = ANDROID_APP.relativeTo(COMPOSE_APP).path + "/src/main",
        )
        assertTrue(complaints.isEmpty(), "per-app languages:\n" + complaints.joinToString("\n"))
    }

    /**
     * The backup rules name the file the app writes.
     *
     * The other games install their storage through games-core's `PrefsHost.install`, and their test
     * checks those calls agree. Vinto opens its one `SharedPreferences` file itself
     * (`AndroidStorage.android.kt`), so the same promise is checked on that call: a renamed file with
     * backup rules still naming the old one backs up nothing, silently, and the player finds out on a
     * new phone.
     */
    @Test
    fun theBackupRulesNameTheOneFileTheAppWrites() {
        val opened = listOf(File(COMPOSE_APP, "src/androidMain"), File(ANDROID_APP, "src/main"))
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" } }
            .flatMap { file -> OPENS_PREFS.findAll(file.readText()).map { it.groupValues[1] } }
            .toSet()
        assertEquals(setOf(PREFS), opened, "the app's SharedPreferences files and the backup rules disagree")
    }

    /**
     * Edge to edge, as Play checks it: `MainActivity` calls `enableEdgeToEdge()`, and nothing in the app's
     * Android code or themes uses what Android 15 deprecated for it. `themes.xml` set the two bar colours
     * until 2026-10-05, which put a line of OUR code in Play's "deprecated APIs or parameters for
     * edge-to-edge" warning; what is left there is androidx.activity's own, and accepted.
     */
    @Test
    fun theAppGoesEdgeToEdgeWithNothingAndroid15Deprecated() {
        val complaints = AndroidPlatformAudit.edgeToEdgeComplaints(ANDROID_APP, manifestDir = "src/main")
        assertTrue(complaints.isEmpty(), "edge to edge:\n" + complaints.joinToString("\n"))
    }

    /**
     * composeApp's Android half ships inside the app as games-core's does in the other games (`SystemBars`
     * turns the bar icons from there), so a deprecated window call in it is in Play's warning just the same.
     */
    @Test
    fun composeAppsAndroidHalfUsesNoneOfTheDeprecatedWindowApis() {
        val androidMain = File(COMPOSE_APP, "src/androidMain")
        assertTrue(androidMain.isDirectory, "${androidMain.path} moved, so this would check nothing")
        val complaints = AndroidPlatformAudit.deprecatedWindowApiComplaints(androidMain)
        assertTrue(complaints.isEmpty(), "deprecated window APIs:\n" + complaints.joinToString("\n"))
    }

    /** The check is not vacuous: the two theme items Vinto used to set are each named, a comment is not. */
    @Test
    fun theEdgeToEdgeCheckNamesTheBarColoursTheThemeUsedToSet() {
        withModule { module ->
            write(module, "src/main/kotlin/MainActivity.kt", "class MainActivity { fun f() = enableEdgeToEdge() }")
            write(
                module,
                "src/main/res/values/themes.xml",
                listOf(
                    """<resources><style name="Theme.Vinto">""",
                    """<item name="android:statusBarColor">@color/rail</item>""",
                    """<item name="android:navigationBarColor">@color/rail</item>""",
                    """<!-- <item name="android:navigationBarDividerColor">@color/rail</item> -->""",
                    """</style></resources>""",
                ).joinToString("\n"),
            )
            val complaints = AndroidPlatformAudit.edgeToEdgeComplaints(module, manifestDir = "src/main")
            assertEquals(2, complaints.size, complaints.joinToString("\n"))
            assertTrue(complaints.any { "statusBarColor" in it } && complaints.any { "navigationBarColor" in it })
        }
    }

    /** And an app that never calls `enableEdgeToEdge()` (or only in a comment) is refused. */
    @Test
    fun anAppThatNeverCallsEnableEdgeToEdgeIsRefused() {
        withModule { module ->
            write(module, "src/main/kotlin/MainActivity.kt", "class MainActivity { // enableEdgeToEdge()\n }")
            val complaints = AndroidPlatformAudit.edgeToEdgeComplaints(module, manifestDir = "src/main")
            assertTrue(complaints.single().contains("enableEdgeToEdge"), complaints.joinToString("\n"))
        }
    }

    private fun withModule(block: (File) -> Unit) {
        val module = createTempDirectory("vinto-audit").toFile()
        try {
            block(module)
        } finally {
            module.deleteRecursively()
        }
    }

    private fun write(module: File, path: String, text: String) {
        val file = File(module, path)
        file.parentFile.mkdirs()
        file.writeText(text)
    }

    private companion object {
        /**
         * `composeApp`, where Gradle runs this test; the fallback is for a run started from the
         * repository root.
         */
        val COMPOSE_APP: File = File("").absoluteFile.let {
            if (File(it, "src/commonMain/composeResources").isDirectory) it else File(it, "composeApp")
        }

        /** The Android application module: the manifest, `res/`, the release build type. */
        val ANDROID_APP: File = File(COMPOSE_APP.parentFile, "androidApp")

        /** The SharedPreferences file Vinto writes, without the extension (`AndroidStorage.android.kt`). */
        const val PREFS = "vinto"

        val OPENS_PREFS = Regex("""getSharedPreferences\(\s*"([^"]+)"""")
    }
}

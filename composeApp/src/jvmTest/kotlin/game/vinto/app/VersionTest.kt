package game.vinto.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The version on the screen is the version that shipped — on **both** stores.
 *
 * `VERSION` is written three times: here in common code, where the home screen and the settings
 * read it; in `androidApp/build.gradle.kts` as the `versionName` Play shows; and in
 * `iosApp/project.yml` as the `MARKETING_VERSION` the App Store shows. A Compose Multiplatform
 * common source set has no `BuildConfig` and Xcode reads neither of the others, so three copies
 * is the floor. This is what makes them stop being three copies and start being one, checked.
 *
 * **Read through one level of indirection on purpose.** The Android build named its version
 * inline until `b3c26e7` moved it into a `val` — at which point the old regex matched nothing,
 * `shipped` came back null, and the assertion failed with `expected:<null> but was:<1.0>`: a
 * message that reads like the *version* drifted when what drifted was this test's ability to
 * find it. Resolving a bare identifier through the `val` beside it covers both shapes, and
 * [versionIn] refuses rather than returning null when it recognises neither, so the next change
 * of shape says so in as many words.
 */
class VersionTest {

    @Test
    fun theVersionShownMatchesTheOnePlayShips() {
        // Gradle runs a module's tests from the module's own directory. `../androidApp`, not
        // this module: since AGP 9 the application half lives in its own module, because
        // `com.android.application` may no longer share one with the Kotlin Multiplatform
        // plugin. `versionName` went with it.
        val script = File("../androidApp/build.gradle.kts")
        assertTrue(script.exists(), "expected androidApp/build.gradle.kts beside composeApp")

        assertEquals(
            VERSION,
            versionIn(script.readText(), "versionName"),
            "the version on the home screen and the one in the .aab have drifted apart",
        )
    }

    @Test
    fun theVersionShownMatchesTheOneTheAppStoreShips() {
        val project = File("../iosApp/project.yml")
        assertTrue(project.exists(), "expected iosApp/project.yml beside composeApp")

        val shipped = Regex("""MARKETING_VERSION:\s*"?([0-9][^"\s#]*)"?""")
            .find(project.readText())
            ?.groupValues
            ?.get(1)

        assertEquals(
            VERSION,
            shipped,
            "the version on the home screen and the one Xcode stamps have drifted apart",
        )
    }

    /**
     * The value assigned to [key], following it through a `val` when the assignment is a bare
     * identifier rather than a string.
     *
     * One hop, not a general evaluator: `versionName = MARKETING_VERSION` beside
     * `val MARKETING_VERSION = "1.0"` is the shape the build actually has, and a reader that
     * chased arbitrary expressions would be a Kotlin interpreter with a bug in it.
     */
    private fun versionIn(script: String, key: String): String {
        val assigned = Regex("""\b$key\s*=\s*(?:"([^"]+)"|([A-Za-z_][A-Za-z0-9_]*))""")
            .find(script)
        assertTrue(assigned != null, "no `$key =` in the build script — has it been renamed?")

        assigned.groupValues[1].takeIf { it.isNotEmpty() }?.let { return it }

        val named = assigned.groupValues[2]
        val declared = Regex("""\bval\s+$named\s*=\s*"([^"]+)"""").find(script)
        assertTrue(declared != null, "`$key` is set from `$named`, which is not a string `val`")
        return declared.groupValues[1]
    }
}

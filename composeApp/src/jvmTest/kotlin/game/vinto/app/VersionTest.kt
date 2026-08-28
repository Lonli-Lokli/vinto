package game.vinto.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The version on the screen is the version that shipped.
 *
 * `VERSION` is written twice — here in common code, where the home screen and the settings can
 * read it, and in `build.gradle.kts` as the `versionName` Android puts in the package — because
 * a Compose Multiplatform common source set has no `BuildConfig`. Two copies of a number drift;
 * this is what makes them stop being two copies and start being one, checked.
 */
class VersionTest {

    @Test
    fun theVersionShownMatchesTheOneAndroidShips() {
        // Gradle runs a module's tests from the module's own directory.
        val script = File("build.gradle.kts")
        assertTrue(script.exists(), "expected composeApp/build.gradle.kts beside the test's cwd")

        val shipped = Regex("""versionName\s*=\s*"([^"]+)"""")
            .find(script.readText())
            ?.groupValues
            ?.get(1)

        assertEquals(
            shipped,
            VERSION,
            "the version on the home screen and the one in the APK have drifted apart",
        )
    }
}

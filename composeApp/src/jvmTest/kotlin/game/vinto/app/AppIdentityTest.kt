package game.vinto.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The one string both stores know this app by, held in three files that cannot see each other.
 *
 * `applicationId` in `androidApp/build.gradle.kts`, `PRODUCT_BUNDLE_IDENTIFIER` twice in the
 * Xcode project, and `bundleId` / `google.packageName` in `vydanne.config.mjs`. Nothing links
 * them, all five must be the same string, and every way they can drift is silent:
 *
 * * Change the Gradle one and iOS keeps shipping under the old identity — two apps, in two
 *   stores, that are supposed to be one product.
 * * Change either and forget vydanne, and the release tooling writes the listing for an app
 *   that does not exist. That failure surfaces as a 404 from App Store Connect during a
 *   release, which is the worst moment to be reading a config file.
 *
 * **It is also the one identifier that can never be corrected later.** Android treats a changed
 * `applicationId` as a different app: a rename after launch abandons every install, every
 * review and every rating, and no signing key or store setting undoes it. So the value is worth
 * a test now, while 9.10 has not shipped and changing it is still free.
 *
 * This deliberately says nothing about `namespace`, `packageOfResClass` or the Kotlin package,
 * which are all still `game.vinto.app.*`. Those are internal — the package R and BuildConfig
 * are generated into, and where the source lives — and AGP has never required them to match the
 * applicationId. Renaming 162 files to make two unrelated things look alike would be a large
 * diff with no effect on anything a player or a store can see.
 */
class AppIdentityTest {

    @Test
    fun androidAndAppleShipUnderTheSameIdentity() {
        assertEquals(ID, androidApplicationId(), "the Android applicationId is not $ID")

        val apple = appleBundleIds()
        assertTrue(apple.isNotEmpty(), "no PRODUCT_BUNDLE_IDENTIFIER in the Xcode project")
        apple.forEach { assertEquals(ID, it, "an Xcode build configuration still ships as $it") }
    }

    /** And the release tooling writes the listing for that same app rather than another one. */
    @Test
    fun theReleaseToolingNamesTheSameApp() {
        val config = read("vydanne.config.mjs")

        listOf("bundleId" to "Apple", "packageName" to "Play").forEach { (key, store) ->
            val found = Regex("""$key:\s*'([^']+)'""").find(config)?.groupValues?.get(1)
                ?: fail("vydanne.config.mjs has no $key — $store would have no app to write to")
            assertEquals(ID, found, "vydanne would write the $store listing for $found")
        }
    }

    /**
     * Two rules Android enforces at install time and nowhere earlier, both of which a
     * hand-edited identifier can break: at least two segments, and every segment a legal Java
     * identifier starting with a letter.
     */
    @Test
    fun theIdentityIsOneAndroidWillAccept() {
        val parts = ID.split(".")
        assertTrue(parts.size >= 2, "$ID has no dot; Android requires at least two segments")
        parts.forEach { part ->
            assertTrue(part.isNotEmpty(), "$ID has an empty segment")
            assertTrue(part.first().isLetter(), "segment \"$part\" of $ID does not start with a letter")
            assertTrue(
                part.all { it.isLetterOrDigit() || it == '_' },
                "segment \"$part\" of $ID is not a legal identifier",
            )
        }
    }

    // ------------------------------------------------------------------ the reading

    private fun androidApplicationId(): String {
        val found = Regex("""applicationId\s*=\s*"([^"]+)"""")
            .find(read("androidApp/build.gradle.kts"))
            ?: fail("no applicationId in androidApp/build.gradle.kts")
        return found.groupValues[1]
    }

    private fun appleBundleIds(): List<String> =
        Regex("""PRODUCT_BUNDLE_IDENTIFIER\s*=\s*([^;]+);""")
            .findAll(read("iosApp/iosApp.xcodeproj/project.pbxproj"))
            .map { it.groupValues[1].trim().trim('"') }
            .toList()

    /** The module directory is the working directory, but that has moved before now. */
    private fun read(relative: String): String {
        var here: File? = File(System.getProperty("user.dir"))
        while (here != null) {
            val candidate = File(here, relative)
            if (candidate.isFile) return candidate.readText()
            here = here.parentFile
        }
        fail("cannot find $relative from ${System.getProperty("user.dir")}")
    }

    private companion object {
        /**
         * Under the studio's own domain, beside the two hostnames the game already answers on
         * — `vinto.kupalinka.app` and `vinto-room.kupalinka.app`.
         */
        const val ID = "app.kupalinka.vinto"
    }
}

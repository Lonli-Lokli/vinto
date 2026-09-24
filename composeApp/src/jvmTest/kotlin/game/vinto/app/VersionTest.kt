package game.vinto.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The version on the screen is the version that store shipped — each store its own.
 *
 * **The two stores do not share a marketing version** (VERSIONING.md): iOS can be on 1.1 while
 * Android is still 1.0. So the app no longer carries one `VERSION` for all of them — that
 * constant, and the test that held it equal to both stores at once, forced exactly the lockstep
 * VERSIONING.md rules out. Each store build reads its own number at runtime instead
 * (`appVersion()`): iOS from its bundle, Android from its package. What is checked here is that
 * those reads land on the numbers the stores are given, and what the two storeless builds say.
 *
 * **Read through one level of indirection on purpose.** The Android build named its version
 * inline until `b3c26e7` moved it into a `val` — at which point the old regex matched nothing,
 * and the failure read like the *version* had drifted when what drifted was this test's ability
 * to find it. [versionIn] follows the `val`, and refuses rather than returning null when it
 * recognises neither shape.
 */
class VersionTest {

    /** The bundle read IS the App Store's number: Info.plist takes it from project.yml. */
    @Test
    fun theIphoneReadsTheVersionTheAppStoreShips() {
        val plist = File("../iosApp/iosApp/Info.plist").readText()
        val shown = Regex("""<key>CFBundleShortVersionString</key>\s*<string>([^<]*)</string>""")
            .find(plist)
            ?.groupValues
            ?.get(1)

        assertEquals("\$(MARKETING_VERSION)", shown, "Info.plist stopped taking the version from project.yml")
    }

    /**
     * The web and the desktop window have no store, and ship from master — so they carry the
     * newest version either store has, never a number neither of them has reached.
     */
    @Test
    fun theStorelessBuildsCarryTheNewestStoreVersion() {
        val newest = listOf(iosVersion(), androidVersion())
            .maxWith(compareBy<String>({ it.part(0) }, { it.part(1) }, { it.part(2) }))

        assertEquals(newest, WEB_VERSION, "WEB_VERSION is not the newer of the two store versions")
    }

    private fun iosVersion(): String {
        val project = File("../iosApp/project.yml")
        assertTrue(project.exists(), "expected iosApp/project.yml beside composeApp")
        val shipped = Regex("""MARKETING_VERSION:\s*"?([0-9][^"\s#]*)"?""").find(project.readText())
        assertTrue(shipped != null, "no MARKETING_VERSION in project.yml")
        return shipped.groupValues[1]
    }

    // Gradle runs a module's tests from the module's own directory. `../androidApp`, not this
    // module: since AGP 9 the application half lives in its own module, and `versionName` with it.
    private fun androidVersion(): String {
        val script = File("../androidApp/build.gradle.kts")
        assertTrue(script.exists(), "expected androidApp/build.gradle.kts beside composeApp")
        return versionIn(script.readText(), "versionName")
    }

    private fun String.part(i: Int): Int = split('.').getOrNull(i)?.toIntOrNull() ?: 0

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

    /**
     * Every build phase the spec declares is in the project Xcode actually builds.
     *
     * `iosApp.xcodeproj` is GENERATED from `project.yml` by xcodegen and committed beside it, so
     * the two drift the moment somebody edits the spec and does not regenerate. Nothing complains:
     * the stale project builds perfectly, and simply does less than it says it does.
     *
     * That is not hypothetical. **"Stamp build number" was declared and absent**, and it is the
     * phase whose whole job is to refuse a Release binary that cannot be traced to a commit. Its
     * own comment records the cost of not having it — Vodar 1.2 reached App Store Connect as build
     * 1, above a 131, and Apple accepted it because CFBundleVersion need only be unique within a
     * marketing version. Vinto then archived as build "1" the same way, and the guard written to
     * catch exactly that was the thing that had gone missing.
     *
     * Matching on the phase NAMES rather than on the script bodies: a name is what xcodegen writes
     * through verbatim, and comparing bodies would fail on whitespace and teach everyone to
     * regenerate the baseline instead of reading the diff.
     */
    @Test
    fun everyBuildPhaseTheSpecDeclaresIsInTheProjectXcodeBuilds() {
        val spec = File("../iosApp/project.yml")
        val generated = File("../iosApp/iosApp.xcodeproj/project.pbxproj")
        assertTrue(spec.exists(), "expected iosApp/project.yml beside composeApp")
        assertTrue(generated.exists(), "expected the generated iosApp.xcodeproj beside the spec")

        // `      - name: X` under pre/postBuildScripts. Indentation-anchored so a `name:` belonging
        // to a target or a scheme cannot be mistaken for a phase.
        val declared = Regex("""^ {6}- name: (.+)$""", RegexOption.MULTILINE)
            .findAll(spec.readText())
            .map { it.groupValues[1].trim() }
            .toList()
        assertTrue(declared.isNotEmpty(), "no build phases found in project.yml; this test is stale")

        val project = generated.readText()
        val missing = declared.filterNot { project.contains(it) }

        assertEquals(
            emptyList(),
            missing,
            "iosApp.xcodeproj is behind project.yml — these phases are declared and will not run. " +
                "Regenerate it: xcodegen generate --spec iosApp/project.yml --project iosApp",
        )
    }
}

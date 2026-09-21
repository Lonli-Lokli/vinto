package game.vinto.app

import game.vinto.app.crash.CrashReport
import game.vinto.app.crash.CrashSurface
import game.vinto.app.crash.SentryPlatform
import game.vinto.app.crash.crashEnvelope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A report that arrives is only worth having if it can be read.
 *
 * Reported by the app about itself — `MOVE_REFUSED in SOLO`, 2026-09-21 — and unreadable in
 * three separate ways at once, each of which had left no trace anywhere because every one of
 * them produces a perfectly valid event that Sentry accepts and files:
 *
 * - **`ol0.d in SourceFile [Line 26]`**, on a build whose R8 mapping was uploaded and whose
 *   `sentry-debug-meta.properties` names it. Sentry keys a mapping on a frame's `module`, and
 *   the whole obfuscated name was being sent as `function` with no `module` at all — so there
 *   was nothing for the mapping to rewrite, and every frame read `a.b.c` regardless.
 * - **No platform.** `platform` was `platformName()`, which returns the sentence *"Android 34"*
 *   for a person to read. Sentry's `platform` is a fixed vocabulary; anything outside it becomes
 *   `other`, and `other` is not a Java event — so the Java pipeline, which is what applies a
 *   ProGuard mapping, never ran on it either. One field, both symptoms.
 * - **No build.** `release` is `vinto@1.0` for every build ever made, so a report could not be
 *   told from one three weeks older, and neither could the mapping it should be read through.
 *
 * `Host`'s own docstring warned about the first of these: *"Not `platformName`. That returns
 * 'Android 34' … sentences for a crash report, and branching on them would mean parsing prose
 * that exists to be read by a person."* The crash reporter was branching on nothing and sending
 * the prose.
 */
class ACrashCanBeReadTest {

    @Test
    fun aMinifiedFrameCarriesTheModuleAMappingIsKeyedOn() {
        val item = envelope(frames = listOf("at ol0.d(SourceFile:26)")).split("\n").last()

        assertTrue(item.contains(""""module":"ol0""""), "no module, so no mapping applies:\n$item")
        assertTrue(item.contains(""""function":"d""""), "the method name is not on its own:\n$item")
    }

    /** A name with no package at all still goes out — as a function, with nothing invented. */
    @Test
    fun aFrameWithNoPackageIsSentAsItIs() {
        val item = envelope(frames = listOf("at main(Main.kt:3)")).split("\n").last()

        assertTrue(item.contains(""""function":"main""""), item)
        assertFalse(item.contains(""""module":"""""), "an empty module was invented:\n$item")
    }

    @Test
    fun theBuildNumberRidesAlongSoTwoBuildsOfOneVersionAreTellableApart() {
        val item = envelope().split("\n").last()
        assertTrue(item.contains(""""dist":"609""""), "no build number on the report:\n$item")
    }

    /**
     * And this target says `java`, which is the word that runs the pipeline.
     *
     * Not cosmetic and not a tag: Sentry picks the processing for an event from this field, and
     * applying an R8 mapping is part of the Java processing. A JVM or Android event that does not
     * say `java` is an event whose mapping is uploaded, named, and never read.
     */
    @Test
    fun thisTargetNamesItselfAsSentryUnderstandsIt() {
        assertEquals(SentryPlatform.JAVA, crashPlatform)
    }

    /**
     * And the revision, because the build number is a count and not a name.
     *
     * `dist` is the commit *count*, which identifies a revision only while the build came off
     * master with a clean tree — and a report has to be readable when it did not. With the sha
     * on it, a report opens against the code that produced it rather than the code of the day.
     */
    @Test
    fun theCommitItWasBuiltFromRidesAlongWithTheCount() {
        val item = envelope().split("\n").last()
        assertTrue(item.contains(""""commit":"f0efca97""""), "no revision on the report:\n$item")
    }

    /** The sentence for a person is still carried, where a person reads it. */
    @Test
    fun theHumanNameForTheMachineIsKeptAsATag() {
        val item = envelope(os = "Android 34").split("\n").last()
        assertTrue(item.contains(""""os":"Android 34""""), "the readable name was dropped:\n$item")
    }

    private fun assertFalse(condition: Boolean, message: String) = assertTrue(!condition, message)

    private fun envelope(
        frames: List<String> = listOf("CardStage.kt:610"),
        os: String = "JVM 17",
    ) = crashEnvelope(
        CrashReport(
            eventId = "deadbeef",
            sentAtIso = "2026-09-21T15:37:27Z",
            timestampSeconds = 1_758_469_047.0,
            platform = SentryPlatform.JAVA,
            release = "vinto@1.0",
            dist = "609",
            commit = "f0efca97",
            os = os,
            environment = "production",
            surface = CrashSurface.SOLO,
            type = "TableTrouble",
            message = "MOVE_REFUSED in SOLO",
            frames = frames,
        ),
    )
}

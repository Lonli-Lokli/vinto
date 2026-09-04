package game.vinto.app

import game.vinto.app.crash.CrashReport
import game.vinto.app.crash.CrashSurface
import game.vinto.app.crash.crashEnvelope
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A stack trace reaches Sentry as frames, not as a paragraph.
 *
 * Every line used to be sent as `{"filename": "<the whole raw line>"}`, and the first real report
 * showed what that costs. From issue 7710776149, verbatim:
 *
 *     Unknown function in at 5   Vinto.debug.dylib   0x1073b3077
 *     kfun:game.vinto.app.game.Verdict#internal + 7475  [Line: Unknown]  (Not in app)
 *
 * The frame naming the failing function was there all along, and Sentry could not see it — so it
 * titled the issue after `kotlin.Throwable#<init>`, the one frame every crash in this app will
 * ever share. Two unrelated bugs would have arrived looking like one issue.
 *
 * The lines below are real ones from that report and from a JVM run, not invented shapes.
 */
class CrashFramesTest {

    @Test
    fun aNativeFrameCarriesItsFunctionAddressAndImage() {
        val body = envelopeWith(
            "at 5   Vinto.debug.dylib                   0x1073b3077        " +
                "kfun:game.vinto.app.game.Verdict#internal + 7475",
        )

        assertTrue(body.contains(""""function":"kfun:game.vinto.app.game.Verdict#internal""""), body)
        assertTrue(body.contains(""""package":"Vinto.debug.dylib""""), body)

        // The address is the whole point of parsing a native frame: Sentry symbolicates by
        // looking THIS up in an uploaded dSYM. Without it, uploading symbols achieves nothing.
        assertTrue(body.contains(""""instruction_addr":"0x1073b3077""""), body)

        // The trailing "+ 7475" is an offset into the function; the absolute address already
        // implies it, so it must not end up glued to the function name.
        assertFalse(body.contains("7475\""), "the offset leaked into a field: $body")
    }

    @Test
    fun aJvmFrameCarriesItsFileAndLine() {
        val body = envelopeWith("at game.vinto.app.game.Verdict.invoke(Standings.kt:171)")

        assertTrue(body.contains(""""function":"game.vinto.app.game.Verdict.invoke""""), body)
        assertTrue(body.contains(""""filename":"Standings.kt""""), body)
        assertTrue(body.contains(""""lineno":171"""), body)
    }

    /** A JVM stack from a build with no debug info says `(Unknown Source)` and has no line. */
    @Test
    fun aJvmFrameWithoutALineStillCarriesItsFunction() {
        val body = envelopeWith("at game.vinto.app.Foo.bar(Unknown Source)")

        assertTrue(body.contains(""""function":"game.vinto.app.Foo.bar""""), body)
        assertFalse(body.contains(""""lineno""""), "invented a line number: $body")
    }

    /**
     * Ours is marked, everything else is marked as not — and both matter.
     *
     * Sentry names an issue after the topmost `in_app` frame and folds the rest away. An absent
     * `in_app` is "unknown" rather than "library", so the `false` on Kotlin's own frames is what
     * makes ours the one that surfaces.
     */
    @Test
    fun ourFramesAreMarkedAndTheRuntimeIsNot() {
        val ours = envelopeWith("at 5   Vinto.debug.dylib  0x1  kfun:game.vinto.app.game.Verdict#internal + 1")
        assertTrue(ours.contains(""""in_app":true"""), ours)

        // Kotlin's own frame — and note it sits in the SAME binary as ours on iOS, which is why
        // the image name cannot be what decides this.
        val theirs = envelopeWith("at 0   Vinto.debug.dylib  0x2  kfun:kotlin.Throwable#<init>(){} + 75")
        assertTrue(theirs.contains(""""in_app":false"""), theirs)
    }

    /** A line neither pattern claims is still sent, rather than silently dropped. */
    @Test
    fun anUnrecognisedLineSurvivesAsItAlwaysDid() {
        val body = envelopeWith("something the format changed under")

        assertTrue(body.contains(""""filename":"something the format changed under""""), body)
    }

    private fun envelopeWith(frame: String) = crashEnvelope(
        CrashReport(
            eventId = "e1",
            sentAtIso = "2026-09-04T00:00:00Z",
            timestampSeconds = 1.0,
            platform = "iOS 26.5",
            release = "vinto@1.0",
            environment = "production",
            surface = CrashSurface.MENU,
            type = "NoWhenBranchMatchedException",
            message = "no message",
            frames = listOf(frame),
        ),
    )
}

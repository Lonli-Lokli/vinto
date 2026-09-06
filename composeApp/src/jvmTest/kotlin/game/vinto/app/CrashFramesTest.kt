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

    /**
     * The mapping id rides along, because an uploaded mapping is ignored without it.
     *
     * Sentry will not apply `mapping.txt` to an event that does not name the mapping's uuid, so
     * this field is the difference between a readable release stack and `a.b.c`. The Sentry
     * Gradle plugin injects the uuid into the Android manifest and `proguardUuid()` reads it —
     * work the sentry-android SDK would do for us if this app had one.
     */
    @Test
    fun anAndroidReportNamesTheMappingItShouldBeReadThrough() {
        val body = crashEnvelope(report(uuid = "8c8d1f0e-0000-4000-8000-abcdefabcdef"))

        assertTrue(body.contains(""""debug_meta""""), body)
        assertTrue(body.contains(""""type":"proguard""""), body)
        assertTrue(body.contains(""""uuid":"8c8d1f0e-0000-4000-8000-abcdefabcdef""""), body)
    }

    /** Everywhere else — iOS, desktop, web, and any debug build — there is no mapping to name. */
    @Test
    fun aReportWithNoMappingSendsNoDebugMeta() {
        assertFalse(crashEnvelope(report(uuid = null)).contains("debug_meta"), "sent an empty image list")
    }

    /**
     * The web build's own shape, measured rather than guessed.
     *
     * These four lines were read out of Chrome's console from the real production bundle — the
     * one `wasmJsBrowserDistribution` writes, wasm-opt and all — by throwing on purpose at
     * startup and printing `stackTraceToString()`. They are what a browser actually hands the
     * reporter, and none of them matched either existing pattern: V8 puts a space before the
     * paren, so every web frame was arriving as `Unparsed` and every web crash would have been
     * titled after whichever line they all share.
     *
     * The Kotlin name is there at all only because the build keeps the wasm name section
     * (`binaryenArguments.add("-g")`). Without it this frame reads `wasm-function[15659]` and
     * there is nothing to name an issue after.
     */
    @Test
    fun aWasmFrameCarriesItsKotlinFunction() {
        val body = envelopeWith(
            "at <vinto-kmp:composeApp>.game.vinto.app.main " +
                "(http://localhost:8099/f57a9e63ad84e59b3a0e.wasm:wasm-function[15659]:0x564a6e)",
        )

        // The `<module>.` prefix is dropped: it repeats the wasm file, and leaving it on the
        // function would put it in front of every symbol Sentry groups by.
        assertTrue(body.contains(""""function":"game.vinto.app.main""""), body)
        assertTrue(body.contains(""""package":"f57a9e63ad84e59b3a0e.wasm""""), body)
        assertTrue(body.contains(""""instruction_addr":"0x564a6e""""), body)
        assertTrue(body.contains(""""in_app":true"""), body)
    }

    /** Kotlin's own wasm frames are marked not-ours, so ours is the one Sentry surfaces. */
    @Test
    fun theWasmRuntimeIsNotMarkedAsOurs() {
        val body = envelopeWith(
            "at <vinto-kmp:composeApp>.kotlin.Throwable.<init>_2656 " +
                "(http://localhost:8099/f57a9e63ad84e59b3a0e.wasm:wasm-function[1858]:0x201fe2)",
        )

        assertTrue(body.contains(""""function":"kotlin.Throwable.<init>_2656""""), body)
        assertTrue(body.contains(""""in_app":false"""), body)
    }

    /**
     * The JavaScript half of the same stack — the glue webpack minified.
     *
     * A column as well as a line, because that is what a source map is keyed on: Sentry cannot
     * apply the uploaded `composeApp.js.map` to a frame that names only a line.
     */
    @Test
    fun aBrowserJsFrameCarriesItsFileLineAndColumn() {
        val body = envelopeWith("at kotlin.createJsError (http://localhost:8099/composeApp.js:2:500036)")

        assertTrue(body.contains(""""function":"kotlin.createJsError""""), body)
        assertTrue(body.contains(""""filename":"http://localhost:8099/composeApp.js""""), body)
        assertTrue(body.contains(""""lineno":2"""), body)
        assertTrue(body.contains(""""colno":500036"""), body)
    }

    /** V8 writes a frame with no function as bare location, and it still says where it was. */
    @Test
    fun anAnonymousBrowserFrameStillSaysWhereItWas() {
        val body = envelopeWith("at http://localhost:8099/composeApp.js:2:531663")

        assertTrue(body.contains(""""filename":"http://localhost:8099/composeApp.js""""), body)
        assertTrue(body.contains(""""lineno":2"""), body)
        assertTrue(body.contains(""""colno":531663"""), body)
    }

    private fun report(uuid: String?) = CrashReport(
        eventId = "e1",
        sentAtIso = "2026-09-04T00:00:00Z",
        timestampSeconds = 1.0,
        platform = "Android 36",
        release = "vinto@1.0",
        environment = "production",
        surface = CrashSurface.MENU,
        type = "IllegalStateException",
        message = "boom",
        frames = listOf("at game.vinto.app.Foo.bar(Foo.kt:1)"),
        proguardUuid = uuid,
    )

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

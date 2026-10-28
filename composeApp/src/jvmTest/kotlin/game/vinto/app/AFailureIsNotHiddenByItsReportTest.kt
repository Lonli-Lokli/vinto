package game.vinto.app

import game.vinto.app.crash.CrashReporter
import game.vinto.app.crash.CrashSurface
import game.vinto.app.crash.SentryPlatform
import game.vinto.app.crash.relayedCrash
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.PrintWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A report names what went wrong, and reporting it never becomes a second failure.
 *
 * Sentry 7817278407, from the web on 1.5 (720): `RuntimeException: Exception while trying to
 * handle coroutine exception`, and nothing else. That sentence is kotlinx.coroutines saying that a
 * `CoroutineExceptionHandler` *threw* while it was handling a failure — it wraps what the handler
 * threw as the cause and the failure it was handed as a suppressed exception. The only handler on
 * that path that can throw something other than what it was given is the app's own, which is the
 * crash reporter. And the report kept neither half.
 *
 * The way the reporter throws on the web was measured, not guessed: a browser error that carries
 * no `stack` — Chromium's `DOMException` is one, which is what a refused `localStorage` throws —
 * reaches Kotlin/Wasm as a `JsException` whose `stackTraceToString()` throws
 * `NullPointerException`. The JVM cannot make that `JsException`, so the first two cases build a
 * failure that fails the same two ways a report reads it.
 */
class AFailureIsNotHiddenByItsReportTest {

    private val sent = mutableListOf<String>()

    private val reporter = CrashReporter(
        dsn = "https://abc123@o1.ingest.us.sentry.io/456",
        platform = SentryPlatform.JAVASCRIPT, dist = "720", commit = "a8995395", os = "Web (Kotlin/Wasm)",
        release = "vinto@1.5",
        environment = "production",
        scope = CoroutineScope(Dispatchers.Unconfined),
        now = { 1_793_219_253_000 },
        nowIso = { "2026-10-28T20:27:33Z" },
        surface = { CrashSurface.MENU },
        post = { _, _, body -> sent += body },
    )

    /** A failure whose stack cannot be printed, the way a stackless browser error cannot on Wasm. */
    private class Unprintable : IllegalStateException("The quota has been exceeded.") {
        override fun printStackTrace(s: PrintWriter) = throw NullPointerException()
    }

    /** And one that cannot even say what it is. */
    private class Unspeakable : IllegalStateException() {
        override val message: String get() = throw NullPointerException()
    }

    @Test
    fun aFailureWhoseStackCannotBeReadIsStillReported() {
        reporter.report(Unprintable())

        val exception = exceptions(sent.single()).last()
        assertEquals("Unprintable", exception.text("type"))
        assertEquals("The quota has been exceeded.", exception.text("value"))
    }

    @Test
    fun aFailureThatCannotSayWhatItIsIsStillReported() {
        reporter.report(Unspeakable())

        assertEquals("Unspeakable", exceptions(sent.single()).last().text("type"))
    }

    /**
     * A cause is half the story, and it used to be cut off: the frames were the first thirty lines
     * of `stackTraceToString()`, so a `Caused by:` arrived only when the stack above it was short,
     * and then as a "frame" whose file name was the sentence.
     */
    @Test
    fun whatCausedAFailureTravelsWithIt() {
        reporter.report(IllegalStateException("the table fell over", aCardWithNoRank()))

        val chain = exceptions(sent.single())
        assertEquals(listOf("IllegalArgumentException", "IllegalStateException"), chain.map { it.text("type") })
        assertEquals("a card with no rank", chain.first().text("value"))
        assertTrue(
            chain.first().frames().any { it.contains(OUR_MODULE) },
            "the cause arrived without the frames that say where it came from: ${chain.first()}",
        )
        assertTrue(
            chain.flatMap { it.frames() }.none { it.contains("Caused by") },
            "a cause was sent as a frame again",
        )
    }

    /**
     * The report itself, as the browser now hands it over: both halves, each with its frames.
     *
     * The trace is the shape Kotlin/Wasm 2.4 prints for kotlinx.coroutines' wrapper, read off V8:
     * a header, the engine's own header again, frames, then the suppressed failure indented and the
     * cause. The frames are this app's, placed where such a failure would put them.
     */
    @Test
    fun theBrowsersCoroutineReportNamesBothFailures() {
        reporter.report(
            relayedCrash(
                name = "RuntimeException",
                message = "Exception while trying to handle coroutine exception",
                stack = WRAPPED_ON_WASM,
                fallback = "uncaught error",
            ),
        )

        val chain = exceptions(sent.single())
        assertEquals(
            listOf("NullPointerException", "IllegalStateException", "RuntimeException"),
            chain.map { it.text("type") },
            "the handler's failure and the one it was handed should both arrive, the wrapper last",
        )
        assertEquals("the room never answered", chain[1].text("value"))
        val handed = chain[1].frames().single { it.contains("game.vinto.client.RemoteGame.run") }
        assertTrue(handed.contains(""""in_app":true"""), "the app's own frame was not marked as the app's: $handed")
        assertTrue(chain[0].frames().any { it.contains("game.vinto.app.crash.CrashReporter.envelopeFor") })
        val headers = listOf("the room never answered", "handle coroutine")
        assertTrue(
            chain.flatMap { it.frames() }.none { frame -> headers.any { frame.contains(it) } },
            "a header was sent as a frame",
        )
    }

    /** Firefox and Safari write a stack with no header; its first line is a frame and stays one. */
    @Test
    fun aStackThatStartsWithAFrameKeepsIt() {
        reporter.report(
            relayedCrash(
                name = "TypeError",
                message = "e is undefined",
                stack = "game.vinto.app.main@https://vinto.kupalinka.app/8e3e.wasm:wasm-function[15659]:0x564a6e\n",
                fallback = "uncaught error",
            ),
        )

        val only = exceptions(sent.single()).single()
        assertEquals("TypeError", only.text("type"))
        assertTrue(only.frames().single().contains("game.vinto.app.main"), "the only frame was taken for a header")
    }

    /** A promise rejected with a bare string has no name and no stack, and is still a report. */
    @Test
    fun aRejectionWithNothingButAStringIsStillNamed() {
        reporter.report(relayedCrash(name = "", message = "nope", stack = "", fallback = "unhandled rejection"))

        val only = exceptions(sent.single()).single()
        assertEquals("BrowserCrash", only.text("type"))
        assertEquals("nope", only.text("value"))
    }

    /** Built a frame below the test, because the JVM elides every frame a cause shares with its wrapper. */
    private fun aCardWithNoRank() = IllegalArgumentException("a card with no rank")

    private fun exceptions(envelope: String): List<JsonObject> =
        Json.parseToJsonElement(envelope.lines()[2])
            .jsonObject
            .getValue("exception")
            .jsonObject
            .getValue("values")
            .jsonArray
            .map { it.jsonObject }

    private fun JsonObject.text(key: String): String? = get(key)?.jsonPrimitive?.content

    private fun JsonObject.frames(): List<String> =
        get("stacktrace")?.jsonObject?.get("frames")?.jsonArray?.map { it.toString() }.orEmpty()

    private companion object {
        private const val OUR_MODULE = """"module":"game.vinto.app.AFailureIsNotHiddenByItsReportTest""""

        private const val WASM = "(https://vinto.kupalinka.app/8e3eab49a1da99ef8c43.wasm:wasm-function"

        val WRAPPED_ON_WASM = listOf(
            "kotlin.RuntimeException: Exception while trying to handle coroutine exception",
            "RuntimeException: Exception while trying to handle coroutine exception",
            "    at kotlin.createJsError (https://vinto.kupalinka.app/kotlin/vinto-kmp-composeApp.import-object.mjs:37:50)",
            "    at <vinto-kmp:composeApp>.kotlin.Throwable.<init> $WASM[2949]:0xa4c94)",
            "    at <vinto-kmp:composeApp>.kotlinx.coroutines.handleCoroutineException $WASM[40211]:0x9c1e2)",
            "    Suppressed: kotlin.IllegalStateException: the room never answered",
            "    IllegalStateException: the room never answered",
            "        at kotlin.createJsError (https://vinto.kupalinka.app/kotlin/vinto-kmp-composeApp.import-object.mjs:37:50)",
            "        at <vinto-kmp:composeApp>.kotlin.Throwable.<init> $WASM[2949]:0xa4c94)",
            "        at <vinto-kmp:composeApp>.game.vinto.client.RemoteGame.run $WASM[61402]:0x1d0a11)",
            "        ... and 3 more common stack frames skipped",
            "Caused by: NullPointerException: null",
            "    at kotlin.createJsError (https://vinto.kupalinka.app/kotlin/vinto-kmp-composeApp.import-object.mjs:37:50)",
            "    at <vinto-kmp:composeApp>.kotlin.wasm.internal.THROW_NPE $WASM[465]:0x7b872)",
            "    at <vinto-kmp:composeApp>.game.vinto.app.crash.CrashReporter.envelopeFor $WASM[53650]:0x1c2a3f)",
            "",
        ).joinToString("\n")
    }
}

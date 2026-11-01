package game.vinto.app

import game.vinto.app.crash.CrashReport
import game.vinto.app.crash.CrashReporter
import game.vinto.app.crash.CrashSurface
import game.vinto.app.crash.SentryPlatform
import game.vinto.app.crash.WasmImage
import game.vinto.app.crash.crashEnvelope
import game.vinto.app.crash.relayedCrash
import game.vinto.app.crash.wasmImageFrom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A web crash names the Kotlin file and line, not only the function.
 *
 * Sentry 7817278407's frames read `kotlin.Throwable.<init>_2763 in 8e3eab49a1da99ef8c43.wasm
 * [Line: Unknown]`. The name came from the module's name section; the line could only come from a
 * debug file, and Sentry looks one up for a frame only when the event says which module the frame
 * is in (`debug_meta.images`, a `wasm` image keyed by the module's `build_id`) and marks the frame
 * as a native one at an offset into it (`platform: native`, `addr_mode: rel:<image>`). The event
 * said neither, so no upload could ever have been used.
 *
 * The shape is `@sentry/wasm`'s, read from its source (`registry.js`, `index.js`); the debug id is
 * the `build_id` as Sentry's own reader derives it, checked against `symbolic`.
 */
class AWebCrashReadsBackToALineTest {

    private val image = WasmImage(
        codeFile = "https://vinto.kupalinka.app/8e3eab49a1da99ef8c43.wasm",
        buildId = "b9022426cc1147f7d9ef995a44aab2da",
    )

    private val ours = "at <vinto-kmp:composeApp>.game.vinto.app.crash.catchUncaught\$lambda.invoke " +
        "(https://vinto.kupalinka.app/8e3eab49a1da99ef8c43.wasm:wasm-function[53689]:0x78c3d4)"
    private val skia = "at <skiko>.SkCanvas::drawRect " +
        "(https://vinto.kupalinka.app/bfa5198fb2fe683c613a.wasm:wasm-function[812]:0x1f0a2)"

    @Test
    fun theEventNamesTheModuleItsFramesAreIn() {
        val event = event(report(frames = listOf(ours)))

        val images = assertNotNull(event["debug_meta"]?.jsonObject?.get("images")?.jsonArray, "no debug_meta in $event")
        val only = images.single().jsonObject
        assertEquals("wasm", only.text("type"))
        assertEquals(image.codeFile, only.text("code_file"))
        assertEquals("b9022426cc1147f7d9ef995a44aab2da", only.text("code_id"))
        assertEquals("b9022426cc1147f7d9ef995a44aab2da0", only.text("debug_id"))
    }

    @Test
    fun aFrameInThatModuleIsAnOffsetSentryCanLookUp() {
        val frame = frames(event(report(frames = listOf(ours)))).single()

        assertEquals("native", frame.text("platform"))
        assertEquals("rel:0", frame.text("addr_mode"))
        assertEquals("0x78c3d4", frame.text("instruction_addr"))
        assertEquals("game.vinto.app.crash.catchUncaught\$lambda.invoke", frame.text("function"))
    }

    /** Skia's module has no debug file, so its frames stay what they were rather than point at ours. */
    @Test
    fun aFrameInAnotherModuleIsLeftAlone() {
        val frame = frames(event(report(frames = listOf(skia)))).single()

        assertNull(frame["addr_mode"], "a frame from another module was pointed at ours: $frame")
        assertNull(frame["platform"])
    }

    /** A build that was not stamped (a development server) sends what it always did. */
    @Test
    fun withoutAnImageNothingChanges() {
        val event = event(report(frames = listOf(ours), images = emptyList()))

        assertNull(event["debug_meta"])
        assertNull(frames(event).single()["addr_mode"])
    }

    /** The image travels with every exception in the chain, not only the first. */
    @Test
    fun aCauseInTheModuleIsLookedUpToo() {
        val sent = mutableListOf<String>()
        val reporter = CrashReporter(
            dsn = "https://abc123@o1.ingest.us.sentry.io/456",
            platform = SentryPlatform.JAVASCRIPT, dist = "720", commit = "a8995395", os = "Web (Kotlin/Wasm)",
            release = "vinto@1.5",
            environment = "production",
            scope = CoroutineScope(Dispatchers.Unconfined),
            now = { 1 },
            nowIso = { "2026-10-28T20:27:33Z" },
            surface = { CrashSurface.MENU },
            images = { listOf(image) },
            post = { _, _, body -> sent += body },
        )
        val trace = listOf(
            "kotlin.RuntimeException: wrapped",
            "    at kotlinx.coroutines.x (a.js:1:2)",
            "Caused by: NullPointerException: null",
            ours,
        )
        reporter.report(relayedCrash("RuntimeException", "wrapped", trace.joinToString("\n"), "uncaught error"))

        val event = Json.parseToJsonElement(sent.single().lines()[2]).jsonObject
        val cause = event.getValue("exception").jsonObject.getValue("values").jsonArray.first().jsonObject
        val frame = cause.getValue("stacktrace").jsonObject.getValue("frames").jsonArray.single().jsonObject
        assertEquals("rel:0", frame.text("addr_mode"), "the cause's frame was not pointed at the module: $frame")
    }

    /** What the deploy writes into `index.html`, read back the way the page reads it. */
    @Test
    fun theDeploysTagIsReadIntoAnImage() {
        val read = wasmImageFrom("8e3eab49a1da99ef8c43.wasm b9022426cc1147f7d9ef995a44aab2da") {
            "https://vinto.kupalinka.app/$it"
        }
        assertEquals(image, read)
    }

    @Test
    fun aTagThatIsNotWhatTheDeployWritesIsIgnored() {
        listOf("", "8e3eab49a1da99ef8c43.wasm", "x.wasm not-hex-at-all-not-hex-at-all-!!", "x.wasm abc").forEach {
            assertNull(wasmImageFrom(it) { file -> "https://h/$file" }, "'$it' was read as an image")
        }
    }

    /**
     * Firefox and Safari write a frame as `name@url…`, not `at name (url…)`. They were sent as
     * unparsed text, so a Firefox crash could be neither symbolicated nor source-mapped.
     */
    @Test
    fun firefoxFramesAreReadToo() {
        val wasm = "game.vinto.app.main@https://vinto.kupalinka.app/8e3eab49a1da99ef8c43.wasm:wasm-function[15659]:0x564a6e"
        val script = "dispatch@https://vinto.kupalinka.app/composeApp.38cf5e2d285bef88.js:2:500036"
        val anonymous = "@https://vinto.kupalinka.app/composeApp.38cf5e2d285bef88.js:2:531663"

        val (first, second, third) = frames(event(report(frames = listOf(wasm, script, anonymous)))).reversed()
        assertEquals("game.vinto.app.main", first.text("function"))
        assertEquals("0x564a6e", first.text("instruction_addr"))
        assertEquals("rel:0", first.text("addr_mode"))
        assertEquals("dispatch", second.text("function"))
        assertEquals("https://vinto.kupalinka.app/composeApp.38cf5e2d285bef88.js", second.text("filename"))
        assertEquals("500036", second.text("colno"))
        assertFalse(third.containsKey("function"))
        assertEquals("531663", third.text("colno"))
        assertTrue(listOf(first, second, third).none { it.text("filename")?.contains("@") == true })
    }

    private fun report(frames: List<String>, images: List<WasmImage> = listOf(image)) = CrashReport(
        eventId = "e1",
        sentAtIso = "2026-10-28T20:27:33Z",
        timestampSeconds = 1.0,
        platform = SentryPlatform.JAVASCRIPT,
        release = "vinto@1.5",
        dist = "720",
        commit = "a8995395",
        os = "Web (Kotlin/Wasm)",
        environment = "production",
        surface = CrashSurface.MENU,
        type = "RuntimeException",
        message = "Exception while trying to handle coroutine exception",
        frames = frames,
        images = images,
    )

    private fun event(report: CrashReport): JsonObject =
        Json.parseToJsonElement(crashEnvelope(report).lines()[2]).jsonObject

    private fun frames(event: JsonObject): List<JsonObject> =
        event
            .getValue("exception")
            .jsonObject
            .getValue("values")
            .jsonArray
            .last()
            .jsonObject
            .getValue("stacktrace")
            .jsonObject
            .getValue("frames")
            .jsonArray
            .map { it.jsonObject }

    private fun JsonObject.text(key: String): String? = get(key)?.jsonPrimitive?.content
}

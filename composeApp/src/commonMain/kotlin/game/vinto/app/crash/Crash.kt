package game.vinto.app.crash

/**
 * Crash reporting for the clients: what a report is allowed to contain, and how it is sent.
 *
 * The room's half of this is `worker/cloudflare/sentry.mjs`, and this is deliberately its
 * mirror rather than its cousin — the same DSN parsing, the same scrubbing rules, the same
 * envelope. Two implementations because the two runtimes share no code, one set of rules
 * because a room code leaking from a phone is exactly as bad as one leaking from the edge.
 *
 * Everything here is pure and target-independent, so it is tested on the JVM and true on all
 * four. The only platform-specific part is *when* a crash is noticed, which is
 * [installCrashHandler].
 */

/** A DSN split into the two things a POST needs. */
data class Dsn(val key: String, val url: String)

/**
 * Reads a DSN, or answers null.
 *
 * A DSN looks like `https://<key>@<host>/<projectId>`. The key is **write-only** — it can
 * submit events and cannot read them — which is the only reason it may sit inside an app
 * anybody can unzip. Null for anything unparseable, so a typo switches reporting off rather
 * than throwing on the path that was already going wrong.
 */
fun parseDsn(dsn: String?): Dsn? {
    if (dsn.isNullOrBlank()) return null

    val scheme = dsn.substringBefore("://", missingDelimiterValue = "")
    val rest = dsn.substringAfter("://", missingDelimiterValue = "")
    if (scheme.isEmpty() || rest.isEmpty()) return null

    val key = rest.substringBefore('@', missingDelimiterValue = "")
    val hostAndPath = rest.substringAfter('@', missingDelimiterValue = "")
    if (key.isEmpty() || hostAndPath.isEmpty()) return null

    val host = hostAndPath.substringBefore('/', missingDelimiterValue = "")
    val projectId = hostAndPath.substringAfter('/', missingDelimiterValue = "")
    if (host.isEmpty() || projectId.isEmpty()) return null

    return Dsn(key = key, url = "$scheme://$host/api/$projectId/envelope/")
}

private val roomInUrl = Regex("""([?&]room=)[A-Za-z0-9]+""", RegexOption.IGNORE_CASE)

// The `\\?` before every quote is not decoration. Scrubbing runs over the *serialised*
// event, so by the time these patterns see a room code the JSON escaping has already turned
// `room: "7KQ2MP"` into `room: \"7KQ2MP\"` — and a pattern that expects a bare quote then
// matches nothing and reports clean. That is the worst possible failure for a scrubber: it
// is silent, and it only happens for the values that came from a stack trace, which is
// exactly where a secret ends up.
private val roomInText = Regex("""\b(room|code)\s*[:=]\s*\\?"?[A-Z0-9]{6}""", RegexOption.IGNORE_CASE)
private val tokenish = Regex(
    """(\\?["']?token\\?["']?\s*[:=]\s*\\?"?)[A-Za-z0-9_-]{8,}""",
    RegexOption.IGNORE_CASE,
)
private val ipv4 = Regex("""\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b""")

/**
 * Removes anything that could identify a player or hand over a seat.
 *
 * Applied to the whole serialised report rather than to named fields, because the field that
 * leaks is always the one nobody thought to name. A room code is the specific hazard: it is a
 * shared secret that travels in a URL, so an unscrubbed message publishes one into a store
 * that people read.
 */
fun scrubReport(text: String): String = text
    .replace(roomInUrl) { "${it.groupValues[1]}<redacted>" }
    .replace(roomInText) { "${it.groupValues[1]}=<redacted>" }
    .replace(tokenish) { "${it.groupValues[1]}<redacted>" }
    .replace(ipv4, "<redacted>")

/** Where a crash happened, coarsely. Never which room, never which player. */
enum class CrashSurface { SOLO, ONLINE, LESSON, MENU }

/**
 * Where in a game the app was, for a crash report.
 *
 * The mirror of the room's `roomContext` in `sentry.mjs`, and for the same reason: without it
 * a report says only that *a* client on *a* surface failed, which is a sentence rather than
 * an address. With it a report names the deal — the same `gameId` the bug-report control puts
 * in an exported recording — and roughly where in the game it was.
 *
 * Three fields and no fourth. **No room code**: a code is a join credential, [scrubReport]
 * would strip one that arrived through a stack trace, and this is the same rule applied on
 * purpose before anything is built. **No nickname, no seat, no device**: a seat is not a
 * person and this is the pipe that quietly grows a user record if nobody says otherwise.
 *
 * `turn` rather than an action index because it is what the *view* carries, and the view is
 * the one thing a local game and an online one hold identically — a crash address that only
 * worked in solo play would be missing from exactly the sessions that are hardest to
 * reproduce.
 */
data class CrashPlace(
    val gameId: String? = null,
    val round: Int? = null,
    val turn: Int? = null,
) {
    val isEmpty: Boolean get() = gameId == null && round == null && turn == null
}

/**
 * The one envelope Sentry's ingest endpoint takes: three lines of JSON.
 *
 * Built by hand for the reason `sentry.mjs` gives and one more: the wasm client is the target
 * with no size headroom left, and this is the whole of what an SDK would do for a Kotlin
 * exception. See `design.md` §A9 for the measurement that settled it.
 *
 * There is **no `user` object and no device id**, ever. Vinto has no accounts, a seat is not
 * a person, and a crash report is the pipe that quietly grows one if nobody says otherwise.
 */
data class CrashReport(
    val eventId: String,
    val sentAtIso: String,
    val timestampSeconds: Double,
    val platform: String,
    val release: String,
    val environment: String,
    val surface: CrashSurface,
    val type: String,
    val message: String,
    val frames: List<String> = emptyList(),
    val place: CrashPlace = CrashPlace(),
    /**
     * The id of the R8 mapping this build was minified with, on Android and nowhere else.
     *
     * Sentry applies a mapping only to an event that names it, so without this the uploaded
     * `mapping.txt` is never used and a release stack stays `a.b.c`. Null everywhere else, and
     * on a debug build, where nothing is minified.
     */
    val proguardUuid: String? = null,
)

fun crashEnvelope(report: CrashReport): String = with(report) {
    val body = buildString {
        append("""{"event_id":"""").append(eventId).append("""","timestamp":""").append(timestampSeconds)
        append(""","platform":"""").append(platform).append('"')
        append(""","level":"error","logger":"vinto-app"""")
        append(""","release":"""").append(release).append('"')
        append(""","environment":"""").append(environment).append('"')
        append(""","tags":{"surface":"""").append(surface.name).append(""""}""")
        // Omitted entirely when there is nothing to say, rather than sent as three nulls: an
        // `extra` block that is always present teaches a reader to skim past it.
        if (!place.isEmpty) {
            append(""","extra":{""")
            val parts = buildList {
                place.gameId?.let { add(""""gameId":""" + json(it)) }
                place.round?.let { add(""""round":$it""") }
                place.turn?.let { add(""""turn":$it""") }
            }
            append(parts.joinToString(","))
            append('}')
        }
        append(""","exception":{"values":[{"type":""").append(json(type))
        append(""","value":""").append(json(message))
        if (frames.isNotEmpty()) {
            append(""","stacktrace":{"frames":[""")
            // Sentry wants the newest frame last; a Kotlin stack trace is newest first.
            frames.asReversed().forEachIndexed { index, frame ->
                if (index > 0) append(',')
                appendFrame(parseFrame(frame))
            }
            append("""]}""")
        }
        append("""}]}}""")
        // Which mapping to read this stack through. Sentry ignores an uploaded mapping unless the
        // event names its uuid here, which is why the plugin injects one into the manifest.
        proguardUuid?.let {
            append(""","debug_meta":{"images":[{"type":"proguard","uuid":""")
            append(json(it)).append("""}]}""")
        }
    }

    val header = """{"event_id":"$eventId","sent_at":"$sentAtIso"}"""
    header + "\n" + """{"type":"event"}""" + "\n" + scrubReport(body)
}

/**
 * One frame, in the fields Sentry reads for the shape it turned out to be.
 *
 * `in_app` is set on every frame rather than only on ours, because Sentry treats an ABSENT
 * `in_app` as unknown and a `false` as "library" — and it is the false ones that let it fold
 * Kotlin's and Compose's internals away and name the issue after our own topmost frame. The first
 * real report was titled after `kotlin.Throwable#<init>`, which is where every crash begins and
 * therefore tells nobody anything.
 *
 * A native frame carries `instruction_addr` and `package`, which is the pair a dSYM lookup needs;
 * without them an uploaded dSYM has nothing to match against. A JVM frame carries the file and
 * line, which is all Sentry needs when the build is not minified — and, once R8 is on, what a
 * mapping file is applied to.
 */
private fun StringBuilder.appendFrame(frame: CrashFrame) {
    append('{')
    when (frame) {
        is CrashFrame.Jvm -> {
            append(FUNCTION_KEY).append(json(frame.function))
            append(""","filename":""").append(json(frame.file))
            frame.line?.let { append(""","lineno":""").append(it) }
        }

        is CrashFrame.Native -> {
            append(FUNCTION_KEY).append(json(frame.function))
            append(""","package":""").append(json(frame.image))
            append(""","instruction_addr":""").append(json(frame.address))
        }

        // The name is the whole value here: with the wasm name section kept, this frame reads
        // `game.vinto.app.main`, and without it there is nothing to name an issue after. The
        // address is carried for completeness rather than for lookup — no wasm debug files are
        // uploaded, because the names travel in the module itself.
        is CrashFrame.Wasm -> {
            append(FUNCTION_KEY).append(json(frame.function))
            append(""","package":""").append(json(frame.module))
            append(""","instruction_addr":""").append(json(frame.address))
        }

        // Line AND column, because that is the pair a JavaScript source map is keyed on.
        is CrashFrame.Script -> {
            frame.function?.let { append(FUNCTION_KEY).append(json(it)).append(',') }
            append(""""filename":""").append(json(frame.file))
            frame.line?.let { append(""","lineno":""").append(it) }
            frame.column?.let { append(""","colno":""").append(it) }
        }

        // Nothing was understood, so the whole line goes where it always went. Sentry shows it
        // verbatim, which is worse than a parsed frame and much better than a dropped one.
        is CrashFrame.Unparsed -> append(""""filename":""").append(json(frame.raw))
    }
    append(""","in_app":""").append(frame.isOurs())
    append('}')
}

/** The auth header Sentry's ingest wants. The key is write-only; see [parseDsn]. */
fun sentryAuth(key: String): String =
    "Sentry sentry_version=7, sentry_key=$key, sentry_client=vinto-app/1"

/** Written by every frame shape that has a name to give, which is all of them but a bare URL. */
private const val FUNCTION_KEY = """"function":"""

/** The shortest Unicode escape JSON accepts, so a control character is padded to it. */
private const val UNICODE_ESCAPE_DIGITS = 4
private const val HEX = 16

/** Minimal JSON string escaping — the values here are messages and file names. */
private fun json(value: String): String = buildString {
    append('"')
    for (character in value) {
        when {
            character == '"' -> append("\\\"")
            character == '\\' -> append("\\\\")
            character == '\n' -> append("\\n")
            character == '\r' -> append("\\r")
            character == '\t' -> append("\\t")
            character < ' ' -> {
                append("\\u")
                append(character.code.toString(HEX).padStart(UNICODE_ESCAPE_DIGITS, '0'))
            }
            else -> append(character)
        }
    }
    append('"')
}

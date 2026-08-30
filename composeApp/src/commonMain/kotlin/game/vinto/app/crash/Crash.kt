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
)

fun crashEnvelope(report: CrashReport): String = with(report) {
    val body = buildString {
        append("""{"event_id":"""").append(eventId).append("""","timestamp":""").append(timestampSeconds)
        append(""","platform":"""").append(platform).append('"')
        append(""","level":"error","logger":"vinto-app"""")
        append(""","release":"""").append(release).append('"')
        append(""","environment":"""").append(environment).append('"')
        append(""","tags":{"surface":"""").append(surface.name).append(""""}""")
        append(""","exception":{"values":[{"type":""").append(json(type))
        append(""","value":""").append(json(message))
        if (frames.isNotEmpty()) {
            append(""","stacktrace":{"frames":[""")
            // Sentry wants the newest frame last; a Kotlin stack trace is newest first.
            frames.asReversed().forEachIndexed { index, frame ->
                if (index > 0) append(',')
                append("""{"filename":""").append(json(frame)).append('}')
            }
            append("""]}""")
        }
        append("""}]}}""")
    }

    val header = """{"event_id":"$eventId","sent_at":"$sentAtIso"}"""
    header + "\n" + """{"type":"event"}""" + "\n" + scrubReport(body)
}

/** The auth header Sentry's ingest wants. The key is write-only; see [parseDsn]. */
fun sentryAuth(key: String): String =
    "Sentry sentry_version=7, sentry_key=$key, sentry_client=vinto-app/1"

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

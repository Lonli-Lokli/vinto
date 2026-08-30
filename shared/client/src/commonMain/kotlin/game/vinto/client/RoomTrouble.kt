package game.vinto.client

/**
 * Why a call to the room service did not work, in the one vocabulary all four platforms share.
 *
 * Every connector fails its own way — an `IOException` on Android, a `WebSocketHandshake`
 * failure on the JVM, a wrapped `NSError` on iOS, a rejected `fetch` in a browser — and until
 * now the screens above them treated all of it as one thing: a caught `Exception` whose
 * `message` was shown to the player. That message is written for a developer when it exists at
 * all, and when the service answered with a status rather than a socket error there *was* no
 * exception: the connectors never looked at the status code, so a 404 or a 503 was handed
 * straight to a JSON parser and the player was shown "Unexpected JSON token at offset 0".
 *
 * This is the middle ground. The platform keeps its own transport and its own exceptions; what
 * it must do is say which of these six things happened, so a screen can decide whether to
 * offer another go and a person can be told something true.
 */
enum class RoomTrouble {
    /** The service could not be reached at all: no signal, aeroplane mode, DNS, a dead host. */
    OFFLINE,

    /** A code nobody has issued, or a room that has since gone. Trying again will not help. */
    NO_SUCH_ROOM,

    /** The room service is deliberately not open (`ROOM_OPEN`), or is shutting down. */
    CLOSED,

    /** Too many people, too fast: this room is full, or the service is shedding load. */
    BUSY,

    /** The service understood and said no — a bad nickname, a malformed request. */
    REFUSED,

    /** The service broke, or answered something that is not the protocol. */
    BROKEN,
}

/**
 * A failure a screen can act on.
 *
 * [permanent] is the property that matters most and the one nothing had before: `RemoteRoom`'s
 * reconnect loop caught every exception and backed off, so a mistyped room code and a tunnel
 * were the same thing — a spinner, for ever, with no way to tell whether waiting would help.
 */
class RoomServiceException(
    val trouble: RoomTrouble,
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause) {

    /** True when trying again cannot change the answer, so a screen must stop and say so. */
    val permanent: Boolean
        get() = when (trouble) {
            RoomTrouble.NO_SUCH_ROOM, RoomTrouble.CLOSED, RoomTrouble.REFUSED -> true
            RoomTrouble.OFFLINE, RoomTrouble.BUSY, RoomTrouble.BROKEN -> false
        }
}

/**
 * What an HTTP status means here, or null for one that is fine.
 *
 * The room service already answers with the right statuses — 404 for a code it never issued,
 * 503 when it is closed, 429 for a room that is full, 413 for a body over the cap. All four
 * connectors used to discard every one of them.
 */
fun troubleFor(status: Int): RoomTrouble? = when {
    status in OK_RANGE -> null
    status == NOT_FOUND -> RoomTrouble.NO_SUCH_ROOM
    status == TOO_MANY -> RoomTrouble.BUSY
    status == UNAVAILABLE -> RoomTrouble.CLOSED
    status in SERVER_RANGE -> RoomTrouble.BROKEN
    else -> RoomTrouble.REFUSED
}

/**
 * The body of a good answer, or the trouble the status names.
 *
 * Every connector's two REST calls go through this, so a status means the same thing on four
 * platforms and a screen has one thing to handle. The service's own words are carried through
 * when they are short enough to be a sentence — it answers `no such room` and
 * `the room service is closed` in plain text, which are better than anything invented here —
 * and a long body (an HTML error page from something in front of the service) is dropped,
 * because a paragraph of markup in a toast is worse than no detail at all.
 */
fun requireOk(status: Int, body: String): String {
    val trouble = troubleFor(status) ?: return body
    throw RoomServiceException(trouble, said(body) ?: "the room service answered $status")
}

/** Whatever the service said, if it said something a person could read. */
private fun said(body: String): String? {
    val text = jsonError(body) ?: body
    val line = text.trim().lineSequence().firstOrNull()?.trim().orEmpty()
    return line.takeIf { it.isNotEmpty() && it.length <= MAX_SAID && !it.startsWith("<") }
}

/**
 * The `error` of a `{"error":"..."}` body, without decoding it as JSON.
 *
 * Deliberately a substring rather than a parse: this runs on the path where the answer is
 * already known not to be what was expected, and asking a strict parser to read a body that
 * might be an HTML error page would throw a second exception over the first.
 */
private fun jsonError(body: String): String? {
    val key = body.indexOf("\"error\"")
    if (key < 0) return null
    val opened = body.indexOf('"', body.indexOf(':', key) + 1)
    if (opened < 0) return null
    val closed = body.indexOf('"', opened + 1)
    return if (closed < 0) null else body.substring(opened + 1, closed)
}

private val OK_RANGE = 200..299
private val SERVER_RANGE = 500..599
private const val NOT_FOUND = 404
private const val TOO_MANY = 429
private const val UNAVAILABLE = 503

/** Long enough for the service's own sentences, short enough that markup is excluded. */
private const val MAX_SAID = 120

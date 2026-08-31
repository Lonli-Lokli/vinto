package game.vinto.app.link

import game.vinto.protocol.looksLikeRoomCode

/**
 * Invitations, as links rather than as codes to transcribe.
 *
 * The lobby has always shown the code — monospaced and letterspaced, because it is the one
 * string in this app somebody reads down a telephone — and the share sheet has always sent
 * it. What neither did was *open the app*: an invite arrived as six characters and a hostname,
 * and the person on the other end had to install the game, find the online screen and type
 * them in. Every step of that is somewhere to lose them, and the funnel this measures
 * (`FunnelStep.INVITE_SHARED` → `ROOM_JOINED`) is exactly the one it was built to show.
 *
 * Everything here is pure, so the parsing is the same on all four platforms and testable
 * without any of them. The platform half is only ever "here is a URL somebody opened".
 */

/** Where the web client lives; the same host the invite names. */
const val INVITE_HOST: String = "vinto.kupalinka.app"

/** The path an invite link carries, kept short because people read these aloud too. */
const val INVITE_PATH: String = "/r/"

/**
 * The app's own scheme, for the case an https link cannot be claimed.
 *
 * App Links and Universal Links are the good path: they open the app *and* fall back to the
 * website for somebody who does not have it. But both require a file hosted on the domain
 * (§1f), and neither is verified on a device where that file has not been published yet. The
 * custom scheme has no such requirement and no such fallback, so it is the belt rather than
 * the braces: it works from a QR code or a message between two people who both have the app.
 */
const val INVITE_SCHEME: String = "vinto"

/** The link to send somebody. */
fun inviteLink(code: String): String = "https://$INVITE_HOST$INVITE_PATH${code.uppercase()}"

/**
 * The room code an opened link names, or null.
 *
 * Deliberately generous about *shape* and strict about *content*. It accepts the https link,
 * the custom scheme, a bare code somebody pasted, and any of them with the wrong case or
 * surrounding whitespace — because all of those are things a real person will actually hand
 * the app, and refusing them teaches nothing. What it will not do is return something that
 * could not have been issued: [looksLikeRoomCode] is the same check the Worker applies before
 * it wakes the one Durable Object that knows every live room, and the client agreeing with it
 * means a mistyped link fails on the device rather than costing a round trip.
 *
 * It is **not** a security boundary and does not pretend to be one. `resolveRoomCode` in the
 * registry is; this only removes the cheapest kind of nonsense.
 */
fun roomCodeFrom(link: String?): String? {
    val trimmed = link?.trim().orEmpty()
    if (trimmed.isEmpty()) return null

    val candidate = when {
        // `vinto://7KQ2MP` and `vinto://r/7KQ2MP` are both things a QR code or a hand-written
        // link will contain, and neither is worth refusing over a path segment.
        trimmed.startsWith("$INVITE_SCHEME://", ignoreCase = true) ->
            trimmed.removeRange(0, "$INVITE_SCHEME://".length).trimStart('/').removePrefix("r/")

        trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) -> pathCodeOf(trimmed) ?: return null

        // A bare path, which is what a browser reports as `location.pathname`.
        trimmed.startsWith("/") -> trimmed.trimStart('/').removePrefix("r/")

        else -> trimmed
    }

    val code = candidate.substringBefore('?').substringBefore('#').trim('/').uppercase()
    return code.takeIf { looksLikeRoomCode(it) }
}

/**
 * The code out of an absolute URL, or null when the URL is not an invite at all.
 *
 * Hand-rolled rather than parsed, because `java.net.URI` is JVM-only and the alternative is a
 * multiplatform URL library for one path segment. The host is checked: an invite is only an
 * invite from the host that issues them, so a link to somebody else's site that happens to end
 * in six characters is not one.
 */
private fun pathCodeOf(url: String): String? {
    val afterScheme = url.substringAfter("://")
    val host = afterScheme.substringBefore('/').substringBefore(':')
    if (!host.equals(INVITE_HOST, ignoreCase = true)) return null

    val path = "/" + afterScheme.substringAfter('/', missingDelimiterValue = "")
    if (!path.startsWith(INVITE_PATH, ignoreCase = true)) return null
    return path.removeRange(0, INVITE_PATH.length)
}

/**
 * The link the app was opened with, if it was opened with one.
 *
 * A **hand-off**, not a stream: each platform hands over the URL it was launched or resumed
 * with, and [takeOpenedLink] clears it. Clearing is the point — an invite is a one-shot
 * instruction, and a link that stayed readable would put somebody back into the same room
 * every time they pressed Back.
 *
 * Set from a platform entry point (`MainActivity.onCreate`/`onNewIntent`, iOS's
 * `application(_:continue:)`, `location.pathname` on the web) rather than read by one, so
 * `commonMain` never learns what an `Intent` is.
 */
private var opened: String? = null

/** Called by a platform entry point when the app is handed a URL. */
fun offerOpenedLink(link: String?) {
    if (roomCodeFrom(link) != null) opened = link
}

/** The pending link, once. Null when the app was opened normally. */
fun takeOpenedLink(): String? = opened.also { opened = null }

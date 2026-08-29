package game.vinto.app

import androidx.compose.runtime.staticCompositionLocalOf
import game.vinto.app.net.httpBase
import game.vinto.app.net.postBeacon
import game.vinto.client.Analytics
import game.vinto.client.AnalyticsConsent
import game.vinto.client.AnalyticsTransport
import game.vinto.client.Settings
import game.vinto.protocol.AnalyticsEvent
import game.vinto.protocol.Difficulty
import game.vinto.shapes.Difficulty as EngineDifficulty

/**
 * The app's own counter, reachable from any screen.
 *
 * A composition local like `LocalFeedback` and `LocalSounds`, and for the same reason: the
 * places worth counting are spread across every screen, and threading a parameter through all
 * of them would make the plumbing more visible than the game.
 *
 * The default counts nothing. A screen rendered in a test, a preview or a golden therefore
 * emits nothing without anybody remembering to switch it off — which is the right default for
 * a thing whose failure mode is sending data nobody asked to send.
 */
val LocalCounting = staticCompositionLocalOf { NoCounting }

/** Somewhere to send events, or not. */
fun interface Counting {
    fun record(event: AnalyticsEvent)
}

/** The default: a sink that drops everything, used by tests, previews and goldens. */
val NoCounting: Counting = Counting { }

/**
 * Wraps the shared [Analytics] sink so screens depend on one method rather than on the sink's
 * whole surface.
 */
fun counting(sink: Analytics): Counting = Counting { sink.record(it) }

/**
 * Consent, resolved from the two things that decide it.
 *
 * The platform signal wins. A player who opted in and a browser that sends Global Privacy
 * Control is a person who already answered the question somewhere more authoritative than a
 * game's settings screen.
 */
fun consentFrom(settings: Settings): AnalyticsConsent = AnalyticsConsent(
    optedIn = settings.analytics,
    platformObjects = platformObjectsToTracking(),
)

/**
 * Posts a batch to the room service's `/e`.
 *
 * Over the same origin the game already talks to, so there is no second host to configure, no
 * second certificate to trust and no third party in the path. Failures are swallowed: the
 * sink does not retry, and a counter that made a player's connection worse would be a bad
 * trade for a number.
 */
fun analyticsTransport(service: String): AnalyticsTransport =
    AnalyticsTransport { payload -> postBeacon(httpBase(service) + "/e", payload) }

/**
 * A monotonic-enough clock for measuring how long something took.
 *
 * `nowIso()` is the app's clock and it returns a string, which is right for a recording's
 * header and wrong for arithmetic. This is the same instant as a number of milliseconds.
 * Only durations are ever derived from it — the difference between two readings — so a
 * device whose wall clock jumps produces one wrong duration rather than a wrong timeline.
 */
expect fun elapsedMs(): Long

/**
 * The engine's difficulty, as the analytics vocabulary spells it.
 *
 * Two enums rather than one shared: `shapes.Difficulty` carries `serialName`, which is a wire
 * value written into every saved game and every recording, and analytics must not be able to
 * change it by renaming a case. Mapped explicitly so adding a difficulty is a compile error
 * here rather than a silently missing count.
 */
fun EngineDifficulty.counted(): Difficulty = when (this) {
    EngineDifficulty.EASY -> Difficulty.EASY
    EngineDifficulty.MODERATE -> Difficulty.MODERATE
    EngineDifficulty.HARD -> Difficulty.HARD
}

package game.vinto.app.crash

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Installs an uncaught-exception handler, if the platform has one to install.
 *
 * The one part of crash reporting that cannot be shared: a JVM has a default handler, a
 * Kotlin/Native binary has a hook, and a browser has an event. All four do the same job —
 * hand over the throwable that is about to end things — and none of them may swallow it.
 * A crash reporter that stops a crash is a crash reporter that hides a bug.
 */
expect fun installCrashHandler(report: (Throwable) -> Unit)

/**
 * Sends one crash, once, and then gets out of the way.
 *
 * Fire-and-forget, on a scope that is not the frame's: the process is usually on its way down
 * when this runs, and a report that made shutdown slower would be making the failure worse.
 * Nothing is retried and nothing is queued to disk — a crash that never reached Sentry is a
 * lost report, not a reason to write a spool file that outlives the bug.
 *
 * **Once per process.** A handler that fires on every thread's death would send the same
 * stack a hundred times from a device in a loop, which costs the project's Sentry quota and
 * tells nobody anything the first one did not.
 */
class CrashReporter(
    dsn: String?,
    private val platform: String,
    private val release: String,
    private val environment: String,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val nowIso: () -> String,
    private val surface: () -> CrashSurface,
    private val post: suspend (url: String, auth: String, body: String) -> Unit,
) {
    private val target = parseDsn(dsn)
    private var sent = false

    /** True when this build has somewhere to report to. Absent DSN, absent reporting. */
    val enabled: Boolean get() = target != null

    fun report(error: Throwable) {
        val to = target ?: return
        if (sent) return
        sent = true

        val envelope = crashEnvelope(
            CrashReport(
                eventId = eventId(now()),
                sentAtIso = nowIso(),
                timestampSeconds = now() / MILLIS_PER_SECOND,
                platform = platform,
                release = release,
                environment = environment,
                surface = surface(),
                type = error::class.simpleName ?: "Throwable",
                message = error.message ?: "no message",
                frames = error.stackTraceToString().lines().drop(1).take(MAX_FRAMES).map { it.trim() },
            ),
        )

        scope.launch {
            @Suppress("SwallowedException", "TooGenericExceptionCaught")
            try {
                post(to.url, sentryAuth(to.key), envelope)
            } catch (reportingFailed: Exception) {
                // Deliberately nothing. The request that mattered has already failed; this
                // one was only going to say so, and a reporter that throws turns one failure
                // into two.
            }
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000.0

        /** Sentry truncates long traces anyway, and the top of the stack is the useful part. */
        const val MAX_FRAMES = 30
    }
}

/**
 * An event id: 32 hexadecimal characters, which is what Sentry's envelope header wants.
 *
 * Derived from the clock rather than from a random source, because there is exactly one of
 * these per process and the only property that matters is that two crashes from two devices
 * do not collide in a way that makes Sentry drop the second. It is **not** an identifier for
 * a device or a person: it is minted at crash time, never stored, and never reused.
 */
internal fun eventId(millis: Long): String {
    val high = millis.toULong().toString(HEX_RADIX).padStart(HEX_DIGITS / 2, '0')
    val low = (millis * GOLDEN).toULong().toString(HEX_RADIX).padStart(HEX_DIGITS / 2, '0')
    return (high.takeLast(HEX_DIGITS / 2) + low.takeLast(HEX_DIGITS / 2))
}

private const val HEX_RADIX = 16
private const val HEX_DIGITS = 32

/** Knuth's multiplicative constant, to spread the low half rather than repeat the clock. */
private const val GOLDEN = 2_654_435_761L

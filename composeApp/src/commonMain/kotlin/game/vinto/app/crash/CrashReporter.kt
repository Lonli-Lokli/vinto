package game.vinto.app.crash

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Sends one crash, once, and then gets out of the way.
 *
 * Fire-and-forget, on a scope that is not the frame's: the process is usually on its way down
 * when this runs, and a report that made shutdown slower would be making the failure worse.
 * Nothing is retried and nothing is queued to disk — a crash that never reached Sentry is a
 * lost report, not a reason to write a spool file that outlives the bug.
 *
 * A handful per process, and no more ([ReportBudget]).
 */
class CrashReporter(
    dsn: String?,
    private val platform: SentryPlatform,
    private val release: String,
    /** Which build of [release] — the commit count, so two builds of `1.0` are tellable apart. */
    private val dist: String,
    /** The commit this build came from, so a report can be opened against its own code. */
    private val commit: String,
    /** The machine in words, carried as a tag because [platform] is not for reading. */
    private val os: String,
    private val environment: String,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val nowIso: () -> String,
    private val surface: () -> CrashSurface,
    /**
     * Where in a game the app was, read at the moment of the crash. Defaults to nowhere, so
     * a caller that has no game — the tests, a menu-only harness — needs to say nothing.
     */
    private val place: () -> CrashPlace = { CrashPlace() },
    private val post: suspend (url: String, auth: String, body: String) -> Unit,
) {
    private val target = parseDsn(dsn)
    private val budget = ReportBudget()

    /** True when this build has somewhere to report to. Absent DSN, absent reporting. */
    val enabled: Boolean get() = target != null

    /**
     * The envelope for [error], or null when there is nothing to send or nothing new to say.
     *
     * Split from [send] so the caller can **write it down before trying the network**. A
     * process that is about to be killed cannot be relied on to finish a POST, and an envelope
     * on disk can be sent by the next launch; one that only ever existed inside a dying
     * process is a crash nobody will ever hear about.
     */
    fun envelopeFor(error: Throwable): String? {
        if (target == null) return null
        val account = accountOf(error)
        if (!budget.admits(account.type, account.message)) return null

        val sections = traceSections(account.trace)
        // The nearest first when trimming, and then the oldest first, which is how Sentry reads a
        // chain: the failure the report is about comes last and names the issue.
        val chained = sections.drop(1).take(MAX_CHAINED).asReversed()
        return crashEnvelope(
            CrashReport(
                eventId = eventId(now()),
                sentAtIso = nowIso(),
                timestampSeconds = now() / MILLIS_PER_SECOND,
                platform = platform,
                release = release,
                dist = dist,
                commit = commit,
                os = os,
                environment = environment,
                surface = surface(),
                type = account.type,
                message = account.message,
                frames = sections.first().frames.take(MAX_FRAMES),
                chained = chained.map { it.copy(frames = it.frames.take(MAX_FRAMES)) },
                place = place(),
            ),
        )
    }

    /**
     * Posts one envelope, and says whether it arrived.
     *
     * The `Job` is returned so a fatal path can wait on it ([CrashPipe.ByHand.awaitSent]); the `onSent`
     * callback runs only on success, which is what lets a stored copy be cleared without
     * guessing.
     */
    fun send(envelope: String, onSent: () -> Unit = {}): Job? {
        val to = target ?: return null
        return scope.launch {
            @Suppress("SwallowedException", "TooGenericExceptionCaught")
            try {
                post(to.url, sentryAuth(to.key), envelope)
                onSent()
            } catch (reportingFailed: Exception) {
                // Deliberately nothing. The request that mattered has already failed; this
                // one was only going to say so, and a reporter that throws turns one failure
                // into two. The stored copy stays put and the next launch tries again.
            }
        }
    }

    /** Builds and sends in one go, for a failure nobody is waiting on. */
    fun report(error: Throwable): Job? = envelopeFor(error)?.let { send(it) }

    private companion object {
        const val MILLIS_PER_SECOND = 1000.0

        /** Sentry truncates long traces anyway, and the top of the stack is the useful part. */
        const val MAX_FRAMES = 30

        /** Causes and suppressed failures carried beside the one reported; past a few, it is a loop. */
        const val MAX_CHAINED = 5
    }
}

/**
 * **A handful per process, and no more**, and each failure once — on every pipe, the SDK's too.
 *
 * A handler that fires on every thread's death would send the same stack a hundred times from a
 * device in a loop, which costs the project's Sentry quota and tells nobody anything the first one
 * did not. It was *one*, which was right while the only caller was the fatal handler and wrong the
 * moment non-fatal failures started arriving too — a background coroutine failing early would then
 * have spent the budget the crash that actually ended the app needed. [BUDGET] is small enough
 * that a loop cannot run up a bill and large enough that the first thing to go wrong does not
 * silence the last.
 */
internal class ReportBudget {
    private var spent = 0
    private val seen = mutableSetOf<String>()

    /** True for a failure worth a report — new, and within the budget — which it then counts. */
    fun admits(error: Throwable): Boolean =
        admits(error::class.simpleName ?: "Throwable", runCatching { error.message }.getOrNull().orEmpty())

    /** The same, for a failure already read ([accountOf]) — a relayed one has no class to ask. */
    fun admits(type: String, message: String): Boolean {
        if (spent >= BUDGET) return false
        // The same failure twice is one bug reported twice. A retry loop that throws the same
        // exception every second is the ordinary way a client burns a quota, and it is exactly
        // the shape of the socket loop the reporter watches.
        if (!seen.add("$type:$message")) return false
        spent++
        return true
    }

    private companion object {
        /** How many distinct failures one process may report. */
        const val BUDGET = 5
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

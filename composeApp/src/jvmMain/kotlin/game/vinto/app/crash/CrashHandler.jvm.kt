package game.vinto.app.crash

import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** The desktop window sends the app's own envelope; nobody ships it, and an SDK would add nothing. */
internal actual val crashPipe: CrashPipe =
    CrashPipe.ByHand(catchUncaught = ::catchUncaught, awaitSent = ::awaitSent)

/**
 * The JVM's default handler, wrapped rather than replaced.
 *
 * Chaining to whatever was there is the whole discipline: the previous handler is the one that
 * prints the trace and lets the process end. A reporter that installs itself over that and
 * returns leaves an app frozen on a dead frame, which is a worse bug than the one being reported.
 * (Android's, which shows "Vinto has stopped", is the Sentry SDK's to chain to now.)
 */
private fun catchUncaught(report: (Throwable) -> Unit) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
        @Suppress("TooGenericExceptionCaught")
        try {
            report(error)
        } catch (reportingFailed: Throwable) {
            // Never let the reporter's own failure replace the crash being reported.
            //
            // A stack trace to stderr rather than a logger, deliberately: this runs inside an
            // uncaught-exception handler in a process that is already ending, after the thing
            // whose job was to report failures has itself failed. Anything with more machinery
            // is another thing that can throw here, and what it would throw away is the crash.
            @Suppress("PrintStackTrace")
            reportingFailed.printStackTrace()
        }
        previous?.uncaughtException(thread, error)
    }
}

/**
 * Holds the dying thread until the report is away, or until the ceiling.
 *
 * `runBlocking` on the crashing thread, which is exactly the situation blocking is for: the
 * very next thing this thread does is hand the throwable to the platform's handler, and on
 * Android that ends the process. Without this the POST was launched into a process that no
 * longer existed a microsecond later, which is why a correct reporter with a correct envelope
 * delivered nothing at all.
 *
 * The ceiling is short on purpose. An app that has already crashed must not sit there because
 * a network is not answering — and if the timeout wins, the envelope is still on disk and the
 * next launch sends it.
 */
private fun awaitSent(job: Job?) {
    if (job == null) return
    runBlocking { withTimeoutOrNull(REPORT_CEILING_MS) { job.join() } }
}

/** Long enough for a POST on a phone network, short enough that nobody watches a dead app. */
private const val REPORT_CEILING_MS = 4_000L

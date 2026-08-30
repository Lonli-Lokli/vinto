package game.vinto.app.crash

import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The JVM's default handler, wrapped rather than replaced.
 *
 * Chaining to whatever was there is the whole discipline: on Android the previous handler is
 * the platform's, and it is what actually shows "Vinto has stopped" and ends the process.
 * A reporter that installs itself over that and returns leaves an app frozen on a dead frame,
 * which is a worse bug than the one being reported.
 */
actual fun installCrashHandler(report: (Throwable) -> Unit) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
        @Suppress("TooGenericExceptionCaught")
        try {
            report(error)
        } catch (reportingFailed: Throwable) {
            // Never let the reporter's own failure replace the crash being reported.
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
actual fun awaitCrashReport(job: Job?) {
    if (job == null) return
    runBlocking { withTimeoutOrNull(REPORT_CEILING_MS) { job.join() } }
}

/** Long enough for a POST on a phone network, short enough that nobody watches a dead app. */
private const val REPORT_CEILING_MS = 4_000L

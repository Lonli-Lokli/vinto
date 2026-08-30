package game.vinto.app.crash

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

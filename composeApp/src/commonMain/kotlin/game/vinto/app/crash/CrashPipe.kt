package game.vinto.app.crash

import kotlinx.coroutines.Job

/**
 * How a platform's crashes reach Sentry: by the app's own envelope, or through the platform's SDK.
 *
 * The one part of crash reporting that cannot be shared. A sealed choice rather than two `expect`
 * functions every target had to answer, because Android no longer has an answer to give to either:
 * the SDK installs its own handler and keeps its own store (design D2). A new target picks one of
 * these to compile.
 */
internal sealed interface CrashPipe {

    /**
     * The app's own envelope (`Crash.kt`), POSTed by [CrashReporter]: the web, where Kotlin/Wasm
     * has no native crash for an SDK to catch and the bundle has no room for one, and the desktop
     * window nobody ships (design D1). iOS too, until its half of the change lands.
     */
    class ByHand(
        /**
         * Installs an uncaught-exception handler, if the platform has one to install.
         *
         * A JVM has a default handler, a Kotlin/Native binary has a hook, and a browser has an
         * event. All of them do the same job — hand over the throwable that is about to end things
         * — and none of them may swallow it. A crash reporter that stops a crash is a crash
         * reporter that hides a bug.
         */
        val catchUncaught: (report: (Throwable) -> Unit) -> Unit,
        /**
         * Waits, briefly, for a crash report to actually leave the device.
         *
         * **The one thing standing between a crash and a report.** `report` launches a POST and
         * returns; the handler then chains to the platform's, which can end the process at once.
         * A DNS lookup, a TLS handshake and a POST do not fit in the microseconds between those two
         * lines, so every fatal crash was reported *nowhere* — the reporter worked, the envelope
         * was correct, and the process was gone before the packet was.
         *
         * So the crashing thread blocks on it, with a short ceiling: a process that is already
         * dying must not be held there by a network that is not answering. On the JVM and on Apple
         * that is `runBlocking` with a timeout; in a browser there is nothing to block *for* — the
         * page is not torn down by an unhandled rejection — and this does nothing.
         */
        val awaitSent: (Job?) -> Unit,
    ) : CrashPipe

    /**
     * The platform's own Sentry SDK, which catches what escapes — an uncaught exception, an ANR, a
     * native crash — keeps it on disk, and is handed what the app survives. Android
     * (`CrashPipe.android.kt`).
     */
    class Sdk(
        /**
         * Starts the SDK, and answers how a failure the app survives reaches it — or null when it
         * did not start. [surface] and [place] are read when a report is made, not now.
         */
        val start: (
            dsn: String,
            surface: () -> CrashSurface,
            place: () -> CrashPlace,
        ) -> ((Throwable) -> Unit)?,
    ) : CrashPipe
}

/** This platform's pipe. */
internal expect val crashPipe: CrashPipe

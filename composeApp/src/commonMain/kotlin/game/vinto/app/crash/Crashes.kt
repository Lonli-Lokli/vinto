package game.vinto.app.crash

import game.vinto.app.SENTRY_DSN
import game.vinto.app.VERSION
import game.vinto.app.elapsedMs
import game.vinto.app.net.postBeacon
import game.vinto.app.nowIso
import game.vinto.app.platformName
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

/**
 * Crash reporting for the whole process, installed before anything draws.
 *
 * It used to be a `LaunchedEffect` inside a composable inside `App()`, which meant the handler
 * came into existence **after** the first composition — so the one crash you most want, the
 * one that stops the app on the launcher before a player sees anything, happened while there
 * was nothing listening. That is not a small window on a cold start: it covers the vault being
 * opened, the deep link being read, the resources being resolved, and the whole first frame.
 *
 * So it is a process-level object with an `install` the entry points call, and the composable's
 * only remaining job is to say *where* the app is when something goes wrong. Nothing here
 * needs Compose, which is the property that lets it be installed from `main()`.
 *
 * Everything it sends is in `Crash.kt`, which is where the rule about what may leave lives.
 * This file decides only *when*.
 */
object Crashes {

    private var reporter: CrashReporter? = null
    private var surface: () -> CrashSurface = { CrashSurface.MENU }

    /**
     * Starts reporting, once per process.
     *
     * Idempotent on purpose: Android calls this from `MainActivity.onCreate`, which runs again
     * on a configuration change, and a second handler chained onto the first would send every
     * crash twice. [scope] outlives the composition — a report is fired as the process is
     * ending, and a scope tied to a frame would be cancelled before the POST left.
     */
    fun install(scope: CoroutineScope, dsn: String = SENTRY_DSN) {
        if (reporter != null) return

        val crashes = CrashReporter(
            dsn = dsn,
            platform = platformName(),
            release = "vinto@$VERSION",
            environment = "production",
            scope = scope,
            now = ::elapsedMs,
            nowIso = ::nowIso,
            surface = { surface() },
            place = { Where.now() },
            post = { url, auth, body ->
                postBeacon(url, body, contentType = "application/x-sentry-envelope", auth = auth)
            },
        )
        reporter = crashes
        if (crashes.enabled) installCrashHandler(crashes::report)
    }

    /**
     * Where the app is, read at the moment of a crash rather than pushed on every change.
     *
     * A lambda rather than a value because the surface changes with every screen and a crash
     * report wants the one that was showing, not the one that was showing when this was called.
     */
    fun watching(where: () -> CrashSurface) {
        surface = where
    }

    /**
     * Reports something that went wrong without ending the process.
     *
     * A coroutine that fails on a background scope, a socket loop that throws where nobody is
     * catching: on Android these reach the default handler only sometimes, and in a browser
     * they reach it as an unhandled rejection with the Kotlin stack already lost. Reporting
     * them explicitly is the difference between "the app is fine and the room never loads" and
     * a stack trace naming the line.
     */
    fun report(error: Throwable) {
        reporter?.report(error)
    }

    /**
     * A handler for a scope whose failures would otherwise be silent.
     *
     * Attached to the app scope, so a background coroutine that throws is reported rather than
     * printed to a console nobody is reading. It does **not** rethrow: the scope's job is
     * supervised and the app is still usable, which is exactly the case the fatal handler
     * cannot see.
     */
    fun handler(): CoroutineContext = CoroutineExceptionHandler { _, error -> report(error) }

    /** For tests: forget the installed reporter so the next `install` takes. */
    internal fun forget() {
        reporter = null
        surface = { CrashSurface.MENU }
    }
}

/**
 * A scope for work that must outlive whatever screen started it — a crash report, chiefly.
 *
 * `SupervisorJob` so one failure does not cancel the rest, and [Crashes.handler] so a failure
 * is heard at all. Created by each platform's entry point and handed to [Crashes.install].
 */
fun appReportingScope(): CoroutineScope =
    CoroutineScope(SupervisorJob() + CoroutineName("vinto-app") + Crashes.handler())

package game.vinto.app.crash

import game.vinto.app.BUILD_COMMIT
import game.vinto.app.BUILD_NUMBER
import game.vinto.app.SENTRY_DSN
import game.vinto.app.appVersion
import game.vinto.app.crashPlatform
import game.vinto.app.elapsedMs
import game.vinto.app.isEmulatedDevice
import game.vinto.app.net.postBeacon
import game.vinto.app.nowIso
import game.vinto.app.platformName
import game.vinto.app.platformVault
import game.vinto.client.Vault
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
 * *How* a report travels is the platform's ([CrashPipe]): the app's own envelope on the web and
 * the desktop, whose rules about what may leave are in `Crash.kt`, and the Sentry SDK on Android,
 * whose are in `PhoneReports.kt`. This file decides only *when*, and *where the app was*.
 */
object Crashes {

    /**
     * Where a crash waits between the process dying and the next launch.
     *
     * One slot, deliberately: a phone in a crash loop would otherwise fill the vault with
     * copies of one bug, and the most recent is the one worth having. It holds a Sentry
     * envelope, which carries nothing identifying — `Crash.kt` makes that structural.
     */
    private const val PENDING = "vinto.crash.pending"

    private var installed = false
    private var reporter: ((Throwable) -> Unit)? = null
    private var surface: () -> CrashSurface = { CrashSurface.MENU }

    /**
     * Starts reporting, once per process.
     *
     * Idempotent on purpose: a second handler chained onto the first would send every crash
     * twice, and an entry point can run again — a recreated window, a test. [scope] outlives the
     * composition — a report is fired as the process is ending, and a scope tied to a frame would
     * be cancelled before the POST left.
     */
    fun install(
        scope: CoroutineScope,
        /**
         * Where a crash waits between the process dying and the next launch, for the envelope
         * the app sends itself. Android does not use it: the SDK keeps its own store.
         *
         * Defaulted here rather than passed by each entry point, so `androidApp` never has to name
         * a type from `shared:client` it does not otherwise depend on.
         */
        vault: Vault? = platformVault(),
        dsn: String = SENTRY_DSN,
        /**
         * An emulator or a simulator, which installs nothing: no handler, no SDK, no stored crash
         * sent, and [report] does nothing because there is no reporter to hand to. Checked here
         * rather than at each entry point so that all four ask the one question
         * ([isEmulatedDevice]), and a parameter so that a test can be the emulator. The DSN could
         * not do it: it is built into every build, and the build type says only which bucket a
         * crash lands in, not whether a player had it.
         */
        emulated: Boolean = isEmulatedDevice,
    ) {
        if (installed || emulated) return
        installed = true

        reporter = when (val pipe = crashPipe) {
            is CrashPipe.ByHand -> reportByHand(pipe, scope, vault, dsn)
            is CrashPipe.Sdk -> pipe.start(dsn, { surface() }, Where::now)
        }
    }

    /** The app's own envelope: built, stored, and POSTed by [CrashReporter]. */
    private fun reportByHand(
        pipe: CrashPipe.ByHand,
        scope: CoroutineScope,
        vault: Vault?,
        dsn: String,
    ): (Throwable) -> Unit {
        val crashes = CrashReporter(
            dsn = dsn,
            platform = crashPlatform,
            release = "vinto@${appVersion()}",
            dist = BUILD_NUMBER,
            commit = BUILD_COMMIT,
            os = platformName(),
            environment = sentryEnvironment(),
            scope = scope,
            now = ::elapsedMs,
            nowIso = ::nowIso,
            surface = { surface() },
            place = { Where.now() },
            post = { url, auth, body ->
                postBeacon(url, body, contentType = "application/x-sentry-envelope", auth = auth)
            },
        )

        if (crashes.enabled) {
            // A crash the last run could not finish sending. First, because the whole point is
            // that it survived a process being killed and nothing else will send it.
            vault?.read(PENDING)?.let { stored ->
                crashes.send(stored) { vault.erase(PENDING) }
            }

            pipe.catchUncaught { error ->
                // Written down before the network is touched. The next thing the platform's
                // handler does may be to end the process — so the envelope has to exist somewhere
                // that outlives it, or a POST cut off halfway is a crash nobody ever hears about.
                // Cleared only when the send actually succeeds.
                crashes.envelopeFor(error)?.let { envelope ->
                    vault?.write(PENDING, envelope)
                    pipe.awaitSent(crashes.send(envelope) { vault?.erase(PENDING) })
                }
            }
        }
        return { error -> crashes.report(error) }
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

    /** For tests: where a report made now would say the app is. */
    internal fun where(): CrashSurface = surface()

    /**
     * Reports something that went wrong without ending the process.
     *
     * A coroutine that fails on a background scope, a socket loop that throws where nobody is
     * catching: on Android these reach the default handler only sometimes, and in a browser
     * they reach it as an unhandled rejection with the Kotlin stack already lost. Reporting
     * them explicitly is the difference between "the app is fine and the room never loads" and
     * a stack trace naming the line.
     *
     * **Never throws.** It runs inside a `CoroutineExceptionHandler`, and a handler that throws is
     * what kotlinx.coroutines reports as "Exception while trying to handle coroutine exception" —
     * the failure it was handed, buried under the reporter's own (Sentry 7817278407).
     */
    fun report(error: Throwable) {
        runCatching { reporter?.invoke(error) }
    }

    /**
     * A handler for a scope whose failures would otherwise be silent.
     *
     * Attached to the app scope, so a background coroutine that throws is reported rather than
     * printed to a console nobody is reading. It does **not** rethrow: the scope's job is
     * supervised and the app is still usable, which is exactly the case the fatal handler
     * cannot see. Supervised by [game.vinto.app.rememberAppScope], not by Compose: the scope
     * `rememberCoroutineScope` makes cancels the Recomposer when one of its jobs fails.
     */
    fun handler(): CoroutineContext = CoroutineExceptionHandler { _, error -> report(error) }

    /** For tests: forget the installed reporter so the next `install` takes. */
    internal fun forget() {
        installed = false
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

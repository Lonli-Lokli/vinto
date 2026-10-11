package game.vinto.app.crash

import game.vinto.app.AndroidStorage
import game.vinto.app.BUILD_COMMIT
import game.vinto.app.BUILD_NUMBER
import game.vinto.app.appVersion
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid

/**
 * Android reports through `sentry-android` (design D1, D2).
 *
 * The SDK catches what no Kotlin `try` sees — an ANR, a native crash — as well as an uncaught
 * exception, and it names its own R8 mapping, which the hand-built envelope got wrong in the one
 * field that mattered (`CrashFramesTest`). What this app adds is what the SDK does not decide for
 * itself: the options and the scrubbing (`PhoneReports.kt`), and where in a game a report was made.
 */
internal actual val crashPipe: CrashPipe = CrashPipe.Sdk(::startSentry)

/**
 * Starts the SDK and answers how a failure the app survives reaches it.
 *
 * Called from `VintoApplication.onCreate`, after storage, which is where the build is read from: the
 * version off the installed package and whether the package is a release. Null when there is no
 * context yet or no DSN worth the name — a malformed one makes `SentryAndroid.init` throw, and a
 * reporter that takes the app down on its way up is the worst one there is.
 */
private fun startSentry(
    dsn: String,
    surface: () -> CrashSurface,
    place: () -> CrashPlace,
): ((Throwable) -> Unit)? {
    val context = AndroidStorage.context ?: return null
    if (parseDsn(dsn) == null) return null

    SentryAndroid.init(context) { options ->
        vintoSentryOptions(
            options = options,
            build = SdkBuild(
                dsn = dsn,
                version = appVersion(),
                buildNumber = BUILD_NUMBER,
                commit = BUILD_COMMIT,
                environment = sentryEnvironment(),
            ),
            surface = surface,
            place = place,
        )
        // Android's own two, which the SDK's JVM core does not have and so `PhoneReports.kt` cannot
        // set: no picture of the screen and no tree of what is on it, either of which can show a
        // room code. Both are off by default in 8.55.0; said here so a future default cannot turn
        // them on.
        options.isAttachScreenshot = false
        options.isAttachViewHierarchy = false
    }

    // The same handful per process as the hand-built reporter, each failure once: a socket loop
    // that throws every second must not spend the project's quota.
    val budget = ReportBudget()
    return { error -> if (budget.admits(error)) Sentry.captureException(error) }
}

package game.vinto.app.crash

import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.setUnhandledExceptionHook

/**
 * Kotlin/Native's own hook, and deliberately nothing from Objective-C.
 *
 * `setUnhandledExceptionHook` is plain Kotlin standard library rather than an Objective-C
 * binding, which is deliberate: README §7 records two mistakes that were only findable on a
 * Mac and both were bindings, so avoiding them removes that whole family of failure.
 *
 * It did not remove all of it. The hook is `@ExperimentalNativeApi` and a missing opt-in is a
 * compile **error**, so this file still cost a CI round trip — the point being that "plain
 * stdlib" is safer than a binding and is not the same as safe. Nothing on a Linux host can
 * check an `iosMain` file at all.
 *
 * It catches a Kotlin exception that escapes to the top. A genuine native crash — a signal,
 * a Swift trap — is what the Sentry SDK would add, and §A9 records that as flagged rather
 * than settled.
 */
@OptIn(ExperimentalNativeApi::class)
actual fun installCrashHandler(report: (Throwable) -> Unit) {
    setUnhandledExceptionHook { error ->
        report(error)
        // The hook is cleared once it fires, so returning here lets the runtime terminate as
        // it would have. Rethrowing would re-enter a hook that is no longer installed.
    }
}

/**
 * Holds the dying thread until the report is away, or until the ceiling.
 *
 * The same reason as the JVM's: the hook returns and the runtime terminates, so a POST that
 * was merely *launched* never leaves. `runBlocking` exists on Kotlin/Native and this is what
 * it is for. If the timeout wins the envelope is still stored, and the next launch sends it.
 */
actual fun awaitCrashReport(job: Job?) {
    if (job == null) return
    runBlocking { withTimeoutOrNull(REPORT_CEILING_MS) { job.join() } }
}

/** Long enough for a POST on a phone network, short enough that nobody watches a dead app. */
private const val REPORT_CEILING_MS = 4_000L

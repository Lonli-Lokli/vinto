package game.vinto.app.crash

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

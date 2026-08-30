package game.vinto.app.crash

/**
 * Kotlin/Native's own hook, and deliberately nothing from Objective-C.
 *
 * `setUnhandledExceptionHook` is plain Kotlin standard library, so this file compiles on a
 * host with no Apple toolchain to check it — which matters, because it cannot be compiled
 * here. README §7 records the two mistakes that were only findable on a Mac; both were
 * Objective-C bindings, and neither is reachable from this.
 *
 * It catches a Kotlin exception that escapes to the top. A genuine native crash — a signal,
 * a Swift trap — is what the Sentry SDK would add, and §A9 records that as flagged rather
 * than settled.
 */
actual fun installCrashHandler(report: (Throwable) -> Unit) {
    setUnhandledExceptionHook { error ->
        report(error)
        // The hook is cleared once it fires, so returning here lets the runtime terminate as
        // it would have. Rethrowing would re-enter a hook that is no longer installed.
    }
}

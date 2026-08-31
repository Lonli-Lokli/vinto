package game.vinto.app.crash

import kotlinx.browser.window
import kotlinx.coroutines.Job
import org.w3c.dom.events.Event

/**
 * The browser has no uncaught-exception handler a Kotlin coroutine would reach, so the app
 * hears about a crash the way a page does: through `window`'s error events.
 *
 * Two of them, and the second is the one that matters. `error` is a synchronous throw;
 * `unhandledrejection` is a promise nobody caught, which is where an exception from a
 * coroutine actually surfaces on this target — listening only to `error` would miss most of
 * what goes wrong in a suspending UI.
 *
 * Neither is cancelled: `preventDefault` is never called, so the failure still reaches the
 * console. A reporter that silences the browser's own logging makes the crash harder to debug
 * for the one person in a position to debug it.
 *
 * Through `kotlinx.browser` rather than `js("...")`, because a listener has to call back into
 * Kotlin and `js(...)` cannot capture a Kotlin value — an earlier version of this file tried
 * to bridge one through a global and would have reported nothing at all.
 */
actual fun installCrashHandler(report: (Throwable) -> Unit) {
    window.addEventListener("error", { event: Event ->
        report(BrowserCrash(describe(event, "uncaught error")))
    })
    window.addEventListener("unhandledrejection", { event: Event ->
        report(BrowserCrash(describe(event, "unhandled rejection")))
    })
}

/** What the browser knew about the failure, which on this target is a string. */
private class BrowserCrash(override val message: String) : Throwable(message)

/**
 * The event's own message, when it has one.
 *
 * Read through `js(...)` on the raw event because the two event types keep it in different
 * places — `ErrorEvent.message` against `PromiseRejectionEvent.reason.message` — and neither
 * shape is guaranteed: a promise can be rejected with any value at all, a bare string
 * included. Falls back to naming which listener fired, which is worth more than an empty
 * report.
 */
private fun describe(event: Event, fallback: String): String = messageOf(event) ?: fallback

private fun messageOf(event: Event): String? =
    readMessage(event.asDynamicEvent()).takeIf { it.isNotBlank() }

private fun Event.asDynamicEvent(): JsAny = this

private fun readMessage(event: JsAny): String =
    readMessageJs(event)?.toString() ?: ""

// detekt reads Kotlin, not the JavaScript below, so it cannot see that `event` is used.
@Suppress("UnusedParameter")
private fun readMessageJs(event: JsAny): JsString? = js(
    "(event.message || (event.reason && (event.reason.message || event.reason)) || '') + ''",
)

/**
 * Nothing to wait for, and nothing to wait *with*.
 *
 * A browser has no blocking primitive on the main thread, and it does not need one: an
 * unhandled rejection does not tear the page down, so the POST launched a moment ago runs to
 * completion like any other. The other three targets block because their runtime is about to
 * end the process.
 */
@Suppress("UnusedParameter")
actual fun awaitCrashReport(job: Job?) = Unit

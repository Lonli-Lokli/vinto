package game.vinto.app.crash

import kotlinx.browser.window
import kotlinx.coroutines.Job
import org.w3c.dom.events.Event

/**
 * The app's own envelope, and it stays: Kotlin/Wasm has no native crash for an SDK to catch, and
 * the bundle has no room for one (`add-live-analytics/design.md` §A9).
 */
internal actual val crashPipe: CrashPipe =
    CrashPipe.ByHand(catchUncaught = ::catchUncaught, awaitSent = ::awaitSent)

/**
 * The browser has no uncaught-exception handler a Kotlin coroutine would reach, so the app
 * hears about a crash the way a page does: through `window`'s error events.
 *
 * Two of them. `error` is a synchronous throw — and a coroutine failure nothing handled, which
 * kotlinx.coroutines hands to `reportError`; `unhandledrejection` is a promise nobody caught.
 *
 * Neither is cancelled: `preventDefault` is never called, so the failure still reaches the
 * console. A reporter that silences the browser's own logging makes the crash harder to debug
 * for the one person in a position to debug it.
 *
 * Through `kotlinx.browser` rather than `js("...")`, because a listener has to call back into
 * Kotlin and `js(...)` cannot capture a Kotlin value — an earlier version of this file tried
 * to bridge one through a global and would have reported nothing at all.
 */
private fun catchUncaught(report: (Throwable) -> Unit) {
    keepDeeperStacks()
    window.addEventListener("error", { event: Event ->
        report(relayed(event, "uncaught error"))
    })
    window.addEventListener("unhandledrejection", { event: Event ->
        report(relayed(event, "unhandled rejection"))
    })
}

/**
 * The failure as the browser has it — its name, its message and, above all, its stack.
 *
 * Read off the thrown value (`ErrorEvent.error`, `PromiseRejectionEvent.reason`) rather than the
 * event: the event's `message` is one line with "Uncaught " in front, and that line was all this
 * kept, so Sentry 7817278407 arrived as a sentence about a coroutine handler and the frames of the
 * listener that wrote it down. Each read falls back to empty, because a promise can be rejected
 * with anything at all and a cross-origin script error carries nothing; [relayedCrash] decides
 * what an empty one means.
 */
private fun relayed(event: Event, fallback: String): Throwable {
    val raw: JsAny = event
    return relayedCrash(
        name = failureName(raw),
        message = failureMessage(raw),
        stack = failureStack(raw),
        fallback = fallback,
    )
}

// detekt reads Kotlin, not the JavaScript below, so it cannot see that `event` is used.
@Suppress("UnusedParameter")
private fun failureName(event: JsAny): String = js(
    """{ try {
         var e = event.error !== undefined ? event.error : event.reason;
         return (e && typeof e === 'object' && typeof e.name === 'string') ? e.name : '';
       } catch (x) { return ''; } }""",
)

@Suppress("UnusedParameter")
private fun failureMessage(event: JsAny): String = js(
    """{ try {
         var e = event.error !== undefined ? event.error : event.reason;
         if (e && typeof e === 'object' && e.message != null) return String(e.message);
         if (e != null && typeof e !== 'object') return String(e);
         return event.message ? String(event.message) : '';
       } catch (x) { return ''; } }""",
)

@Suppress("UnusedParameter")
private fun failureStack(event: JsAny): String = js(
    """{ try {
         var e = event.error !== undefined ? event.error : event.reason;
         return (e && typeof e === 'object' && typeof e.stack === 'string') ? e.stack : '';
       } catch (x) { return ''; } }""",
)

/**
 * V8 keeps ten frames of every stack unless told otherwise, and on Kotlin/Wasm up to seven of those
 * are `Throwable`'s own constructors — so a web report could end before it reached the line that
 * threw. Every Kotlin exception here builds a JavaScript `Error`, which is what pays for the
 * deeper capture; the engine and the bot throw none in their loops. Other engines ignore it.
 */
private fun keepDeeperStacks(): Unit = js("{ Error.stackTraceLimit = 50; }")

/**
 * Nothing to wait for, and nothing to wait *with*.
 *
 * A browser has no blocking primitive on the main thread, and it does not need one: an
 * unhandled rejection does not tear the page down, so the POST launched a moment ago runs to
 * completion like any other. The other three targets block because their runtime is about to
 * end the process.
 */
@Suppress("UnusedParameter")
private fun awaitSent(job: Job?) = Unit

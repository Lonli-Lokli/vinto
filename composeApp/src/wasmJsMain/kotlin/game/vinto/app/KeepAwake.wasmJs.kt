package game.vinto.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.browser.document

/**
 * The Screen Wake Lock API, asked for politely and never insisted upon.
 *
 * Three things make this different from the other three actuals, and all of them are the
 * browser being a browser:
 *
 *  * **It may simply not be there.** Safari got it late and a page served over plain http never
 *    gets it at all. `js("...")` guards on the object existing, so an unsupported browser is a
 *    browser where the game plays exactly as before.
 *  * **It is a promise, and it can be refused** — a laptop already on battery saver will say no.
 *    A refusal is nothing to report: the player did not ask for this directly and cannot act on
 *    the answer.
 *  * **The browser takes it back when the tab is hidden**, and does *not* give it back when the
 *    tab returns. So the lock is re-requested on `visibilitychange`, which is the whole reason
 *    this is more than two lines.
 */
@Composable
actual fun KeepAwake(on: Boolean) {
    DisposableEffect(on) {
        if (!on) {
            releaseWakeLock()
            onDispose { }
        } else {
            requestWakeLock()
            val again: (org.w3c.dom.events.Event) -> Unit = { if (pageIsVisible()) requestWakeLock() }
            document.addEventListener("visibilitychange", again)
            onDispose {
                document.removeEventListener("visibilitychange", again)
                releaseWakeLock()
            }
        }
    }
}

/** `document.visibilityState`, which Kotlin's DOM bindings do not surface for wasm. */
private fun pageIsVisible(): Boolean = js("document.visibilityState === 'visible'")

private fun requestWakeLock() {
    // Guarded on the object existing, because Safari got this late and a page on plain http
    // never gets it at all; a refusal is swallowed because the player did not ask directly and
    // could not act on the answer. The handle hangs off `globalThis` so [releaseWakeLock] finds
    // the same lock this took.
    js(
        "{ try { if (!navigator.wakeLock) return; if (globalThis.__vintoWakeLock) return; " +
            "navigator.wakeLock.request('screen').then(function (l) { globalThis.__vintoWakeLock = l; " +
            "l.addEventListener('release', function () { globalThis.__vintoWakeLock = null; }); }, " +
            "function () {}); } catch (e) {} }",
    )
}

private fun releaseWakeLock() {
    js(
        "{ try { var held = globalThis.__vintoWakeLock; globalThis.__vintoWakeLock = null; " +
            "if (held) held.release(); } catch (e) {} }",
    )
}

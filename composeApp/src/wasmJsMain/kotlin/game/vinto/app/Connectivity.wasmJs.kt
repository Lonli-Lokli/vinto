package game.vinto.app

import game.vinto.client.Reachability
import kotlinx.browser.window
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.w3c.dom.events.Event

/**
 * The browser says it through `navigator.onLine` and the `online` / `offline` events.
 *
 * `false` is dependable — the browser knows it has no interface — and is the whole reason
 * this exists. `true` only means "not definitely offline", which is as much as any platform
 * can promise, so it opens the door the same way.
 */
actual fun platformReachability(): Flow<Reachability> = callbackFlow {
    fun now(): Reachability =
        if (window.navigator.onLine) Reachability.ONLINE else Reachability.OFFLINE

    trySend(now())
    val changed: (Event) -> Unit = { trySend(now()) }
    window.addEventListener("online", changed)
    window.addEventListener("offline", changed)
    awaitClose {
        window.removeEventListener("online", changed)
        window.removeEventListener("offline", changed)
    }
}

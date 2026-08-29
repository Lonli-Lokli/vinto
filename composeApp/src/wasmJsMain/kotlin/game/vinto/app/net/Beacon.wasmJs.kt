package game.vinto.app.net

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.fetch.RequestInit

/**
 * `fetch` with `keepalive`, which is what lets a last batch survive the page closing —
 * exactly the moment a session's most interesting event happens.
 */
actual suspend fun postBeacon(url: String, body: String) {
    runCatching {
        window.fetch(url, beaconInit(body)).await<org.w3c.fetch.Response>()
    }
}

// `RequestInit` in the Kotlin/Wasm DOM bindings has no `keepalive`, so the options object is
// built in JavaScript. detekt reads Kotlin, not the body below, so it cannot see the use.
@Suppress("UnusedParameter")
private fun beaconInit(body: String): RequestInit = js(
    """{
      return {
        method: 'POST',
        body: body,
        keepalive: true,
        headers: { 'content-type': 'application/json' },
      };
    }""",
)

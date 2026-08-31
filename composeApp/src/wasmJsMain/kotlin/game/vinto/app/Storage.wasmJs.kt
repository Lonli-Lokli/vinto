package game.vinto.app

import game.vinto.client.Vault
import kotlinx.browser.localStorage

actual fun platformVault(): Vault = LocalStorageVault()

private class LocalStorageVault : Vault {
    override fun read(key: String): String? = localStorage.getItem(key)
    override fun write(key: String, value: String) = localStorage.setItem(key, value)
    override fun erase(key: String) = localStorage.removeItem(key)
}

/**
 * The browser's own clock, reached through `js(...)` rather than `kotlin.js.Date`.
 *
 * `kotlin.js.Date` is in the **Kotlin/JS** standard library and not in Kotlin/Wasm's, so it
 * does not resolve here — the two targets share a language and not a stdlib. A one-expression
 * `js(...)` is the supported way to reach a browser global from Wasm, and it is what the
 * compiler would have generated anyway.
 */
private fun isoNow(): String = js("new Date().toISOString()")

actual fun nowIso(): String = isoNow()

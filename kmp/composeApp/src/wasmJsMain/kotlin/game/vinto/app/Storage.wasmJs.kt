package game.vinto.app

import game.vinto.client.Vault
import kotlinx.browser.localStorage

actual fun platformVault(): Vault = LocalStorageVault()

private class LocalStorageVault : Vault {
    override fun read(key: String): String? = localStorage.getItem(key)
    override fun write(key: String, value: String) = localStorage.setItem(key, value)
    override fun erase(key: String) = localStorage.removeItem(key)
}

actual fun nowIso(): String = kotlin.js.Date().toISOString()

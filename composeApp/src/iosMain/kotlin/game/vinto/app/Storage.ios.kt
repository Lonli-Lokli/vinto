package game.vinto.app

import game.vinto.client.Vault
import platform.Foundation.NSUserDefaults

actual fun platformVault(): Vault = DefaultsVault()

private class DefaultsVault : Vault {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun read(key: String): String? = defaults.stringForKey(key)
    override fun write(key: String, value: String) = defaults.setObject(value, key)
    override fun erase(key: String) = defaults.removeObjectForKey(key)
}

actual fun nowIso(): String =
    platform.Foundation.NSISO8601DateFormatter().stringFromDate(
        platform.Foundation.NSDate(),
    )

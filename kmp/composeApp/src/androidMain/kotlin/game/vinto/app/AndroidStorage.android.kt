package game.vinto.app

import android.content.Context
import game.vinto.client.MemoryVault
import game.vinto.client.Vault

/**
 * Attached once, from the activity, because `SharedPreferences` needs a `Context` and Compose
 * Multiplatform's common code has no notion of one.
 *
 * Held as the application context rather than the activity's: this outlives any one screen,
 * and holding an activity here is the classic way to leak one.
 */
object AndroidStorage {
    private var preferences: android.content.SharedPreferences? = null

    fun attach(context: Context) {
        preferences = context.applicationContext.getSharedPreferences("vinto", Context.MODE_PRIVATE)
    }

    internal fun vault(): Vault = preferences?.let(::PreferenceVault) ?: MemoryVault()
}

private class PreferenceVault(
    private val preferences: android.content.SharedPreferences,
) : Vault {
    override fun read(key: String): String? = preferences.getString(key, null)

    override fun write(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    override fun erase(key: String) {
        preferences.edit().remove(key).apply()
    }
}

actual fun platformVault(): Vault = AndroidStorage.vault()

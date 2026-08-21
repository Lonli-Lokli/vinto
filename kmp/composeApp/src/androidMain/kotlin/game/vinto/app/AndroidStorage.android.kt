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

    /**
     * The application context, for the two things that need one and are not storage: putting
     * a report on the clipboard, and handing it to the share sheet. The application context
     * rather than the activity's, for the reason above.
     */
    internal var context: Context? = null
        private set

    fun attach(context: Context) {
        this.context = context.applicationContext
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

actual fun nowIso(): String = java.time.Instant.now().toString()

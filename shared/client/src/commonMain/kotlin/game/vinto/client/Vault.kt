package game.vinto.client

/**
 * Somewhere small and durable to keep a string.
 *
 * Deliberately not a database and not a file API. What a local game needs to survive being
 * closed is one blob under one key, and everything larger — a schema, a migration story, a
 * background writer — is a cost paid for a feature nobody asked for. When a saved game grows
 * past what this can hold comfortably, that is the moment to reach for more, not before.
 *
 * Every platform already has one of these; the actuals are three lines each.
 */
interface Vault {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun erase(key: String)
}

/** For tests, and for a platform that has not been given a real one yet. */
class MemoryVault(private val entries: MutableMap<String, String> = mutableMapOf()) : Vault {
    override fun read(key: String): String? = entries[key]
    override fun write(key: String, value: String) { entries[key] = value }
    override fun erase(key: String) { entries.remove(key) }
}

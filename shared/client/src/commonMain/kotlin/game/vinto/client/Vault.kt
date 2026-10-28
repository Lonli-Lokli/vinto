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

/**
 * A vault that answers even when the storage under it refuses.
 *
 * A browser can refuse `localStorage` outright — Firefox with cookies blocked, Safari's "block all
 * cookies", a full quota — and it refuses by throwing, from every read. The web app read the vault
 * before it drew anything, so a refusing browser kept the page on "Dealing…" for good, and the
 * crash reporter, which reads the vault before it starts listening, never heard about it.
 *
 * So a refusal costs persistence and nothing else: what storage would not take is held here for
 * the session and read back from here, and a key it never had reads as absent — which every caller
 * already handles, because it is what a first launch looks like.
 */
class ForgivingVault(private val stored: Vault) : Vault {
    /** What storage would not take this session, by key; null where it would not erase. */
    private val refused = mutableMapOf<String, String?>()

    override fun read(key: String): String? =
        if (key in refused) refused[key] else runCatching { stored.read(key) }.getOrNull()

    override fun write(key: String, value: String) {
        if (runCatching { stored.write(key, value) }.isSuccess) refused.remove(key) else refused[key] = value
    }

    override fun erase(key: String) {
        if (runCatching { stored.erase(key) }.isSuccess) refused.remove(key) else refused[key] = null
    }
}

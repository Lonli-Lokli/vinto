package game.vinto.app

import game.vinto.client.Vault

/**
 * The platform's own small durable store.
 *
 * Android has `SharedPreferences`, the JVM has a file, a browser has `localStorage`, iOS has
 * `NSUserDefaults`. All four are a few lines and none of them is worth a dependency.
 */
expect fun platformVault(): Vault

/**
 * The wall clock, as an ISO-8601 string.
 *
 * Lives out here rather than anywhere near the game. Nothing in the engine, the session or the
 * recorder may read a clock — a recording that depended on one would stop being reproducible,
 * which is the only thing a bug report is for. A timestamp belongs to whoever is *asking* for
 * the report, which is this layer.
 */
expect fun nowIso(): String

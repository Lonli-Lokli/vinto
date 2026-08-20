package game.vinto.app

import game.vinto.client.Vault

/**
 * The platform's own small durable store.
 *
 * Android has `SharedPreferences`, the JVM has a file, a browser has `localStorage`, iOS has
 * `NSUserDefaults`. All four are a few lines and none of them is worth a dependency.
 */
expect fun platformVault(): Vault

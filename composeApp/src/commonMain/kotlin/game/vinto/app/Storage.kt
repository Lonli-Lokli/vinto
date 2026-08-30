package game.vinto.app

import androidx.compose.runtime.staticCompositionLocalOf
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

/**
 * The app's vault, reachable from any screen.
 *
 * A composition local for the same reason `LocalCounting` is one: the places that want to
 * write something durable are spread across screens, and threading a `Vault` through every
 * signature between `App` and a score sheet would put plumbing in a dozen parameter lists for
 * two call sites.
 *
 * Null by default, and that is the useful part rather than an oversight: a screen rendered in
 * a test, a preview or a golden writes nothing to anybody's device without a caller having
 * remembered to prevent it.
 */
val LocalVault = staticCompositionLocalOf<Vault?> { null }

package game.vinto.app

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the platform's accessibility settings ask for less motion.
 *
 * Read once per resolution in `App`, where it is combined with the player's own
 * `MotionChoice`: the setting can force motion off (or on) regardless of the platform, and
 * `SYSTEM` defers to this. Platforms that do not expose a preference — or where reading it
 * needs machinery this port has not grown yet — answer `false`, and the in-app setting is
 * the override that serves those players; see the actuals.
 */
expect fun systemPrefersReducedMotion(): Boolean

/**
 * Whether the table should move, resolved from the setting and the platform in `App` and
 * read by the stage. A composition local like `LocalFeedback`, and for the same reason:
 * every screen that plays a scene needs the answer, and none of them should be a parameter
 * longer for it.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

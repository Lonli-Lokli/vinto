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

/**
 * A multiplier on top of the player's [game.vinto.client.Pace], for callers that need the
 * table to stop waiting.
 *
 * The stage is built out of *pauses* — a beat where a bot would be thinking, a dwell after a
 * drawn card is turned over, a gap between the scenes of one move — and they are the point
 * (see `Pacing`). But they are pauses in a real clock, and that makes a whole round something
 * only a person with two minutes can watch. A test that plays a round out is not watching it,
 * and neither is anything else that runs the table with nobody in front of it.
 *
 * Nothing about the *game* changes when this is zero: every frame still goes through the same
 * queue in the same order, every scene is still prepared, played and reconciled. What goes is
 * the waiting, which is the only part that was ever about a human being present.
 *
 * It is deliberately not a [game.vinto.client.Pace] value. Pace is a setting a player chose
 * and one this must not be able to overwrite — "instant" is a property of who is watching,
 * not a speed somebody asked for, and putting it in the enum would put it in the settings
 * screen and in the vault.
 */
val LocalPacing = staticCompositionLocalOf { 1f }

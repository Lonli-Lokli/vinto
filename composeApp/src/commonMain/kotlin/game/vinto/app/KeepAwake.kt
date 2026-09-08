package game.vinto.app

import androidx.compose.runtime.Composable

/**
 * Holds the screen awake for as long as this is composed and [on] is true.
 *
 * Composed rather than called, because the thing being asked for is a *lease*: every platform
 * below hands the permission back when the composition leaves, so a player who quits the round,
 * opens the settings or closes the tab stops paying for it without anything having to remember
 * to say so. A `keepAwake(true)` with a matching `keepAwake(false)` somewhere else is the shape
 * that leaks a lit screen, and the leak is invisible until somebody's battery is flat.
 *
 * Every actual is absent-safe in the same way the rest of the platform seams are: a target with
 * no way to ask simply does not, and the game plays identically.
 */
@Composable
expect fun KeepAwake(on: Boolean)

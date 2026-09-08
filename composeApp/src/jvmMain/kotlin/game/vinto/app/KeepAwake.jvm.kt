package game.vinto.app

import androidx.compose.runtime.Composable

/**
 * Nothing to hold.
 *
 * A desktop's screen saver is the operating system's business and there is no portable way to
 * ask it to wait — the ones that exist are per-platform native calls, and the desktop build is a
 * maintainer's window for looking at the UI rather than something anybody plays a round on.
 */
@Composable
actual fun KeepAwake(on: Boolean) = Unit

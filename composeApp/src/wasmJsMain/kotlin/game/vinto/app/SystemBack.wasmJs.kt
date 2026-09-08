package game.vinto.app

import androidx.compose.runtime.Composable

/** Nothing to honour here: see the expect declaration. */
@Composable
actual fun SystemBack(enabled: Boolean, onBack: () -> Unit) = Unit

/** The browser has history, but this app puts nothing in it. */
actual val hasSystemBack: Boolean = false

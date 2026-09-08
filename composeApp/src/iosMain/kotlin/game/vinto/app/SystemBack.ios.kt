package game.vinto.app

import androidx.compose.runtime.Composable

/** Nothing to honour here: see the expect declaration. */
@Composable
actual fun SystemBack(enabled: Boolean, onBack: () -> Unit) = Unit

/** The edge swipe belongs to a navigation stack this app does not have. */
actual val hasSystemBack: Boolean = false

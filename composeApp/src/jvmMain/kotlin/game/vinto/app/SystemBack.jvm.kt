package game.vinto.app

import androidx.compose.runtime.Composable

/** Nothing to honour here: see the expect declaration. */
@Composable
actual fun SystemBack(enabled: Boolean, onBack: () -> Unit) = Unit

/** A desktop window has no back at all. */
actual val hasSystemBack: Boolean = false

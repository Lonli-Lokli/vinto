package game.vinto.app

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun SystemBack(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

/** The gesture is real here, and a screen that ignores it closes the app. */
actual val hasSystemBack: Boolean = true

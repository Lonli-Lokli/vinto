package game.vinto.app

import androidx.compose.runtime.Composable

/**
 * The platform's own way of going back, honoured.
 *
 * On Android this is a hardware promise: a screen that does not answer the back gesture closes
 * the app instead, which from a settings screen looks exactly like a crash. Every other target
 * has its own arrangement — a browser has history, iOS has an edge swipe belonging to a
 * navigation stack this app does not have — so their actuals do nothing, and the on-screen
 * "Back" is what those platforms use.
 */
@Composable
expect fun SystemBack(enabled: Boolean, onBack: () -> Unit)

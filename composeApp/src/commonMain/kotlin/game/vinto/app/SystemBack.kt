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

/**
 * Whether [SystemBack] is actually wired to anything on this platform.
 *
 * Android is the only true: its actual is a real `BackHandler`, and the gesture is a hardware
 * promise. Everywhere else the actual is `Unit`, so a screen that offers no exit of its own has
 * none at all — which is what a solo round on the web was, reachable only until it ended.
 *
 * Read by the table, to decide whether to draw a way out in its header. A screen asking "does
 * this platform go back by itself?" is asking about a capability rather than about a brand,
 * which is why this sits beside the handler rather than in a `when` on some platform name.
 */
expect val hasSystemBack: Boolean

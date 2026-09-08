package game.vinto.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * `View.keepScreenOn`, which is the window flag without the window.
 *
 * Set on the composition's own view rather than on the activity, so the flag belongs to the
 * thing that asked for it: `FLAG_KEEP_SCREEN_ON` on the activity outlives every screen in it and
 * has to be cleared by hand, which is the leak this seam exists to make impossible.
 */
@Composable
actual fun KeepAwake(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
}

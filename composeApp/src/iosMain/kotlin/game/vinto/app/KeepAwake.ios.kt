package game.vinto.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication

/**
 * `idleTimerDisabled`, which is a property of the whole application rather than of a view.
 *
 * So it is set on the way in and cleared on the way out, and the `DisposableEffect` is what
 * guarantees the second half — UIKit has no notion of the request belonging to a screen, and a
 * flag left on here is a phone that never sleeps again until it is force-quit.
 */
@Composable
actual fun KeepAwake(on: Boolean) {
    DisposableEffect(on) {
        UIApplication.sharedApplication.idleTimerDisabled = on
        onDispose { UIApplication.sharedApplication.idleTimerDisabled = false }
    }
}

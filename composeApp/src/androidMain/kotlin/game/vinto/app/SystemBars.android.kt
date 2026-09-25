package game.vinto.app

import android.app.Activity
import android.view.View
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
actual fun SystemBars(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        chosenDark = dark
        showBarIcons(window, view, dark)
    }
}

/**
 * Puts the app's own bar icons back after something outside Compose has re-applied the window
 * theme's over them.
 *
 * The launch screen's hand-over does exactly that on Android 12 and 12L: the splash library
 * re-applies the post-splash theme's bars just before its exit animation, and the platform
 * restores them again when the launch screen is removed. The theme follows the phone, and the
 * icons [SystemBars] chose follow the app's own theme setting, so a player whose setting
 * disagrees with the phone would be left with the wrong set until something recomposed.
 * `MainActivity` calls this at both points. Before the first composition has chosen there is
 * nothing to put back, and it does nothing.
 */
fun reassertSystemBars(activity: Activity) {
    val dark = chosenDark ?: return
    showBarIcons(activity.window, activity.window.decorView, dark)
}

/** The last choice [SystemBars] made. Read and written on the main thread only. */
private var chosenDark: Boolean? = null

private fun showBarIcons(window: Window, view: View, dark: Boolean) {
    WindowCompat.getInsetsController(window, view).run {
        isAppearanceLightStatusBars = !dark
        isAppearanceLightNavigationBars = !dark
    }
}

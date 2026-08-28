package game.vinto.app

import android.provider.Settings

/**
 * Android's "remove animations" accessibility switch zeroes the animator duration scale,
 * which is the documented way for an app to notice it. Read through the application context
 * [AndroidStorage] already holds; before `attach` has run there is nothing to read and the
 * answer is the default.
 */
actual fun systemPrefersReducedMotion(): Boolean {
    val resolver = AndroidStorage.context?.contentResolver ?: return false
    val scale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    return scale == 0f
}

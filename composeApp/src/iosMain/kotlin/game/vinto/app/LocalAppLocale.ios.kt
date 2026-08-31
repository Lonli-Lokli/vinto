package game.vinto.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages

actual object LocalAppLocale {
    /**
     * The key iOS itself reads a language preference from, so writing it is what makes the
     * resource lookup underneath Compose answer in the chosen language.
     */
    private const val LANGUAGES = "AppleLanguages"

    private val device = NSLocale.preferredLanguages.first() as String
    private val Chosen = staticCompositionLocalOf { device }

    actual val current: String
        @Composable get() = Chosen.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val defaults = NSUserDefaults.standardUserDefaults
        if (value == null) {
            // Removed rather than set back to the device's language: leaving the key behind
            // would pin whatever it last held, so "follow the device" would stop following it
            // the moment the device changed.
            defaults.removeObjectForKey(LANGUAGES)
        } else {
            defaults.setObject(listOf(value), LANGUAGES)
        }
        return Chosen.provides(value ?: device)
    }
}

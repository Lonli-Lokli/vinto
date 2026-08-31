package game.vinto.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

actual object LocalAppLocale {
    /** The device's own, remembered on the first override so "follow the device" can return. */
    private var device: Locale? = null

    actual val current: String
        @Composable get() = Locale.getDefault().toString()

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val configuration = LocalConfiguration.current
        if (device == null) device = Locale.getDefault()

        val chosen = value?.let(::Locale) ?: device!!
        Locale.setDefault(chosen)
        configuration.setLocale(chosen)
        val resources = LocalContext.current.resources
        @Suppress("DEPRECATION") // The replacement makes a new Context; this updates the one in hand.
        resources.updateConfiguration(configuration, resources.displayMetrics)
        return LocalConfiguration.provides(configuration)
    }
}

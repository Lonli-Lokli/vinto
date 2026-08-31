package game.vinto.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

actual object LocalAppLocale {
    private var device: Locale? = null
    private val Chosen = staticCompositionLocalOf { Locale.getDefault().toString() }

    actual val current: String
        @Composable get() = Chosen.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        if (device == null) device = Locale.getDefault()
        val chosen = value?.let(::Locale) ?: device!!
        Locale.setDefault(chosen)
        return Chosen.provides(chosen.toString())
    }
}

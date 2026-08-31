package game.vinto.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * The language the app is read in, chosen in the app rather than in the phone's settings.
 *
 * **This is a workaround, and it is JetBrains' own.** Compose Multiplatform's resource lookup
 * reads the *system* locale, and the two things that would let a caller name a different one —
 * `ResourceEnvironment`'s constructor and `LocalComposeEnvironment` — are both `internal`.
 * Checked by compiling against them rather than assumed: the first answers "this is internal
 * API of the Compose gradle plugin", the second "cannot access… it is internal in file".
 *
 * So instead of telling the resource layer which language to use, each platform is told to
 * *be* that language, underneath it. `compose-resource-environment.html` documents this as the
 * approach until a common API exists, and every actual here is that page's, adapted only where
 * this project's own conventions differ.
 *
 * Two things make it work and both are easy to leave out:
 *
 * - **`key(language)`** throws away the composition when the language changes. Resource lookups
 *   are not observable state, so nothing recomposes on its own — without this the app keeps
 *   drawing the old words until something else happens to invalidate it.
 * - **Each actual writes through to the platform**, not just to a composition local: Android
 *   updates the `Configuration`, iOS sets `AppleLanguages`, the JVM sets the default `Locale`,
 *   and the browser shadows `navigator.languages`. The composition local alone would change
 *   nothing, because it is not what `stringResource` reads.
 *
 * Revisit when Compose Multiplatform ships a public API; this whole file goes at that point.
 */
expect object LocalAppLocale {
    val current: String
        @Composable get

    @Composable
    infix fun provides(value: String?): ProvidedValue<*>
}

/**
 * Wraps the app in the chosen language, or leaves it in the device's when none is chosen.
 *
 * @param language a BCP-47 tag, or null to follow the device.
 */
@Composable
fun InLanguage(language: String?, content: @Composable () -> Unit) {
    // Set from the chosen language rather than left to the platform, because the platforms do
    // not agree: Android derives it from the `Configuration` this file has just updated, and
    // the browser and the desktop do not derive it at all. Stating it once here is what makes
    // Hebrew read the same way on all four.
    val direction = when {
        language == null -> LocalLayoutDirection.current
        Language.withTag(language)?.rightToLeft == true -> LayoutDirection.Rtl
        else -> LayoutDirection.Ltr
    }

    CompositionLocalProvider(
        LocalAppLocale provides language,
        LocalLayoutDirection provides direction,
    ) {
        key(language) { content() }
    }
}

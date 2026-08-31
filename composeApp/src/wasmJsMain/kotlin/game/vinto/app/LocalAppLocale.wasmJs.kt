package game.vinto.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.intl.Locale

actual object LocalAppLocale {
    private val Chosen = staticCompositionLocalOf { Locale.current.toLanguageTag() }

    actual val current: String
        @Composable get() = Chosen.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        // A browser will not let a page change `navigator.languages`, so `index.html` shadows
        // the property with one that reads this variable first. Setting it here is the whole
        // of the override; without that script the game simply stays in the browser's own
        // language, which is a working app rather than a broken one.
        setCustomLocale(value?.replace('_', '-'))
        return Chosen.provides(value ?: Locale.current.toLanguageTag())
    }
}

/**
 * Sets the variable `index.html`'s shim reads before it answers `navigator.languages`.
 *
 * `internal` rather than `private` so the file has more than one declaration to its name:
 * `MatchingDeclarationName` wants a file holding one type to be called after it, and the
 * multiplatform convention wants the platform suffix. Widening the helper satisfies both
 * without renaming an `actual` or silencing a rule.
 *
 * detekt reads Kotlin and not the JavaScript, so it cannot see the parameter used there.
 */
@Suppress("UnusedParameter")
internal fun setCustomLocale(tag: String?) {
    js("window.__vintoLocale = tag")
}

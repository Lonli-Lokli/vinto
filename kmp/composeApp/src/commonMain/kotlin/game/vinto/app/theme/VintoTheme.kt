package game.vinto.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The look of the table.
 *
 * Built on Material 3 rather than against it: the components come with touch targets, focus
 * rings and dynamic type already right, and a card game has no reason to re-litigate any of
 * that. What is overridden is the palette, because a felt table and a deck of cards are the
 * one thing Material's defaults cannot know about.
 *
 * Both schemes are defined outright rather than derived from a seed colour. A generated dark
 * scheme puts the cards and the felt within a few steps of each other, and a card you cannot
 * pick out of the background is the only thing on this screen that has to work.
 */
private val Felt = Color(0xFF1B5E43)
private val FeltDark = Color(0xFF0E3428)
private val Gold = Color(0xFFC9A227)
private val Ink = Color(0xFF14181B)
private val Paper = Color(0xFFF7F5EF)

private val LightScheme = lightColorScheme(
    primary = Felt,
    onPrimary = Paper,
    primaryContainer = Color(0xFFB7E4CE),
    onPrimaryContainer = Color(0xFF06281C),
    secondary = Gold,
    onSecondary = Ink,
    background = Color(0xFFEFEDE6),
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFDCDDD6),
    onSurfaceVariant = Color(0xFF44483F),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF6FD3A6),
    onPrimary = Color(0xFF003824),
    primaryContainer = FeltDark,
    onPrimaryContainer = Color(0xFFB7E4CE),
    secondary = Gold,
    onSecondary = Ink,
    background = Color(0xFF10130F),
    onBackground = Color(0xFFE2E3DC),
    surface = Color(0xFF1B1F1B),
    onSurface = Color(0xFFE2E3DC),
    surfaceVariant = Color(0xFF3F473F),
    onSurfaceVariant = Color(0xFFC0C9BE),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)

/**
 * The felt itself.
 *
 * Not a colour-scheme role, because it is not one: Material's surfaces are for panels a user
 * reads, and this is a playing surface that has to sit *behind* white cards without competing
 * with them. Two greens with a gradient between, as on the web table, so the middle of the
 * table reads as further away than its edges.
 */
fun ColorScheme.feltGradient(): List<Color> =
    if (isDarkFelt()) listOf(FeltDarkTop, FeltDarkBottom) else listOf(FeltLightTop, FeltLightBottom)

fun ColorScheme.feltEdge(): Color = if (isDarkFelt()) FeltRimDark else FeltRimLight

/**
 * Anything written *on* the felt.
 *
 * The same near-white in both schemes, because the felt is dark green in both. Using
 * `onPrimary` for this was wrong in a way that only showed up in dark mode: there it is a
 * dark green meant to sit on a light button, and the pile labels vanished into the table.
 */
@Suppress("UnusedReceiverParameter")
fun ColorScheme.onFelt(): Color = FeltInk

private val FeltInk = Color(0xFFF2F5F0)

private val FeltDarkTop = Color(0xFF14442F)
private val FeltDarkBottom = Color(0xFF0A2A1D)
private val FeltLightTop = Color(0xFF1E6B4C)
private val FeltLightBottom = Color(0xFF124A33)
private val FeltRimDark = Color(0xFF2C7A57)
private val FeltRimLight = Color(0xFF0E3A28)

/** The dark scheme is the one whose background is darker than its surface. */
private fun ColorScheme.isDarkFelt(): Boolean = background.luminanceIsLow()

private fun Color.luminanceIsLow(): Boolean = (red + green + blue) < LOW_LUMINANCE

private const val LOW_LUMINANCE = 1.0f

@Composable
fun VintoTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        content = content,
    )
}

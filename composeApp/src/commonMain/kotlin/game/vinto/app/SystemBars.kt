package game.vinto.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Tells the phone which way round its own status and navigation icons should be drawn.
 *
 * The bars are transparent and the app draws behind them, so the icons in them are the one
 * part of this screen the app does not paint and still has to be legible. They come in two
 * sets, light and dark, and which one is right depends on what is behind them — which, since
 * the theme became real, is either slate or paper.
 *
 * Not derived from the system's own night setting: the theme is a setting *in the app*, and a
 * player who picks Light on a phone in dark mode would otherwise get white icons on paper.
 *
 * @param dark whether the app is drawing its dark scheme, in which case the icons are light.
 */
@Composable
expect fun SystemBars(dark: Boolean)

/**
 * What a screen has under the system bars: the rail, or the felt.
 *
 * The app pads its content out of the bars' way and paints what is behind them itself. It painted
 * the rail behind them on every screen, so the menus, whose own ground is the felt, stood between
 * two cream bars — on the phone and in every store shot of them (review, 2026-10-12). Each screen
 * now says which it is, and the strips continue it: the felt's top colour above, its bottom colour
 * below, which are exactly the colours the screen's own gradient starts and ends on. And the icons
 * in the bars are drawn light over the felt in either theme, because the felt is dark in both.
 */
enum class Behind { RAIL, FELT }

/** The current screen's [Behind], held by `App`, which paints it. */
val LocalBehind = staticCompositionLocalOf<MutableState<Behind>?> { null }

/** Declares what this screen has under the bars. */
@Composable
fun BehindTheBars(behind: Behind) {
    val held = LocalBehind.current
    SideEffect { held?.value = behind }
}

/**
 * The padding that keeps clear of a status column, on the edge it stands at, for what has to keep
 * clear of it itself: whatever opens over a table — a sheet — and the room's lobby, which shares a
 * table's screen. Nothing on every other screen, which the app has already kept clear of it.
 *
 * The closed iPhone Duo is the one phone with a column: iOS 27.1 stands its clock, its connectivity
 * and the camera down the right edge, 84 of 466 points (measured on the Duo simulator, Xcode 27.1),
 * and reserves nothing at the top. A table stands its own controls in it ([game.vinto.app.game.Chrome],
 * which says how a column is told from the insets). A padding rather than a width, because it is on
 * the right of the glass whichever way the language reads, and an end padding put it on the left of
 * an Arabic or a Hebrew screen.
 */
val LocalStatusColumn = staticCompositionLocalOf { PaddingValues(0.dp) }

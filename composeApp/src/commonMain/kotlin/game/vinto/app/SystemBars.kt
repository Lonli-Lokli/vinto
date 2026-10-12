package game.vinto.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
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
 * The status column the system keeps down the screen's end edge, or zero where it keeps a bar
 * across the top instead — which is every phone but one.
 *
 * The closed iPhone Duo is the one: iOS 27.1 stands its clock, its connectivity and the camera in a
 * column down the right edge, 84 of 466 points (measured on the Duo simulator, Xcode 27.1), and
 * reserves nothing at the top. Apple's own toolbars stack their buttons in it, under the status, and
 * so does the table's header ([LocalStatusColumn]). Told from the insets alone — a wide end inset
 * with nothing above — so it needs nothing from the platform but what every screen already asks.
 */
internal fun statusColumn(top: Dp, end: Dp): Dp = if (end >= ColumnFrom && top < ColumnTopMost) end else 0.dp

/** Narrower than this, an end inset is a rounded corner or a cut-out rather than a column. */
private val ColumnFrom = 56.dp

/** And a screen with a real bar across the top keeps its status there. */
private val ColumnTopMost = 24.dp

/**
 * The status column's width, where a screen has to keep clear of it itself: a table, which stands
 * its header there, and whatever opens over a table, a sheet. Zero where there is no column, and
 * on every other screen, which the app has already kept clear of it.
 */
val LocalStatusColumn = staticCompositionLocalOf { 0.dp }

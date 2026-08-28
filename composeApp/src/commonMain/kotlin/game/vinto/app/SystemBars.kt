package game.vinto.app

import androidx.compose.runtime.Composable

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

package game.vinto.app

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * WCAG 2.1's arithmetic, in one place.
 *
 * Shared by [ContrastTest], which measures the palette — every pair of colours somebody
 * declared — and [ScreenContrastTest], which measures the screens: every piece of text the
 * app actually drew, against the pixels behind it. Two gates, one definition of "can be
 * read", because two copies of this math would eventually disagree about what a pass is.
 */
object Wcag {

    /** What text needs against its background — SC 1.4.3. */
    const val TEXT = 4.5

    /** Large-scale text, and the visual information that identifies a control — SC 1.4.11. */
    const val UI = 3.0

    private const val OFFSET = 0.05
    private const val LINEAR_MAX = 0.03928
    private const val LINEAR_DIV = 12.92
    private const val GAMMA = 2.4
    private const val A = 0.055
    private const val R = 0.2126
    private const val G = 0.7152
    private const val B = 0.0722

    fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sorted().reversed()
        return (hi + OFFSET) / (lo + OFFSET)
    }

    /** Relative luminance, as WCAG 2.1 defines it. */
    fun luminance(c: Color): Double {
        fun channel(v: Float): Double {
            val d = v.toDouble()
            return if (d <= LINEAR_MAX) d / LINEAR_DIV else ((d + A) / (1 + A)).pow(GAMMA)
        }
        return R * channel(c.red) + G * channel(c.green) + B * channel(c.blue)
    }

    /**
     * A colour drawn at less than full alpha is really the colour it is drawn over — an
     * indicator that fades is only as good as its faintest frame.
     */
    fun over(fg: Color, bg: Color): Color =
        if (fg.alpha == 1f) {
            fg
        } else {
            Color(
                red = fg.red * fg.alpha + bg.red * (1 - fg.alpha),
                green = fg.green * fg.alpha + bg.green * (1 - fg.alpha),
                blue = fg.blue * fg.alpha + bg.blue * (1 - fg.alpha),
            )
        }
}

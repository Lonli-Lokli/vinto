package game.vinto.app

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * The three marks, drawn rather than fetched from an icon font — the rule this app follows
 * everywhere, and the reason they look the same on Android, iOS and the web.
 *
 * Strokes at the same weight as the deck's engraving, so a settings row and a card come from one
 * hand. Each is a fraction of its own box, so the size above is the only thing to change.
 */
internal fun DrawScope.drawSpeaker(ink: Color) {
    val w = size.minDimension
    val body = Path().apply {
        moveTo(w * CONE_BACK, w * CONE_INNER_TOP)
        lineTo(w * CONE_WAIST, w * CONE_INNER_TOP)
        lineTo(w * CONE_FRONT, w * CONE_TOP)
        lineTo(w * CONE_FRONT, w * CONE_BOTTOM)
        lineTo(w * CONE_WAIST, w * CONE_INNER_BOTTOM)
        lineTo(w * CONE_BACK, w * CONE_INNER_BOTTOM)
        close()
    }
    drawPath(body, color = ink)
    // Two arcs rather than three: at 18 dp a third ring closes up into a smudge.
    repeat(WAVES) { i ->
        val r = w * (WAVE_NEAR + WAVE_STEP * i)
        drawArc(
            color = ink,
            startAngle = WAVE_START,
            sweepAngle = WAVE_SWEEP,
            useCenter = false,
            topLeft = Offset(w * WAVE_FROM - r, w * MIDDLE - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = w * WAVE_PEN, cap = StrokeCap.Round),
        )
    }
}

internal fun DrawScope.drawBuzz(ink: Color) {
    val w = size.minDimension
    // A handset, and the two short strokes that say it moved.
    drawRoundRect(
        color = ink,
        topLeft = Offset(w * PHONE_X, w * PHONE_TOP),
        size = Size(w * PHONE_W, w * PHONE_H),
        cornerRadius = CornerRadius(w * PHONE_CORNER, w * PHONE_CORNER),
        style = Stroke(width = w * PHONE_PEN),
    )
    listOf(-1f, 1f).forEach { side ->
        val x = w * MIDDLE + side * w * BUZZ_OUT
        drawLine(
            color = ink,
            start = Offset(x, w * BUZZ_TOP),
            end = Offset(x, w * BUZZ_BOTTOM),
            strokeWidth = w * PHONE_PEN,
            cap = StrokeCap.Round,
        )
    }
}

internal fun DrawScope.drawTally(ink: Color) {
    val w = size.minDimension
    // Three bars of different heights: a count, which is all this setting is about.
    BAR_HEIGHTS.forEachIndexed { i, tall ->
        val x = w * (BAR_NEAR + BAR_STEP * i)
        drawLine(
            color = ink,
            start = Offset(x, w * BAR_FOOT),
            end = Offset(x, w * (BAR_FOOT - tall * BAR_REACH)),
            strokeWidth = w * BAR_PEN,
            cap = StrokeCap.Round,
        )
    }
}

// Every number here is a fraction of the mark's own box, so the size in `SettingsScreen` is the
// only thing to change — the same discipline `GeneratedAvatar` follows, and for the same reason:
// these are drawn at 18 dp in a settings row and would be a smudge at any fixed stroke width.
private const val CONE_BACK = 0.10f
private const val CONE_WAIST = 0.28f
private const val CONE_FRONT = 0.48f
private const val CONE_TOP = 0.16f
private const val CONE_BOTTOM = 0.84f
private const val CONE_INNER_TOP = 0.36f
private const val CONE_INNER_BOTTOM = 0.64f
private const val WAVES = 2
private const val WAVE_NEAR = 0.20f
private const val WAVE_STEP = 0.14f
private const val WAVE_FROM = 0.52f
private const val WAVE_START = -50f
private const val WAVE_SWEEP = 100f
private const val WAVE_PEN = 0.07f

private const val PHONE_X = 0.30f
private const val PHONE_TOP = 0.16f
private const val PHONE_W = 0.34f
private const val PHONE_H = 0.68f
private const val PHONE_CORNER = 0.08f
private const val PHONE_PEN = 0.08f
private const val BUZZ_OUT = 0.40f
private const val BUZZ_TOP = 0.38f
private const val BUZZ_BOTTOM = 0.62f

private val BAR_HEIGHTS = listOf(0.42f, 0.66f, 0.86f)
private const val BAR_NEAR = 0.22f
private const val BAR_STEP = 0.28f
private const val BAR_FOOT = 0.86f
private const val BAR_REACH = 0.72f
private const val BAR_PEN = 0.10f

/** The middle of the box, which three of the marks measure from. */
private const val MIDDLE = 0.5f

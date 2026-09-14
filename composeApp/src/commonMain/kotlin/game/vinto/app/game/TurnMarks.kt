package game.vinto.app.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.abs

/**
 * The mark between the two ends of a planned trade: two arrows, one each way.
 *
 * Drawn rather than typed — the obvious character for it came out as a box in the golden, and
 * a glyph in the rail's own ink is one decision made once. It is the one mark the plan's
 * sentence still draws: the rest of a turn is words, because two people looked at a row of
 * marks and could not say what any of it meant.
 */
internal fun DrawScope.drawTrade(ink: Color) {
    val w = size.minDimension
    val pen = w * PEN
    arrow(ink, pen, y = w * TRADE_TOP, from = w * TRADE_FROM, to = w * TRADE_TO)
    arrow(ink, pen, y = w * TRADE_BOTTOM, from = w * TRADE_TO, to = w * TRADE_FROM)
}

private fun DrawScope.arrow(ink: Color, pen: Float, y: Float, from: Float, to: Float) {
    val head = (to - from) * HEAD
    drawLine(ink, Offset(from, y), Offset(to, y), pen, StrokeCap.Round)
    drawLine(ink, Offset(to, y), Offset(to - head, y - abs(head)), pen, StrokeCap.Round)
    drawLine(ink, Offset(to, y), Offset(to - head, y + abs(head)), pen, StrokeCap.Round)
}

private const val PEN = 0.1f
private const val TRADE_TOP = 0.36f
private const val TRADE_BOTTOM = 0.64f
private const val TRADE_FROM = 0.15f
private const val TRADE_TO = 0.85f
private const val HEAD = 0.3f

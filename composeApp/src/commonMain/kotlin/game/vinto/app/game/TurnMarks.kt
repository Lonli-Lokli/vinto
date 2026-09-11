package game.vinto.app.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * The marks a planned turn is drawn with: where its card comes from, and what becomes of it.
 *
 * **Strokes in the rail's own ink**, like the gear and the exit in the header — not emoji, which
 * would make the row three design languages deep, and not words, because the row has to read at
 * a glance and there are nineteen languages to say it in. A planned turn is a *sequence*, so the
 * belt draws a sequence; the marks are what make that legible without a sentence under it.
 *
 * Each is drawn into a square and scales off `size.minDimension`, so one glyph serves the belt,
 * a legend and whatever comes next.
 */

/** The deck: a stack of cards, face down, that nobody has seen the top of. */
internal fun DrawScope.drawDeck(ink: Color) {
    val w = size.minDimension
    val pen = Stroke(width = w * PEN, cap = StrokeCap.Round)
    // Two cards offset, because one rectangle is a card and two are a pile you draw from.
    drawRoundedCard(ink, pen, x = w * DECK_BACK_X, y = w * DECK_BACK_Y, w = w * CARD_W, h = w * CARD_H)
    drawRoundedCard(ink, pen, x = w * DECK_FRONT_X, y = w * DECK_FRONT_Y, w = w * CARD_W, h = w * CARD_H)
}

/** The discard pile: one card, face up, with its corner turned over. */
internal fun DrawScope.drawPile(ink: Color) {
    val w = size.minDimension
    val pen = Stroke(width = w * PEN, cap = StrokeCap.Round)
    drawRoundedCard(ink, pen, x = w * PILE_X, y = w * PILE_Y, w = w * CARD_W, h = w * CARD_H)
    // The turned corner is what says "face up" without drawing a rank on it.
    val fold = Path().apply {
        moveTo(w * (PILE_X + CARD_W - FOLD), w * PILE_Y)
        lineTo(w * (PILE_X + CARD_W), w * (PILE_Y + FOLD))
        lineTo(w * (PILE_X + CARD_W - FOLD), w * (PILE_Y + FOLD))
        close()
    }
    drawPath(fold, color = ink, style = pen)
}

/** Play it: a card with a spark leaving it — the action going off. */
internal fun DrawScope.drawPlay(ink: Color) {
    val w = size.minDimension
    val pen = Stroke(width = w * PEN, cap = StrokeCap.Round)
    drawRoundedCard(ink, pen, x = w * PLAY_X, y = w * PILE_Y, w = w * CARD_W, h = w * CARD_H)
    // Three short rays off the top corner: a card doing something, rather than a card sitting.
    for (i in 0 until PLAY_RAYS) {
        val t = PLAY_RAY_FROM + i * PLAY_RAY_STEP
        drawLine(
            ink,
            Offset(w * (PLAY_X + CARD_W + PLAY_GAP), w * t),
            Offset(w * (PLAY_X + CARD_W + PLAY_GAP + PLAY_RAY), w * t),
            pen.width,
            StrokeCap.Round,
        )
    }
}

/** Keep it: an arrow going into the hand, and one leaving — the card traded for one of yours. */
internal fun DrawScope.drawKeep(ink: Color) {
    val w = size.minDimension
    val pen = Stroke(width = w * PEN, cap = StrokeCap.Round)
    drawRoundedCard(ink, pen, x = w * KEEP_X, y = w * PILE_Y, w = w * CARD_W, h = w * CARD_H)
    arrow(
        ink,
        pen,
        from = Offset(w * KEEP_IN_FROM, w * KEEP_HIGH),
        to = Offset(w * KEEP_IN_TO, w * KEEP_HIGH),
    )
    arrow(
        ink,
        pen,
        from = Offset(w * KEEP_OUT_FROM, w * KEEP_LOW),
        to = Offset(w * KEEP_OUT_TO, w * KEEP_LOW),
    )
}

/** Let it go: a card with an arrow away from it, and nothing coming back. */
internal fun DrawScope.drawBin(ink: Color) {
    val w = size.minDimension
    val pen = Stroke(width = w * PEN, cap = StrokeCap.Round)
    drawRoundedCard(ink, pen, x = w * BIN_X, y = w * PILE_Y, w = w * CARD_W, h = w * CARD_H)
    arrow(ink, pen, from = Offset(w * BIN_FROM, w * BIN_MID), to = Offset(w * BIN_TO, w * BIN_MID))
}

/** Throw in: two cards coming down onto the same place at once, which is what a window is. */
internal fun DrawScope.drawToss(ink: Color) {
    val w = size.minDimension
    val pen = Stroke(width = w * PEN, cap = StrokeCap.Round)
    drawRoundedCard(ink, pen, x = w * TOSS_X, y = w * TOSS_Y, w = w * CARD_W, h = w * CARD_H)
    for (dx in listOf(-1f, 1f)) {
        arrow(
            ink,
            pen,
            from = Offset(w * (TOSS_MID + dx * TOSS_SPREAD), w * TOSS_TOP),
            to = Offset(w * (TOSS_MID + dx * TOSS_LAND), w * TOSS_Y),
        )
    }
}

/**
 * Two arrows head to tail: a trade, drawn rather than typed.
 *
 * The obvious "↔" is a character, and a character is a bet on the font — the belt's chip came
 * out with a box in it the first time, and a box between two card names is worse than no
 * separator at all. This one cannot fail to render.
 */
internal fun DrawScope.drawTrade(ink: Color) {
    val w = size.minDimension
    val pen = Stroke(width = w * PEN, cap = StrokeCap.Round)
    arrow(
        ink,
        pen,
        from = Offset(w * TRADE_RIGHT, w * TRADE_HIGH),
        to = Offset(w * TRADE_LEFT, w * TRADE_HIGH),
    )
    arrow(
        ink,
        pen,
        from = Offset(w * TRADE_LEFT, w * TRADE_LOW),
        to = Offset(w * TRADE_RIGHT, w * TRADE_LOW),
    )
}

private const val TRADE_LEFT = 0.14f
private const val TRADE_RIGHT = 0.86f
private const val TRADE_HIGH = 0.34f
private const val TRADE_LOW = 0.66f

/** One card's outline, at a size the row can line up. */
private fun DrawScope.drawRoundedCard(
    ink: Color,
    pen: Stroke,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
) {
    val path = Path().apply {
        moveTo(x, y)
        lineTo(x + w, y)
        lineTo(x + w, y + h)
        lineTo(x, y + h)
        close()
    }
    drawPath(path, color = ink, style = pen)
}

/** A line with a head on it. Two of these say "in and out" where one says "away". */
private fun DrawScope.arrow(ink: Color, pen: Stroke, from: Offset, to: Offset) {
    drawLine(ink, from, to, pen.width, StrokeCap.Round)
    val back = if (to.x >= from.x) -1f else 1f
    val head = size.minDimension * HEAD
    val lift = if (to.y >= from.y) -1f else 1f
    val alongY = kotlin.math.abs(to.y - from.y) > kotlin.math.abs(to.x - from.x)
    if (alongY) {
        drawLine(ink, to, to + Offset(-head, lift * head), pen.width, StrokeCap.Round)
        drawLine(ink, to, to + Offset(head, lift * head), pen.width, StrokeCap.Round)
    } else {
        drawLine(ink, to, to + Offset(back * head, -head), pen.width, StrokeCap.Round)
        drawLine(ink, to, to + Offset(back * head, head), pen.width, StrokeCap.Round)
    }
}

private const val PEN = 0.07f
private const val HEAD = 0.13f
private const val CARD_W = 0.30f
private const val CARD_H = 0.44f

private const val DECK_BACK_X = 0.18f
private const val DECK_BACK_Y = 0.22f
private const val DECK_FRONT_X = 0.30f
private const val DECK_FRONT_Y = 0.34f

private const val PILE_X = 0.16f
private const val PILE_Y = 0.28f
private const val FOLD = 0.11f

private const val PLAY_X = 0.12f
private const val PLAY_GAP = 0.06f
private const val PLAY_RAY = 0.16f
private const val PLAY_RAYS = 3
private const val PLAY_RAY_FROM = 0.34f
private const val PLAY_RAY_STEP = 0.16f

private const val KEEP_X = 0.10f
private const val KEEP_HIGH = 0.36f
private const val KEEP_LOW = 0.62f
private const val KEEP_IN_FROM = 0.88f
private const val KEEP_IN_TO = 0.46f
private const val KEEP_OUT_FROM = 0.46f
private const val KEEP_OUT_TO = 0.88f

private const val BIN_X = 0.10f
private const val BIN_MID = 0.50f
private const val BIN_FROM = 0.46f
private const val BIN_TO = 0.88f

private const val TOSS_X = 0.35f
private const val TOSS_Y = 0.60f
private const val TOSS_MID = 0.50f
private const val TOSS_SPREAD = 0.34f
private const val TOSS_LAND = 0.10f
private const val TOSS_TOP = 0.16f

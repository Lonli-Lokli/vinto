package game.vinto.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The marks that used to be characters: a tick, a cross, a two-way arrow, a circular arrow, a
 * trophy, a burst and a frown.
 *
 * Every one of them was a symbol or an emoji typed into a string, and a typed symbol is whatever
 * the phone's own fonts make of it: a colour emoji on one platform, a thin outline on another, an
 * empty box on an old Android. "Icons look like emoji ... we must draw it so it looks the same
 * everywhere" (owner, 2026-10-11). So they are strokes now, the rule `SettingMarks` already
 * follows, and every number is a fraction of the mark's own box, so the box is the only size.
 */
@Composable
internal fun TextMark(
    fontSize: TextUnit,
    ink: Color,
    modifier: Modifier = Modifier,
    draw: DrawScope.(Color) -> Unit,
) {
    // As large as the words beside it, at every system font size: a mark fixed in points sat
    // beside text that had doubled and read as a speck.
    val side = with(LocalDensity.current) { fontSize.toDp() }
    // A `Canvas` has no semantics, so the mark says nothing. That is the contract: each one sits
    // beside words that say what it means, and a caller whose mark is the only carrier of a
    // meaning puts that meaning in words in its own semantics.
    Canvas(modifier = modifier.size(side)) { draw(ink) }
}

/** Right: two strokes, the short one first, the way a pen makes it. */
internal fun DrawScope.drawTick(ink: Color) {
    val w = size.minDimension
    val path = Path().apply {
        moveTo(w * TICK_FROM_X, w * TICK_FROM_Y)
        lineTo(w * TICK_KNEE_X, w * TICK_KNEE_Y)
        lineTo(w * TICK_TO_X, w * TICK_TO_Y)
    }
    drawPath(path, ink, style = Stroke(width = w * PEN, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Wrong: two strokes crossed, at the tick's own weight so the two read as a pair. */
internal fun DrawScope.drawCross(ink: Color) {
    val w = size.minDimension
    val near = w * CROSS_NEAR
    val far = w * CROSS_FAR
    drawLine(ink, Offset(near, near), Offset(far, far), w * PEN, StrokeCap.Round)
    drawLine(ink, Offset(far, near), Offset(near, far), w * PEN, StrokeCap.Round)
}

/** One line with a head at each end: two cards that are one statement, in no order. */
internal fun DrawScope.drawBothWays(ink: Color) {
    val w = size.minDimension
    val y = w * MIDDLE
    val pen = w * PEN
    val barb = w * BARB
    val left = w * BOTH_FROM
    val right = w * BOTH_TO
    drawLine(ink, Offset(left, y), Offset(right, y), pen, StrokeCap.Round)
    listOf(left to 1f, right to -1f).forEach { (tip, inward) ->
        drawLine(ink, Offset(tip, y), Offset(tip + inward * barb, y - barb), pen, StrokeCap.Round)
        drawLine(ink, Offset(tip, y), Offset(tip + inward * barb, y + barb), pen, StrokeCap.Round)
    }
}

/**
 * A circle that does not close, with a head where it stops: "another one". Clockwise, the way
 * the button that wears it spins.
 */
internal fun DrawScope.drawReroll(ink: Color) {
    val w = size.minDimension
    val r = w * TURN_RADIUS
    val centre = Offset(w * MIDDLE, w * MIDDLE)
    drawArc(
        color = ink,
        startAngle = TURN_FROM,
        sweepAngle = TURN_SWEEP,
        useCenter = false,
        topLeft = Offset(centre.x - r, centre.y - r),
        size = Size(r * 2, r * 2),
        style = Stroke(width = w * PEN, cap = StrokeCap.Round),
    )
    // The head sits across the stroke where it ends and points along it.
    val end = (TURN_FROM + TURN_SWEEP) * PI / HALF_TURN_DEGREES
    val outward = Offset(cos(end).toFloat(), sin(end).toFloat())
    val along = Offset(-outward.y, outward.x)
    val at = centre + outward * r
    val head = w * TURN_HEAD
    val path = Path().apply {
        val tip = at + along * head
        val outer = at + outward * (head * TURN_HEAD_SPREAD)
        val inner = at - outward * (head * TURN_HEAD_SPREAD)
        moveTo(tip.x, tip.y)
        lineTo(outer.x, outer.y)
        lineTo(inner.x, inner.y)
        close()
    }
    drawPath(path, ink)
}

/** A cup with two handles on a stem and a base: the call that stakes the round. */
internal fun DrawScope.drawTrophy(ink: Color) {
    val w = size.minDimension
    val left = w * CUP_LEFT
    val right = w * CUP_RIGHT
    val bowl = Path().apply {
        moveTo(left, w * CUP_RIM)
        lineTo(right, w * CUP_RIM)
        quadraticTo(right, w * CUP_WAIST, w * MIDDLE, w * CUP_FOOT)
        quadraticTo(left, w * CUP_WAIST, left, w * CUP_RIM)
        close()
    }
    drawPath(bowl, ink)

    val handle = w * HANDLE_RADIUS
    val pen = Stroke(width = w * TROPHY_PEN, cap = StrokeCap.Round)
    listOf(left to HANDLE_LEFT_FROM, right to HANDLE_RIGHT_FROM).forEach { (x, from) ->
        drawArc(
            color = ink,
            startAngle = from,
            sweepAngle = HANDLE_SWEEP,
            useCenter = false,
            topLeft = Offset(x - handle, w * HANDLE_Y - handle),
            size = Size(handle * 2, handle * 2),
            style = pen,
        )
    }

    drawLine(ink, Offset(w * MIDDLE, w * CUP_FOOT), Offset(w * MIDDLE, w * BASE_TOP), w * TROPHY_PEN)
    drawRoundRect(
        color = ink,
        topLeft = Offset(w * BASE_LEFT, w * BASE_TOP),
        size = Size(w * BASE_WIDE, w * BASE_DEEP),
        cornerRadius = CornerRadius(w * BASE_CORNER, w * BASE_CORNER),
    )
}

/** Rays from a point, long and short in turn: a firework, for a round the reader's side won. */
internal fun DrawScope.drawBurst(ink: Color) {
    val w = size.minDimension
    val centre = Offset(w * MIDDLE, w * MIDDLE)
    repeat(BURST_RAYS) { ray ->
        val angle = ray * 2 * PI / BURST_RAYS
        val way = Offset(cos(angle).toFloat(), sin(angle).toFloat())
        val reach = if (ray % 2 == 0) BURST_LONG else BURST_SHORT
        drawLine(ink, centre + way * (w * BURST_INNER), centre + way * (w * reach), w * PEN, StrokeCap.Round)
    }
    drawCircle(ink, radius = w * BURST_CORE, center = centre)
}

/** A face whose mouth turns down at both ends, for a round the reader's side lost. */
internal fun DrawScope.drawFrown(ink: Color) {
    val w = size.minDimension
    val pen = w * FACE_PEN
    drawCircle(ink, radius = w * FACE_RADIUS, center = Offset(w * MIDDLE, w * MIDDLE), style = Stroke(pen))
    listOf(-1f, 1f).forEach { side ->
        drawCircle(ink, radius = w * EYE_RADIUS, center = Offset(w * (MIDDLE + side * EYE_OUT), w * EYE_Y))
    }
    // The top of a small circle sat low in the face, so the mouth bends down at its two ends.
    val mouth = w * MOUTH_RADIUS
    drawArc(
        color = ink,
        startAngle = MOUTH_FROM,
        sweepAngle = MOUTH_SWEEP,
        useCenter = false,
        topLeft = Offset(w * MIDDLE - mouth, w * MOUTH_Y - mouth),
        size = Size(mouth * 2, mouth * 2),
        style = Stroke(width = pen, cap = StrokeCap.Round),
    )
}

/** The stroke every line mark here is drawn with: heavy enough to survive 11 points of text. */
private const val PEN = 0.14f
private const val MIDDLE = 0.5f

private const val TICK_FROM_X = 0.18f
private const val TICK_FROM_Y = 0.54f
private const val TICK_KNEE_X = 0.42f
private const val TICK_KNEE_Y = 0.78f
private const val TICK_TO_X = 0.84f
private const val TICK_TO_Y = 0.24f

private const val CROSS_NEAR = 0.24f
private const val CROSS_FAR = 0.76f

private const val BOTH_FROM = 0.12f
private const val BOTH_TO = 0.88f
private const val BARB = 0.22f

private const val TURN_RADIUS = 0.32f
private const val TURN_FROM = -45f
private const val TURN_SWEEP = 285f
private const val TURN_HEAD = 0.2f
private const val TURN_HEAD_SPREAD = 0.75f
private const val HALF_TURN_DEGREES = 180f

private const val CUP_LEFT = 0.28f
private const val CUP_RIGHT = 0.72f
private const val CUP_RIM = 0.14f
private const val CUP_WAIST = 0.5f
private const val CUP_FOOT = 0.58f
private const val HANDLE_RADIUS = 0.11f
private const val HANDLE_Y = 0.28f
private const val HANDLE_LEFT_FROM = 90f
private const val HANDLE_RIGHT_FROM = -90f
private const val HANDLE_SWEEP = 180f
private const val TROPHY_PEN = 0.09f
private const val BASE_LEFT = 0.3f
private const val BASE_TOP = 0.74f
private const val BASE_WIDE = 0.4f
private const val BASE_DEEP = 0.12f
private const val BASE_CORNER = 0.03f

private const val BURST_RAYS = 8
private const val BURST_INNER = 0.16f
private const val BURST_LONG = 0.46f
private const val BURST_SHORT = 0.34f
private const val BURST_CORE = 0.07f

private const val FACE_RADIUS = 0.42f
private const val FACE_PEN = 0.08f
private const val EYE_RADIUS = 0.06f
private const val EYE_OUT = 0.15f
private const val EYE_Y = 0.4f
private const val MOUTH_RADIUS = 0.2f
private const val MOUTH_Y = 0.86f
private const val MOUTH_FROM = 220f
private const val MOUTH_SWEEP = 100f

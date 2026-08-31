package game.vinto.app.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import game.vinto.app.LocalReducedMotion

/**
 * Waiting, drawn the way everything else on this table is drawn.
 *
 * Material's own indicator is a thin primary-coloured ring, and on felt it reads as a form
 * submitting. This is a chip's arc: a full track at low opacity with a brighter sweep riding
 * it, round-capped, in the ink of whatever it sits on. Two pieces and no images, which is the
 * same trick `GameButton` uses to look like an object.
 *
 * **Reduced motion is honoured, and honoured properly.** The rule this app follows is "no
 * movement, same information" — so the still version is not a frozen spinner, which says
 * nothing, but a complete ring at the sweep's own weight: visibly a busy indicator, visibly
 * not decoration, and it never moves. The animated branch is a separate composable so the
 * frame clock is not even started when nobody wants it running.
 *
 * @param description what a screen reader says. Waiting is information, and a spinner that
 *   announces nothing leaves a blind player on a screen that has simply stopped.
 */
@Composable
fun VintoSpinner(
    modifier: Modifier = Modifier,
    size: Dp = SpinnerSize,
    colour: Color = MaterialTheme.colorScheme.onFelt(),
    description: String? = null,
) {
    val marked = modifier
        .size(size)
        .semantics { description?.let { contentDescription = it } }

    if (LocalReducedMotion.current) {
        Canvas(marked) { ring(colour, sweepFrom = null) }
    } else {
        SpinningRing(marked, colour)
    }
}

@Composable
private fun SpinningRing(modifier: Modifier, colour: Color) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val start by transition.animateFloat(
        initialValue = StartAngle,
        targetValue = StartAngle + FullTurn,
        animationSpec = infiniteRepeatable(
            // Linear, and one turn a little over a second. Eased rotation reads as a
            // stutter at this diameter, and anything faster reads as an error.
            animation = tween(durationMillis = TurnMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )

    Canvas(modifier) { ring(colour, sweepFrom = start) }
}

/** The track, and — when something is turning — the brighter arc riding it. */
private fun DrawScope.ring(colour: Color, sweepFrom: Float?) {
    val stroke = Stroke(width = size.minDimension * StrokeFraction, cap = StrokeCap.Round)
    val inset = stroke.width / 2
    val box = Size(size.width - stroke.width, size.height - stroke.width)
    val corner = Offset(inset, inset)

    drawArc(
        color = colour.copy(alpha = TrackAlpha),
        startAngle = 0f,
        sweepAngle = FullTurn,
        useCenter = false,
        topLeft = corner,
        size = box,
        style = stroke,
    )

    drawArc(
        color = colour,
        startAngle = sweepFrom ?: StartAngle,
        // Still: the whole ring at full weight, which is a state rather than a frozen motion.
        sweepAngle = if (sweepFrom == null) FullTurn else SweepAngle,
        useCenter = false,
        topLeft = corner,
        size = box,
        style = stroke,
    )
}

/**
 * A spinner with a sentence beside it, for the waits that need saying out loud.
 *
 * "Connecting", "Looking for rooms" — a bare spinner asks the player to guess what is slow,
 * and the guess is usually "this app is broken".
 */
@Composable
fun BusyLine(
    label: String,
    modifier: Modifier = Modifier,
    colour: Color = MaterialTheme.colorScheme.onFelt(),
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Gap),
    ) {
        VintoSpinner(size = InlineSize, colour = colour, description = label)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colour)
    }
}

/** Big enough to read as an object on the felt, small enough to sit inside a button. */
private val SpinnerSize = 22.dp

/** Beside a line of body text, matched to its cap height rather than to its box. */
val InlineSize = 16.dp

/** On a seat plate, where it stands in for the thing the seat is about to become. */
val SeatSize = 18.dp

private val Gap = 8.dp
private const val StrokeFraction = 0.12f
private const val TrackAlpha = 0.22f
private const val StartAngle = -90f
private const val SweepAngle = 250f
private const val FullTurn = 360f
private const val TurnMs = 1100

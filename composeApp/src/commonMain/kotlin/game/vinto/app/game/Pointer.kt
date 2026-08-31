package game.vinto.app.game

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import game.vinto.app.keyOf
import game.vinto.client.Target
import kotlin.math.roundToInt

/**
 * The coach's hand.
 *
 * A card game is taught at a table by somebody putting a finger on a card, and everything a
 * lesson has to say about *which* one is a sentence a pointing hand says better. So there is
 * exactly one on screen at a time, it points from just outside whatever it is naming, and the
 * lesson takes it away the moment the player acts — it was a suggestion, and the table belongs
 * to them.
 *
 * **White, deliberately.** Every other colour here already means something: green is whose
 * turn it is, gold is the caller and what you have chosen, red is a penalty, blue is the
 * coalition, and the breathing ring means "this can be touched". A coach that borrowed any of
 * them would be adding to the confusion it exists to remove.
 *
 * Drawn rather than written: an emoji hand is a different shape, size and colour on every
 * platform, and this one has to sit at a known distance from the edge of a card.
 */
@Composable
fun Pointer(stage: Stage, target: Target?) {
    val rect = target?.let { stage.boundsOf(it.key()) } ?: return
    val density = LocalDensity.current
    val reach = with(density) { Reach.toPx() }
    val hand = with(density) { Hand.toPx() }
    val bobBy = with(density) { Bob.toPx() }

    // A hand touching the thing it is pointing at reads as part of it, and covers whatever is
    // written under a card — "DRAW", "DISCARD", a name.
    val clearance = with(density) { Clearance.toPx() }

    // A wide *button* is pointed at from inside its own left end, because the rail's buttons
    // are stacked and an arrow below one sits on top of the next. Everything else keeps the
    // arrow outside it: the box of recent moves is also full width, and an arrow inside that
    // one lands squarely on the words it is telling the player to read.
    val wide = target is Target.Button && rect.width > stage.size.width * WIDE

    // From above, pointing down, unless there is no room up there. Below looks equally good
    // until the target is a rank chip in a grid, where the hand then sits squarely on the chip
    // in the next row down and names the wrong card.
    val above = rect.top - reach > 0f

    val bob by rememberInfiniteTransition(label = "point").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BobMs), RepeatMode.Reverse),
        label = "bob",
    )

    Box(
        modifier = Modifier
            // Above everything. Cards and plates are `Surface`es with a shadow, and elevation
            // beats sibling order in Compose — without this the hand is drawn *under* the very
            // card it is pointing at, which is a hard thing to notice in a screenshot and an
            // easy one to notice on a phone.
            .zIndex(PointerZ)
            .offset {
                val travel = bob * bobBy
                when {
                    wide -> IntOffset(
                        x = (rect.left + clearance + travel).roundToInt(),
                        y = (rect.center.y - hand / 2f).roundToInt(),
                    )

                    above -> IntOffset(
                        x = (rect.center.x - hand / 2f).roundToInt(),
                        y = (rect.top - hand - clearance - travel).roundToInt(),
                    )

                    else -> IntOffset(
                        x = (rect.center.x - hand / 2f).roundToInt(),
                        y = (rect.bottom + clearance + travel).roundToInt(),
                    )
                }
            }
            .size(Hand),
    ) {
        Canvas(modifier = Modifier.size(Hand)) {
            when {
                wide -> rotate(QUARTER_TURN) { arrow() }
                above -> rotate(HALF_TURN) { arrow() }
                else -> arrow()
            }
        }
    }
}

/**
 * A blunt, chunky arrow.
 *
 * Not a cursor: it is being looked at across a phone, past a table full of cards, by somebody
 * who has never seen this screen before. Big head, short tail, and its own shadow so it
 * survives being drawn over both the felt and the rail.
 */
private fun DrawScope.arrow() {
    val w = size.width
    val h = size.height
    val head = h * HEAD

    val shape = Path().apply {
        moveTo(w / 2f, 0f)
        lineTo(w, head)
        lineTo(w * TAIL_OUT, head)
        lineTo(w * TAIL_OUT, h)
        lineTo(w * TAIL_IN, h)
        lineTo(w * TAIL_IN, head)
        lineTo(0f, head)
        close()
    }

    translate(ShadowDrop, ShadowDrop) { drawPath(shape, Shadow) }
    drawPath(shape, Color.White)

    // A white hand over a white card is not a hand. The outline is what makes it read on the
    // felt, on the rail and on a card face — the three things it is ever drawn over.
    drawPath(shape, Outline, style = Stroke(width = OutlineWidth))
}

/** The name the stage files this target under; see `Stage.mark`. */
fun Target.key(): String = when (this) {
    is Target.Place -> anchor.key()
    is Target.Seat -> "seat:$playerId"
    // Through `keyOf`, exactly as `ChoiceButton` marks it. These two are the two halves of
    // one lookup, and when they disagree the arrow points at nothing and says nothing —
    // which is how the "two ways to start a turn" beat went missing for months.
    is Target.Button -> "choice:${keyOf(label)}"
    is Target.Chip -> "rank:${rank.serialName}"
    is Target.Furniture -> id
}

private val Hand = 24.dp

/** How much room the hand needs above a target before it will point down at it. */
private val Reach = 34.dp
private val Bob = 4.dp
private val Clearance = 5.dp
private const val PointerZ = 100f

private const val BobMs = 620
private const val HALF_TURN = 180f
private const val QUARTER_TURN = 90f

/** Wider than this much of the screen and it is a rail button rather than a card. */
private const val WIDE = 0.5f
private const val HEAD = 0.55f
private const val TAIL_OUT = 0.68f
private const val TAIL_IN = 0.32f
private const val ShadowDrop = 2f
private val Shadow = Color(0x66000000)

/** The rail's own colour: dark enough to hold an edge against a white card. */
private val Outline = Color(0xFF1B2430)
private const val OutlineWidth = 4f

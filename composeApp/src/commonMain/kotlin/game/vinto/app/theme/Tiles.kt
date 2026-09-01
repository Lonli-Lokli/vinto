package game.vinto.app.theme

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A way in, with room to say what it is.
 *
 * A [GameButton] is a *move*: one stamped word, taken in at a glance, sized for a rail where
 * fourteen of them sit side by side. A choice between three destinations is a different thing
 * — it is made once, deliberately, and each option has a sentence's worth of consequence that
 * decides it. Four identical full-width slabs stacked down a screen is what happens when a
 * game borrows a form's controls for that job, and it is what the online screen looked like:
 * "join", "open", "browse" and "back" the same size and shape, distinguished by hue alone.
 *
 * Lit like a button and not like a slot, because a tile is something you push. Bigger, with
 * the name in the app's stamped face and the consequence underneath in plain words — the pair
 * is the point, since the sentence is what makes the choice without a tap to find out.
 *
 * [accent] carries meaning the same way [ButtonTone] does: the tile that always works is the
 * one with colour on it, and the others are the rail's own charcoal.
 */
@Composable
fun ActionTile(
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    /**
     * Whether there is anything to ask for yet.
     *
     * A tile that is not ready still takes the press and still calls [onClick] — because the
     * press is how the player finds out *why*, and the caller is the only thing that knows.
     * It was `Surface(enabled = false)` for one build, which swallowed the tap: reported as
     * "buttons look enabled and nothing is shown if I press", which is exactly what a
     * disabled control that does not look disabled is. Dimming it harder was the other way
     * out and the worse one — a form whose only feedback is that a control looks slightly
     * greyer leaves the player to guess which of the fields above it is at fault.
     */
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lift by animateDpAsState(if (pressed) 0.dp else Lift, label = "tile")
    val shape = RoundedCornerShape(Corner)
    val feedback = LocalFeedback.current

    Surface(
        onClick = {
            feedback.commit()
            onClick()
        },
        // Spoken as the title alone. A screen reader reading title *and* sentence for each of
        // three tiles is a paragraph before the first choice; the sentence is there for the
        // eye, and a reader can reach it as the tile's own text.
        modifier = modifier.fillMaxWidth().heightIn(min = MinHeight).semantics {
            contentDescription = title
        },
        shape = shape,
        color = Color.Transparent,
        // The tile's charcoal is fixed in both schemes, so its ink is too — `Rail.ink` here
        // was the light scheme's near-black on that charcoal, at 1.4:1. Same inks the seat
        // plates use, for the same reason.
        contentColor = Slate.ink,
        border = BorderStroke(Edge, (accent ?: Rail.edge).copy(alpha = if (enabled) 1f else Dimmed)),
        // The lift is the whole affordance, so losing it says "not now" before any colour does.
        shadowElevation = if (enabled) lift else 0.dp,
        interactionSource = interaction,
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        if (pressed) listOf(TileLow, TileHigh) else listOf(TileHigh, TileLow),
                    ),
                ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = PadH, vertical = PadV),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Gap),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(TitleGap),
                ) {
                    Text(
                        text = title.uppercase(),
                        style = stamped(size = TitleSize),
                        color = accent ?: Slate.ink,
                    )
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate.inkDim,
                    )
                }
                Chevron(colour = accent ?: Slate.inkDim)
            }
        }
    }
}

/**
 * The way back, out of the thumb's way.
 *
 * It was a full-width slab at the bottom of every screen, which spends the most valuable
 * region on a phone — the bottom arc, where a thumb rests — on a control that duplicates a
 * system gesture. Up here it costs a corner.
 */
@Composable
fun BackChevron(description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val feedback = LocalFeedback.current

    Surface(
        onClick = {
            feedback.touch()
            onClick()
        },
        modifier = modifier.size(MinTap).semantics { contentDescription = description },
        shape = RoundedCornerShape(Corner),
        color = Color.Transparent,
        // It sits straight on the felt, and the felt is green in both schemes — the light
        // scheme's `Rail.ink` was a near-black chevron on green cloth.
        contentColor = MaterialTheme.colorScheme.onFelt(),
        interactionSource = interaction,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "‹",
                fontSize = BackSize.sp,
                color = MaterialTheme.colorScheme.onFelt(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A tile points somewhere, and this is the arrow saying so. */
@Composable
private fun Chevron(colour: Color) {
    Text(text = "›", fontSize = ChevronSize.sp, color = colour)
}

/**
 * The tile's own charcoal.
 *
 * Fixed rather than a theme role, for the same reason [ButtonTone]'s colours are: these sit on
 * felt in both schemes, and a tile that followed the system theme would be a white card on a
 * green table every morning.
 */
private val TileHigh = Color(0xFF3B4038)
private val TileLow = Color(0xFF272B24)

private val Corner = 12.dp
private val Edge = 1.dp
private val Lift = 3.dp
private val MinHeight = 84.dp
private val MinTap = 48.dp
private val PadH = 18.dp
private val PadV = 16.dp
private val Gap = 12.dp
private val TitleGap = 5.dp

/** How far a tile that has nothing to ask for yet stands back. */
private const val Dimmed = 0.4f

private const val TitleSize = 16
private const val ChevronSize = 26
private const val BackSize = 30

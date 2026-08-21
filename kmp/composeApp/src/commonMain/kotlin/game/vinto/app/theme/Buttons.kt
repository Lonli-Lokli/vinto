package game.vinto.app.theme

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A button with a top to it.
 *
 * Material's own is a stadium — fully rounded, flat, tonal — and on a card table it reads as a
 * settings screen. Premium poker apps have converged somewhere else: a charcoal ground with
 * depth built from bevel and shadow rather than from texture, and the colour kept for the few
 * things that carry meaning, so the cards stay the brightest thing on the screen.
 *
 * The depth here is three cheap pieces and no images: a vertical gradient from a lighter tint
 * at the top to a darker one at the bottom, a hairline of the lighter tint around the edge,
 * and a shadow underneath that collapses on press. Pressed, the gradient inverts — the light
 * moves to the bottom — which is what a real button does when it goes down.
 */
@Composable
fun GameButton(
    label: String,
    tone: ButtonTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: String? = null,
    compact: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lift by animateDpAsState(if (pressed) 0.dp else Lift, label = "lift")
    val shape = RoundedCornerShape(Corner)
    val feedback = LocalFeedback.current

    Surface(
        onClick = {
            feedback.commit()
            onClick()
        },
        modifier = modifier.heightIn(min = if (compact) CompactTap else MinTap),
        shape = shape,
        color = Color.Transparent,
        contentColor = tone.ink,
        border = BorderStroke(Hairline, tone.rim),
        shadowElevation = lift,
        interactionSource = interaction,
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        if (pressed) listOf(tone.low, tone.high) else listOf(tone.high, tone.low),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = if (compact) CompactPadH else PadH,
                    vertical = if (compact) CompactPadV else PadV,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Gap),
            ) {
                leading?.let { Text(it, fontSize = LabelSize) }
                Text(
                    text = label,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) CompactLabel else LabelSize,
                    letterSpacing = Tracking,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * The five kinds of move, and what each looks like.
 *
 * Fixed colours rather than theme roles: the rail these sit on is the same charcoal in light
 * and dark, so a button that followed the colour scheme would change meaning with the system
 * theme — and the meaning is the point. What each colour *means* is ported from the web app's
 * `BUTTON_ACTION_VARIANTS`, whose own comment gives the reason: muscle memory.
 *
 * Each is three values, which is what makes the button look like an object: [high] is the lit
 * top, [low] the shaded bottom, [rim] the edge catching the light.
 */
enum class ButtonTone(val high: Color, val low: Color, val rim: Color, val ink: Color) {
    /** Green — draw, play the action, continue. */
    PLAY(Color(0xFF198845), Color(0xFF0F6B34), Color(0xFF3FD07A), Color.White),

    /** Blue — take a card into a hand, start the round. */
    KEEP(Color(0xFF2F6FE0), Color(0xFF1B4CAE), Color(0xFF5A94F0), Color.White),

    /** Charcoal — decline, discard, go back. The default, and the quietest. */
    NEUTRAL(Color(0xFF39434F), Color(0xFF262E38), Color(0xFF525E6C), Color(0xFFDDE4EB)),

    /** Gold — the move that ends the round for everybody. */
    STAKES(Color(0xFFE0A32A), Color(0xFFB37810), Color(0xFFF3C860), Color(0xFF201603)),

    /** Amber, quieter than gold — naming a rank, of which there are fourteen at once. */
    DECLARE(Color(0xFF6B5A2E), Color(0xFF4A3D1C), Color(0xFF9A8347), Color(0xFFF6E6BC)),

    /**
     * Red — the one button that destroys something.
     *
     * Nothing in a round is this colour, and that is the point: red never appears while a game
     * is being played, so when it does appear the hand slows down.
     */
    DANGER(Color(0xFFB3382F), Color(0xFF7E231C), Color(0xFFD9635A), Color.White),
}

private val Corner = 10.dp
private val Hairline = 1.dp
private val Lift = 3.dp
private val MinTap = 50.dp
private val CompactTap = 44.dp
private val PadH = 16.dp
private val PadV = 12.dp
private val CompactPadH = 8.dp
private val CompactPadV = 8.dp
private val Gap = 8.dp
private val LabelSize = 15.sp
private val CompactLabel = 14.sp
private val Tracking = 0.3.sp

package game.vinto.app.theme

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A button that belongs on a card table rather than in a settings screen.
 *
 * Material's own button is a stadium — fully rounded, tonal, restrained — and it is the single
 * thing that made this screen read as an Android app rather than a game. The web app's buttons
 * are four-pixel corners, a solid colour and a small shadow, which is what a physical control
 * looks like, and that is what this is.
 *
 * The colours are [ButtonTone], ported from the web's `BUTTON_ACTION_VARIANTS`. What they mean
 * is fixed across the game so that a player learns them once: green gets on with the turn,
 * blue puts a card into a hand, slate declines, orange ends the round, amber names a rank.
 */
@Composable
fun GameButton(
    label: String,
    tone: ButtonTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinTap),
        shape = RoundedCornerShape(Corner),
        color = if (pressed) tone.pressed else tone.fill,
        contentColor = tone.ink,
        border = BorderStroke(Hairline, tone.edge),
        shadowElevation = if (pressed) 0.dp else Lift,
        interactionSource = interaction,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier.padding(horizontal = PadH, vertical = PadV),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Gap),
            ) {
                leading?.let { Text(it, fontSize = IconSize) }
                Text(
                    text = label,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = LabelSize,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * The five kinds of move, and what each looks like.
 *
 * Fixed colours rather than theme roles: the panel these sit on is the same dark rail in both
 * light and dark, so a button that followed the colour scheme would change meaning with the
 * system theme — and the meaning is the point.
 */
enum class ButtonTone(val fill: Color, val pressed: Color, val edge: Color, val ink: Color) {
    /** Green — draw, play the action, continue. */
    PLAY(Color(0xFF16A34A), Color(0xFF15803D), Color(0xFF15803D), Color.White),

    /** Blue — take a card into a hand, start the round. */
    KEEP(Color(0xFF2563EB), Color(0xFF1D4ED8), Color(0xFF1D4ED8), Color.White),

    /** Slate — decline, discard, go back. */
    NEUTRAL(Color(0xFF3F4C5A), Color(0xFF323D49), Color(0xFF55636F), Color(0xFFE7ECF1)),

    /** Orange — the move that ends the round for everybody. */
    STAKES(Color(0xFFEA8C0B), Color(0xFFC2740A), Color(0xFFC2740A), Color(0xFF1A1204)),

    /** Amber — naming a rank, and taking the consequences. */
    DECLARE(Color(0xFFD9A21B), Color(0xFFB98816), Color(0xFFB98816), Color(0xFF1A1404)),
}

private val Corner = 5.dp
private val Hairline = 1.dp
private val Lift = 2.dp
private val MinTap = 46.dp
private val PadH = 14.dp
private val PadV = 10.dp
private val Gap = 6.dp
private val LabelSize = 15.sp
private val IconSize = 15.sp

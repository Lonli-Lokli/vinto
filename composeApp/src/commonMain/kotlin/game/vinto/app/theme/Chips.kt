package game.vinto.app.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * One of a small set of answers, all of them visible: a bezel selector.
 *
 * A row of chips was the Material answer and looked it — three outlined rectangles, one of
 * them filled. This is the control the same choice has on a physical thing: a **recessed
 * track** cut into the panel, with the chosen answer carried on a **raised thumb** that
 * slides between the positions. The track is lit from the top like a groove (dark at the top
 * edge, light at the bottom); the thumb is lit like everything else that stands proud of the
 * panel — the exact inverse, which is what makes one read as cut *into* the surface and the
 * other as sitting *on* it.
 *
 * The slide is the point of the animation: a thumb that jumps has been redrawn, a thumb that
 * travels has been *moved*, and the difference is most of what "machined" means on a screen.
 *
 * It replaces the app's last stock Material control as well — haptics on and off is two
 * positions of the same track rather than a Switch.
 */
@Composable
fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    /**
     * Composable, because a label is a *resource* — the words for "Moderate" and "Calm" live
     * in `strings.xml` where a translator can reach them.
     */
    label: @Composable (T) -> String,
    onChoose: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val index = options.indexOf(selected).coerceAtLeast(0)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cell = maxWidth / options.size
        val travel by animateDpAsState(
            targetValue = cell * index,
            animationSpec = tween(SlideMs, easing = FastOutSlowInEasing),
            label = "thumb",
        )

        // The groove.
        Surface(
            modifier = Modifier.fillMaxWidth().height(Track),
            shape = RoundedCornerShape(Corner),
            color = Rail.line,
            border = BorderStroke(1.dp, Rail.edge),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = CUT), Color.Transparent),
                        ),
                    ),
            )
        }

        // The thumb, carrying the answer.
        Box(
            modifier = Modifier
                .offset(x = travel)
                .width(cell)
                .height(Track)
                .padding(Seat)
                .clip(RoundedCornerShape(Corner - Seat))
                .background(Brush.verticalGradient(listOf(Rail.ink, Rail.ink.copy(alpha = FACE)))),
        )

        Row(modifier = Modifier.fillMaxWidth().height(Track), horizontalArrangement = Arrangement.Start) {
            options.forEach { option ->
                val chosen = option == selected
                Box(
                    modifier = Modifier
                        .width(cell)
                        .height(Track)
                        .selectable(selected = chosen, onClick = { onChoose(option) }),
                    contentAlignment = Alignment.Center,
                ) {
                    val name = label(option)
                    Text(
                        text = name.uppercase(),
                        // Stamped like every other control, and spoken as written.
                        modifier = Modifier.semantics { contentDescription = name },
                        style = stamped(size = LabelSize),
                        fontWeight = if (chosen) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (chosen) Rail.fill else Rail.inkDim,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Every position is a target for a thumb, whatever the word in it is. */
private val Track = 46.dp
private val Corner = 7.dp

/** How far the thumb sits inside the groove it runs in. */
private val Seat = 3.dp

private const val CUT = 0.22f
private const val FACE = 0.86f
private const val SlideMs = 190
private const val LabelSize = 14

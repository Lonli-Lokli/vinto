package game.vinto.app.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One of a small set of answers, all of them visible.
 *
 * A row of chips rather than a dropdown or a dialog: three difficulties or three speeds are
 * few enough to show, and a control that hides the alternatives behind a tap is a control that
 * has to be explored before it can be used. The selected one is filled rather than merely
 * outlined, so which is chosen survives being glanced at.
 */
@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = ChipTap).semantics { this.selected = selected },
        shape = RoundedCornerShape(ChipCorner),
        color = if (selected) RailBorder else Color.Transparent,
        contentColor = if (selected) RailInk else RailInkDim,
        border = BorderStroke(1.dp, RailBorder),
    ) {
        Box(modifier = Modifier.padding(vertical = ChipPadV), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = ChipText,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

/** A whole row of them, sharing the width evenly. */
@Composable
fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    /**
     * Composable, because a label is a *resource* now — the words for "Moderate" and "Calm"
     * live in `strings.xml` where a translator can reach them, and reading one is a
     * composition-scoped lookup rather than a property on an enum.
     */
    label: @Composable (T) -> String,
    onChoose: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ChipGap),
    ) {
        options.forEach { option ->
            ChoiceChip(
                label = label(option),
                selected = option == selected,
                onClick = { onChoose(option) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private val ChipCorner = 5.dp
private val ChipGap = 6.dp
private val ChipPadV = 10.dp
private val ChipText = 15.sp

/** Every chip is a target for a thumb, whatever the text in it is. */
private val ChipTap = 44.dp

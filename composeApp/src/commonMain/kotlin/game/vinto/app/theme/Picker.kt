package game.vinto.app.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A drop-down: one closed row showing the current answer, and a list that opens over the screen.
 *
 * This exists because of the language list. A choice of two or three is a [ChoiceRow] — the
 * options are worth showing all at once and a tap is one tap. Twenty-one is not: as a grid it
 * was ten rows of chips, taller than the phone, and it pushed every setting below it off the
 * screen to make room for nineteen answers nobody was looking for.
 *
 * **It opens over the screen rather than expanding in place**, which is the whole reason it is
 * two composables rather than one. Settings is a scrolling column; a list that expanded inside
 * it would put a scroll inside a scroll, and a thumb that meant to move the list would move the
 * page instead. So [PickerField] is the closed control that lives in the column, and
 * [PickerSheet] is composed as a sibling of that column at the screen's root, where it can
 * cover. Callers hold the open flag between them.
 *
 * Not Material's `ExposedDropdownMenuBox`, and not an anchored `Popup`. The first is a stock
 * control and this app draws its own; the second positions itself against an anchor and has to
 * decide what to do when twenty-one rows do not fit below it on a phone — which is to become a
 * sheet, badly. Going straight to the sheet is the same answer without the arithmetic.
 */
@Composable
fun PickerField(
    /**
     * Above the field, or absent. Absent is right inside a `Setting` panel, which already
     * carries the title — a groove labelled LANGUAGE under a panel headed Language is the
     * same word twice and reads as a mistake.
     */
    label: String?,
    value: String,
    description: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val feedback = LocalFeedback.current
    val shape = RoundedCornerShape(Corner)

    Column(modifier = modifier.fillMaxWidth()) {
        label?.let {
            Text(
                text = it.uppercase(),
                style = stamped(size = LabelSize),
                color = Rail.inkDim,
                modifier = Modifier.padding(bottom = LabelGap),
            )
        }
        Surface(
            onClick = {
                feedback.touch()
                onOpen()
            },
            // Spoken as one thing — "Language: Русский" — rather than as a label and a value a
            // reader has to put together. The chevron has no name of its own for the same
            // reason: it is a picture of what the row already says it does.
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinTap)
                .semantics { contentDescription = description },
            shape = shape,
            color = Color.Transparent,
            contentColor = Rail.ink,
            border = BorderStroke(Edge, Rail.line),
            interactionSource = interaction,
        ) {
            Box(
                modifier = Modifier
                    .clip(shape)
                    // The same cut-into-the-panel gradient a text field wears, because this is
                    // a field: it holds an answer and it is where you go to change it.
                    .background(Brush.verticalGradient(listOf(Rail.fill.darken(), Rail.fill))),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = PadH, vertical = PadV),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Gap),
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Rail.ink,
                        modifier = Modifier.weight(1f),
                    )
                    // The chevron the tiles already use, turned a quarter. A down-pointing
                    // glyph of its own (⌄) would be a character this app has never asked its
                    // font for, and `FontCoverageTest` only reads `strings.xml` — so a
                    // missing one would ship as tofu with nothing to catch it.
                    Text(
                        text = "›",
                        fontSize = ChevronSize.sp,
                        color = Rail.edge,
                        modifier = Modifier.rotate(Quarter),
                    )
                }
            }
        }
    }
}

/**
 * The opened list. Bounded and scrolling, because the whole point is that it is longer than a
 * screen; the bound leaves the felt visible above it so it reads as something over the page
 * rather than a new one.
 */
@Composable
fun PickerSheet(
    open: Boolean,
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    VintoSheet(open = open, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = SheetMax)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PadH)
                .padding(bottom = PadH),
            verticalArrangement = Arrangement.spacedBy(RowGap),
        ) {
            Text(
                text = title.uppercase(),
                style = stamped(size = TitleSize),
                color = Rail.inkDim,
                modifier = Modifier.padding(vertical = LabelGap).semantics { heading() },
            )
            content()
        }
    }
}

/**
 * One answer in the list.
 *
 * The chosen one is marked with a drawn dot and the brand colour rather than a tick character,
 * for the reason in the chevron's note above: a glyph is a font dependency nothing here checks.
 * The dot's space is reserved on every row, so choosing does not shuffle the list sideways.
 */
@Composable
fun PickerRow(
    label: String,
    chosen: Boolean,
    description: String,
    onChoose: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val feedback = LocalFeedback.current
    val shape = RoundedCornerShape(Corner)
    // Read here rather than inside the Canvas: `Rail.brand` is a composable getter and a draw
    // scope is not a composition, so the colour has to be captured on the way in.
    val mark = Rail.brand

    Surface(
        onClick = {
            feedback.commit()
            onChoose()
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTap)
            .semantics { contentDescription = description },
        shape = shape,
        color = Color.Transparent,
        contentColor = Rail.ink,
        border = BorderStroke(Edge, if (chosen) mark else Rail.line),
        interactionSource = interaction,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PadH, vertical = PadV),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Gap),
        ) {
            Box(modifier = Modifier.size(Dot), contentAlignment = Alignment.Center) {
                if (chosen) {
                    Canvas(modifier = Modifier.size(Dot)) {
                        drawCircle(color = mark, radius = size.minDimension / 2f)
                    }
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (chosen) mark else Rail.ink,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The top of the groove, a shade under the panel it is cut into. */
private fun Color.darken(): Color = copy(
    red = red * Cut,
    green = green * Cut,
    blue = blue * Cut,
)

private const val Cut = 0.72f

/** A right-pointing chevron, turned to point down. */
private const val Quarter = 90f

private val Corner = 8.dp
private val Edge = 1.dp
private val MinTap = 48.dp
private val PadH = 14.dp
private val PadV = 12.dp
private val Gap = 12.dp
private val LabelGap = 6.dp
private val RowGap = 6.dp
private val Dot = 10.dp

/**
 * How tall the list may get. Short enough that the felt shows above it on the smallest phone
 * this app is tested at, which is what makes it read as a panel over the screen.
 */
private val SheetMax = 420.dp

private const val LabelSize = 12
private const val TitleSize = 13
private const val ChevronSize = 26

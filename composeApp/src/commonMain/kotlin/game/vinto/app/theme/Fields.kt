package game.vinto.app.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A field cut into the panel.
 *
 * The app's controls are one of two things and the difference carries meaning. A [GameButton]
 * is **raised** — lit at the top, shadowed underneath, and the shadow collapses when you press
 * it. A [ChoiceRow]'s track is **cut** — dark at the top edge where a groove would catch no
 * light, lighter at the bottom. A place to type belongs to the second family: it is a slot you
 * put something into, not an object you push.
 *
 * That is the whole reason this exists rather than `OutlinedTextField`. Material's is a
 * stadium outline with a label that animates up into a floating caption, and that one gesture
 * is the most recognisably "Android form" thing a screen can do — it reads as an address
 * entry, on a table that is trying to read as felt and chips. The label here sits **above**,
 * still, at rest, where a plaque would be.
 *
 * Focus is the gold rim, which is the same gold that marks the row that decided a round: this
 * app already uses it to mean *this is the one being talked about*.
 */
@Composable
fun VintoField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    placeholder: String? = null,
    capitalise: KeyboardCapitalization = KeyboardCapitalization.None,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val rim by animateColorAsState(if (focused) Rail.gold else Rail.edge, label = "rim")

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(LabelGap)) {
        Text(
            text = label,
            style = stamped(size = LabelSize),
            color = Rail.inkDim,
        )
        // The groove is drawn *inside* the field's own decoration rather than around it, and
        // that is a tap-target decision rather than a layout one. Wrapped the other way the
        // slot is 48dp tall and the thing that actually takes a tap is the text strip inside
        // it — measured at 25dp, so two thirds of what looks like a field does nothing when
        // a thumb lands on it. `LobbyReachTest` found this on its first run.
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = bodyStyle(Rail.ink),
            cursorBrush = SolidColor(Rail.gold),
            interactionSource = interaction,
            keyboardOptions = KeyboardOptions(capitalization = capitalise),
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTap),
            decorationBox = { field ->
                Slot(rim = rim, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier.padding(horizontal = PadH, vertical = PadV),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        // Under the text rather than a decoration on the field, so it goes the
                        // moment there is anything real to read.
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, style = bodyStyle(Rail.inkDim), maxLines = 1)
                        }
                        field()
                    }
                }
            },
        )
        detail?.let {
            Text(it, style = bodyStyle(Rail.inkDim, size = DetailSize))
        }
    }
}

/**
 * Six characters, six slots.
 *
 * A room code is the one string in this app somebody reads aloud down a telephone, or pastes
 * out of an invitation. One long text box serves neither: it gives no sense of how many
 * characters are wanted, no place to keep your finger while reading them out, and no signal
 * that you have arrived at the end.
 *
 * Three behaviours the single box did not have, each for a way people actually arrive at a code:
 *
 * - **Pasting a whole invitation works.** [onValueChange] is fed through the same
 *   `roomCodeFrom` the deep-link path uses, upstream of here, so a pasted `https://…/r/ABC123`
 *   fills the slots. Somebody who was sent a link and copied the lot should not have to edit it.
 * - **Only real characters are accepted.** The alphabet excludes the letters and digits that
 *   look alike, which is exactly why it does — a code read aloud is where an O becomes a 0.
 * - **Full means full.** The sixth character does not scroll the box; it completes it, and the
 *   button below lights up. That is the signal a text field cannot give.
 *
 * One `BasicTextField` behind six drawn cells rather than six fields: six real fields means
 * six focus states, six keyboards, and a backspace that has to be taught where to go. The
 * platform already gets all of that right for one.
 */
@Composable
fun CodeField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    length: Int = CodeLength,
    detail: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(LabelGap)) {
        Text(text = label, style = stamped(size = LabelSize), color = Rail.inkDim)

        Box {
            // The real field, invisible and full-size: it owns focus, the keyboard, the
            // selection and the caret, and the cells below are a picture of its contents.
            // Drawing the cells *instead* of a field would mean re-implementing all of that.
            BasicTextField(
                value = value,
                onValueChange = { onValueChange(it.uppercase().filter(::isCodeCharacter).take(length)) },
                singleLine = true,
                interactionSource = interaction,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                textStyle = TextStyle(color = Color.Transparent, fontSize = CellSize.sp),
                cursorBrush = SolidColor(Color.Transparent),
                modifier = Modifier.fillMaxWidth().height(CellHeight),
                decorationBox = { field ->
                    // `field` is invoked so the platform still measures and places the real
                    // input; it draws nothing, because every colour in its style is transparent.
                    field()
                    Row(
                        modifier = Modifier.fillMaxWidth().semantics {
                            contentDescription = label
                        },
                        horizontalArrangement = Arrangement.spacedBy(CellGap),
                    ) {
                        repeat(length) { index ->
                            CodeCell(
                                character = value.getOrNull(index),
                                // The cell the next character will land in, lit like a caret.
                                // Only while the field has focus: a lit cell on a screen nobody
                                // is typing into is an animation, not an instruction.
                                waiting = focused && index == value.length,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                },
            )
        }

        detail?.let { Text(it, style = bodyStyle(Rail.inkDim, size = DetailSize)) }
    }
}

/**
 * What a person *types*, as opposed to what the app stamps at them.
 *
 * Body rather than [stamped]: a nickname is somebody's own word and a caps-and-letterspaced
 * rendering of it would be the app shouting a name back at the person who just wrote it. The
 * labels above the fields are stamped, because those are the app's own words.
 */
@Composable
private fun bodyStyle(colour: Color, size: Int = BodySize) =
    MaterialTheme.typography.bodyLarge.copy(color = colour, fontSize = size.sp)

/** One slot of a [CodeField], with its character or the space where one goes. */
@Composable
private fun CodeCell(character: Char?, waiting: Boolean, modifier: Modifier = Modifier) {
    val rim by animateColorAsState(if (waiting) Rail.gold else Rail.edge, label = "cell")

    Slot(rim = rim, modifier = modifier.height(CellHeight)) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = character?.toString() ?: " ",
                style = stamped(size = CellSize),
                color = Rail.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The groove everything typeable sits in.
 *
 * Lit the exact inverse of [GameButton]: dark at the top edge, transparent below, so the eye
 * reads a shadow cast *into* the surface. Shared by [VintoField] and every cell of a
 * [CodeField] so the two cannot drift apart, which is the failure a second copy of eight lines
 * always eventually produces.
 */
@Composable
private fun Slot(rim: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(Corner)
    Surface(
        modifier = modifier.heightIn(min = MinTap),
        shape = shape,
        color = Rail.line,
        border = BorderStroke(Edge, rim),
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = Cut), Color.Transparent)),
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            content()
        }
    }
}

/**
 * The characters a room code can be made of.
 *
 * Declared here as well as in the room's `CODE_ALPHABET` on purpose, and the duplication is
 * safe in one direction only: this is a *filter on typing*, so a character the registry could
 * never have issued is refused before it can be typed. If the two ever disagree the cost is a
 * code that cannot be entered, which somebody will report — not a code that is silently wrong.
 * `looksLikeRoomCode` in `shared/protocol` remains the check that decides.
 */
private fun isCodeCharacter(c: Char): Boolean = c in "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

/** The length every code the registry issues has. */
const val CodeLength: Int = 6

private val Corner = 8.dp
private val Edge = 1.dp
private val MinTap = 48.dp
private val CellHeight = 58.dp
private val CellGap = 6.dp
private val LabelGap = 6.dp
private val PadH = 14.dp
private val PadV = 12.dp

/** How dark the top of the groove is — the whole of the cut illusion. */
private const val Cut = 0.28f

private const val LabelSize = 12
private const val CellSize = 24
private const val DetailSize = 12
private const val BodySize = 17

package game.vinto.app.game

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import game.vinto.app.art.Res
import game.vinto.app.art.card_10
import game.vinto.app.art.card_2
import game.vinto.app.art.card_3
import game.vinto.app.art.card_4
import game.vinto.app.art.card_5
import game.vinto.app.art.card_6
import game.vinto.app.art.card_7
import game.vinto.app.art.card_8
import game.vinto.app.art.card_9
import game.vinto.app.art.card_a
import game.vinto.app.art.card_back
import game.vinto.app.art.card_described
import game.vinto.app.art.card_face_down
import game.vinto.app.art.card_j
import game.vinto.app.art.card_joker
import game.vinto.app.art.card_k
import game.vinto.app.art.card_q
import game.vinto.app.theme.LocalFeedback
import game.vinto.app.theme.Signal
import game.vinto.app.theme.onFelt
import game.vinto.engine.CardView
import game.vinto.shapes.Rank
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val Hairline = 1.dp
private val Ring = 3.dp

private const val FLIP_MS = 420
private const val PULSE_MS = 1100
private const val HALF_TURN = 180f
private const val QUARTER_TURN = 90f
private const val CAMERA = 14f

// The trough of the breath, not its floor. A ring that fades to 45% is 1.8:1 against the
// card for half of every cycle, which is a signal that flickers in and out of existence for
// anybody who needs contrast; from 70% it stays at 3.2:1 at its dimmest and doubles at its
// brightest, which is still plainly a breath.
private const val PULSE_LOW = 0.7f
private const val PULSE_HIGH = 1f

private const val FLINCH_MS = 420
private const val SHAKE_PX = 3f

/** A turned card's footprint is its own, rotated — wide where it was tall. */
private fun CardScale.footprintWidth(turned: Boolean) = if (turned) height else width

private fun CardScale.footprintHeight(turned: Boolean) = if (turned) width else height

/**
 * One card, drawn from the same artwork the web app uses.
 *
 * Shared art rather than a Compose-drawn rank and value: the deck is the game's face, and two
 * clients with different decks look like two games. The images live in `composeResources` as
 * vector drawables, and they are **generated** — `python3 tools/make-card-faces.py` writes them
 * and the SVG preview in `tools/card-faces/` from one source. A card is redrawn there, and
 * never here: an edit to the XML is thrown away by the next run of the generator.
 *
 * Face-down cards carry no identity at all — [CardView.Hidden] holds nothing, because a card
 * id encodes its rank and shipping one for a face-down card would leak every hand while
 * looking perfectly redacted. A back is therefore drawn from position alone.
 *
 * Turning over is a **flip**, not a swap, and the reason is legibility rather than polish: a
 * card that changes face between frames is one the player has to be told about, while a card
 * that turns over says it itself. The face is exchanged at the quarter turn, where the card is
 * edge-on and neither side is visible — the same trick the web app's `flip-card` does.
 */
@Composable
fun CardFace(
    card: CardView,
    scale: CardScale,
    modifier: Modifier = Modifier,
    state: CardState = CardState(),
    label: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val faceUp = card is CardView.Visible

    val turn by animateFloatAsState(
        targetValue = if (faceUp) HALF_TURN else 0f,
        animationSpec = tween(FLIP_MS, easing = FastOutSlowInEasing),
        label = "flip",
    )

    // Held so the back stays drawn through the first half of the turn: `card` becomes Visible
    // the moment the state changes, and drawing the face immediately would show it a fifth of
    // a second before the card has finished turning.
    val showingFace = turn > QUARTER_TURN
    val shape = RoundedCornerShape(TableSizes.Corner)
    val density = LocalDensity.current

    // A hand flinches when a penalty card lands in it. Small and quick — enough to catch the
    // eye of somebody looking elsewhere, which is the entire job: a card appearing in your
    // hand with no explanation is the most confusing thing this game does.
    val shake by animateFloatAsState(
        targetValue = if (state.flinching) 1f else 0f,
        animationSpec = tween(FLINCH_MS, easing = FastOutSlowInEasing),
        label = "flinch",
    )

    val feedback = LocalFeedback.current

    // Read out loud when somebody cannot see it. A description is copy like any other: a
    // screen reader announcing "a face-down card" in a Belarusian game is the same failure as
    // an untranslated button, so it comes from resources too.
    val spoken = label ?: when (card) {
        is CardView.Visible ->
            stringResource(Res.string.card_described, card.card.rank.serialName, card.card.value)

        CardView.Hidden -> stringResource(Res.string.card_face_down)
    }

    Box(
        modifier = modifier
            // The footprint, fixed, and never smaller than a thumb.
            //
            // It used to be a pair of *minimums*, and a turned card came out square: the
            // picture inside is measured unrotated — 38 wide by 53 tall — because a rotation
            // is drawn rather than laid out, so a minimum height of 38 and a child of 53 gave
            // a box of 53 by 53. Five of those need a third more height than five cards do,
            // which is why the side seats ran out of table. Fixed, so it is also the same
            // size whether or not the card can be tapped at this moment — a card that grew
            // by four points when it became touchable would nudge the whole line.
            .size(
                width = maxOf(scale.footprintWidth(state.turned), TapTarget),
                height = maxOf(scale.footprintHeight(state.turned), TapTarget),
            )
            // The tap goes on the *footprint*, not on the picture. A card smaller than a
            // thumb reserves the space either way — see the size above — and hanging the
            // click on the card itself meant an opponent's 36dp card offered a 36dp target
            // inside a 44dp box, which is a miss waiting to happen with three bots watching.
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.clickable(
                        role = Role.Button,
                        onClick = {
                            feedback.touch()
                            onClick()
                        },
                    )
                },
            )
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .size(scale.width, scale.height)
                .graphicsLayer {
                    rotationY = turn
                    // A quarter turn for the seats at the sides of the table, so their cards
                    // lie the way cards lie in front of somebody sitting there.
                    rotationZ = if (state.turned) QUARTER_TURN else 0f
                    translationX = shake * SHAKE_PX * density.density
                    cameraDistance = CAMERA * density.density
                }
                .clip(shape)
                .border(state.ringWidth(), state.ringColour(), shape),
            shape = shape,
            color = Color.Transparent,
        ) {
            Image(
                painter = painterResource(if (showingFace) card.art() else Res.drawable.card_back),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                // The back of the layer is a mirror image once past the quarter turn, so the
                // face is flipped back the other way to read as a card rather than a reflection.
                modifier = Modifier.graphicsLayer { rotationY = if (showingFace) HALF_TURN else 0f },
            )
        }
    }
}

/**
 * The ring around a card: steady gold for one this action has claimed, a slow pulse for one
 * that can be touched.
 *
 * The pulse is what tells a player where to look. Three quarters of the table is untappable at
 * any moment, and the alternative to drawing attention to the few that are is dimming the
 * many that are not — which on a card table reads as a fault rather than a hint.
 */
@Composable
private fun CardState.ringColour(): Color {
    // A declaration answered: green for a right call, red for a wrong one. Loudest, because
    // it is the one moment in the game that is a gamble on your own memory.
    verdict?.let { return if (it) Signal.rightCall else Signal.wrongCall }
    if (chosen) return Signal.chosen
    if (live) return Signal.live
    if (!tappable) return Color.Transparent

    val pulse = rememberInfiniteTransition(label = "tappable")
    val strength by pulse.animateFloat(
        initialValue = PULSE_LOW,
        targetValue = PULSE_HIGH,
        animationSpec = infiniteRepeatable(
            animation = tween(PULSE_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    return Signal.tappable.copy(alpha = strength)
}

/** A card with something to say about it wears a ring; the rest wear a hairline. */
private fun CardState.ringWidth() = if (marked()) Ring else Hairline

private fun CardState.marked() = verdict != null || chosen || tappable || live

/** Which picture a card shows. A hidden one always shows the back, and knows nothing else. */
private fun CardView.art(): DrawableResource = when (this) {
    CardView.Hidden -> Res.drawable.card_back
    is CardView.Visible -> artFor(card.rank)
}

/**
 * The picture for a rank, for anywhere a card is *talked about* rather than played.
 *
 * The help sheet and the lesson both explain cards, and a rank explained as the letter "Q" is
 * a rank a player then has to match against a picture on the table. Same art either way, so
 * what they learn from the words is what they see on the felt.
 */
@Composable
fun CardPicture(rank: Rank, width: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(artFor(rank)),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.width(width),
    )
}

/** The picture for a rank. Internal because the toss-in area draws thrown cards too. */
internal fun artFor(rank: Rank): DrawableResource = when (rank) {
    Rank.TWO -> Res.drawable.card_2
    Rank.THREE -> Res.drawable.card_3
    Rank.FOUR -> Res.drawable.card_4
    Rank.FIVE -> Res.drawable.card_5
    Rank.SIX -> Res.drawable.card_6
    Rank.SEVEN -> Res.drawable.card_7
    Rank.EIGHT -> Res.drawable.card_8
    Rank.NINE -> Res.drawable.card_9
    Rank.TEN -> Res.drawable.card_10
    Rank.JACK -> Res.drawable.card_j
    Rank.QUEEN -> Res.drawable.card_q
    Rank.KING -> Res.drawable.card_k
    Rank.ACE -> Res.drawable.card_a
    Rank.JOKER -> Res.drawable.card_joker
}

/**
 * The gap a pile leaves when it runs out, and the one a hand holds open for a card in the air.
 *
 * @param turned for a seat at the side of the table, whose cards lie sideways — the gap has to
 *   be the shape of the card that left it, or the hand shuffles along while the card flies.
 */
@Composable
fun EmptySlot(
    scale: CardScale,
    caption: String,
    modifier: Modifier = Modifier,
    turned: Boolean = false,
) {
    // Two boxes, and the difference between them is the bug this fixes. The outer one is the
    // *footprint*, padded out to a thumb like every other card's so that nothing on the table
    // shifts when a slot fills or empties. The outline goes on the inner one, which is the
    // size a card is actually drawn at — an outline on the footprint drew the empty draw,
    // discard and toss-in slots wider than the cards that land in them, which on a table
    // whose whole subject is cards reads as three cards of the wrong size.
    Box(
        modifier = modifier.size(
            width = maxOf(scale.footprintWidth(turned), TapTarget),
            height = maxOf(scale.footprintHeight(turned), TapTarget),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(scale.footprintWidth(turned), scale.footprintHeight(turned))
                .border(
                    Hairline,
                    MaterialTheme.colorScheme.onFelt().copy(alpha = FAINT),
                    RoundedCornerShape(TableSizes.Corner),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onFelt().copy(alpha = FAINT),
            )
        }
    }
}

private const val FAINT = 0.4f

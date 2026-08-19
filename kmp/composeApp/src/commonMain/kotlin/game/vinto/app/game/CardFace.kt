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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import game.vinto.app.art.Res
import game.vinto.app.theme.onFelt
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
import game.vinto.app.art.card_j
import game.vinto.app.art.card_joker
import game.vinto.app.art.card_k
import game.vinto.app.art.card_q
import game.vinto.engine.CardView
import game.vinto.shapes.Rank
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private val Hairline = 1.dp
private val Ring = 3.dp
private val TapTarget = 44.dp

private const val FLIP_MS = 420
private const val PULSE_MS = 1100
private const val HALF_TURN = 180f
private const val QUARTER_TURN = 90f
private const val CAMERA = 14f
private const val PULSE_LOW = 0.45f
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
 * clients with different decks look like two games. The images live in `composeResources` and
 * are copied from `apps/vinto/src/app/images`, so a redraw lands in both.
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
    val scheme = MaterialTheme.colorScheme
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

    Box(
        modifier = modifier
            .sizeIn(
                minWidth = if (onClick != null) TapTarget else scale.footprintWidth(state.turned),
                minHeight = scale.footprintHeight(state.turned),
            )
            .semantics { contentDescription = label ?: describe(card) },
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
                .border(state.ringWidth(), state.ringColour(scheme), shape),
            shape = shape,
            color = Color.Transparent,
            onClick = onClick ?: {},
            enabled = onClick != null,
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
private fun CardState.ringColour(scheme: androidx.compose.material3.ColorScheme): Color {
    if (chosen) return scheme.secondary
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
    return scheme.primary.copy(alpha = strength)
}

private fun CardState.ringWidth() = if (chosen || tappable) Ring else Hairline

/** Which picture a card shows. A hidden one always shows the back, and knows nothing else. */
private fun CardView.art(): DrawableResource = when (this) {
    CardView.Hidden -> Res.drawable.card_back
    is CardView.Visible -> when (card.rank) {
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
}

private fun describe(card: CardView): String = when (card) {
    is CardView.Visible -> "${card.card.rank.serialName}, worth ${card.card.value}"
    CardView.Hidden -> "a face-down card"
}

/** The gap a pile leaves when it runs out. */
@Composable
fun EmptySlot(scale: CardScale, caption: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(scale.width, scale.height)
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

private const val FAINT = 0.4f

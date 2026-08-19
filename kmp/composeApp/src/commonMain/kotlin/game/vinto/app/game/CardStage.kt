package game.vinto.app.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import game.vinto.client.Anchor
import game.vinto.client.CardFlight
import game.vinto.engine.CardView
import kotlinx.coroutines.flow.Flow

private const val FLIGHT_MS = 340

/**
 * Where the table's fixed places are on the screen.
 *
 * Filled by the table itself as it lays out — each card, pile and pending slot reports its
 * position — and read by the overlay, which is the only part that needs to know. The game
 * works out *that* a card moved from the deck to a hand; this is the only thing that knows
 * where either of those is, and it changes with every screen size.
 */
class Stage {
    private val places = mutableStateMapOf<Anchor, Offset>()
    private var origin: Offset = Offset.Zero

    internal val flying = mutableStateListOf<Flight>()

    /**
     * Places whose card is currently in the air.
     *
     * The table draws a gap at these, because the card is being drawn by the overlay instead.
     * Without it a card is in two places at once for the third of a second it is moving, and
     * the eye notices the copy rather than the movement.
     */
    val inFlight: Set<Anchor> get() = flying.mapTo(mutableSetOf()) { it.landingAt }

    fun place(anchor: Anchor, coordinates: LayoutCoordinates) {
        places[anchor] = coordinates.positionInRoot() - origin
    }

    internal fun setOrigin(coordinates: LayoutCoordinates) {
        origin = coordinates.positionInRoot()
    }

    internal fun locate(anchor: Anchor): Offset? = places[anchor]

    internal data class Flight(
        val id: Long,
        val card: CardView,
        val from: Offset,
        val to: Offset,
        val landingAt: Anchor,
    )
}

/** Reports this composable's position as [anchor], so a card can be flown to or from it. */
fun Modifier.anchoredAt(stage: Stage, anchor: Anchor): Modifier =
    onGloballyPositioned { stage.place(anchor, it) }

val LocalStage = compositionLocalOf { Stage() }

/**
 * The table, with a layer above it for cards in transit.
 *
 * Cards move by being drawn *over* the table rather than by the table rearranging itself. The
 * web app does the same thing, and the reason is not laziness: a card moving from a hand to
 * the discard pile passes over three other hands, and a layout that tried to animate it in
 * place would have to make room for it in every one of them.
 *
 * The table underneath updates immediately, which means for the third of a second a card is in
 * the air it is also already at its destination. In practice nobody sees it — the flight is
 * over before the eye finishes following it — and the alternative, holding the whole table a
 * beat behind the game, makes every tap feel late.
 */
@Composable
fun CardStage(
    flights: Flow<List<CardFlight>>,
    sizes: TableSizes,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val stage = remember { Stage() }

    LaunchedEffect(flights) {
        var next = 0L
        flights.collect { batch ->
            // One frame first, so the table has re-laid-out and reported where things now are.
            // A flight is worked out from the move, not from the drawing, and the drawing is
            // always a frame behind — asking for positions in the same frame as the move gets
            // the previous ones, or none at all for a slot that has only just appeared.
            withFrameNanos { }

            batch.forEach { flight ->
                val from = stage.locate(flight.from)
                val to = stage.locate(flight.to)
                // A flight between two places the table has not laid out has nowhere to go.
                // Skipping it is right: the card is already where it belongs.
                if (from != null && to != null && from != to) {
                    stage.flying += Stage.Flight(
                        id = next++,
                        card = flight.card?.let { CardView.Visible(it) } ?: CardView.Hidden,
                        from = from,
                        to = to,
                        landingAt = flight.to,
                    )
                }
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize().onGloballyPositioned { stage.setOrigin(it) },
    ) {
        CompositionLocalProvider(LocalStage provides stage) { content() }

        stage.flying.forEach { flight ->
            key(flight.id) { InFlight(flight, sizes, onArrival = { stage.flying.remove(flight) }) }
        }
    }
}

@Composable
private fun InFlight(flight: Stage.Flight, sizes: TableSizes, onArrival: () -> Unit) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(flight.id) {
        progress.animateTo(1f, tween(FLIGHT_MS, easing = FastOutSlowInEasing))
        onArrival()
    }

    val t = progress.value
    val at = Offset(
        x = flight.from.x + (flight.to.x - flight.from.x) * t,
        y = flight.from.y + (flight.to.y - flight.from.y) * t,
    )

    Box(modifier = Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }) {
        CardFace(flight.card, sizes.mine)
    }
}

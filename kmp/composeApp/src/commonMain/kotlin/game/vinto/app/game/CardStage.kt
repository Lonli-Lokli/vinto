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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import game.vinto.client.Anchor
import game.vinto.client.Attention
import game.vinto.client.AnimationQueue
import game.vinto.client.Beat
import game.vinto.client.Frame
import game.vinto.client.Scene
import game.vinto.engine.CardView
import game.vinto.engine.PlayerView
import game.vinto.shapes.Card
import game.vinto.shapes.Rank
import game.vinto.shapes.getCardConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt

/**
 * How long each thing takes.
 *
 * Tuned on a phone rather than on a desktop preview, and the first version was roughly twice
 * this fast. The speed a card *can* cross the table is not the speed at which anybody learns
 * anything from watching it: three bots take their turns between one tap and the next, and at
 * 340 ms a move that is one continuous flicker with no seam between one player and the next.
 * The pauses matter as much as the movements — [TURN_GAP_MS] is what separates "Raph did
 * something, then Don did something" from "some cards moved".
 */
private const val MOVE_MS = 460
private const val PEEK_MS = 1100

/** The lift itself is quick; the card then stays up for the rest of the beat. */
private const val PEEK_LIFT_MS = 550
private const val STAGE_GROW_MS = 300
private const val STAGE_MS = 900
private const val VERDICT_MS = 900
private const val ATTEND_MS = 700
private const val LIFT_FRACTION = 0.28f
private const val STAGE_SCALE = 1.5f
private const val START_SCALE = 0.6f
private const val FULL_TURN = 360f
private const val RESHUFFLE_MS = 900
private const val SWEEP_CARDS = 5
private const val SWEEP_STAGGER = 0.12f
private const val BESIDE_PX = 150f
private const val FLINCH_MS = 420
private const val SAY_MS = 1400

/** Between two scenes of the same move — the beat between a King's declaration and its work. */
private const val BETWEEN_SCENES_MS = 140L

/** Between two players' moves. The seam that makes a turn a turn. */
private const val TURN_GAP_MS = 380L

/**
 * Where the table's fixed places are on screen, and what is currently happening at them.
 *
 * Filled by the table as it lays out — each card, pile and pending slot reports its position —
 * and read by the overlay, which is the only part that needs to know. The game works out
 * *that* a card moved from the deck to a hand; this is the only thing that knows where either
 * of those is, and it changes with every screen size.
 */
class Stage {
    private val places = mutableStateMapOf<Anchor, Offset>()
    private var origin: Offset = Offset.Zero

    internal val flying = mutableStateListOf<Flight>()

    /** Hands mid-flinch, and seats mid-sentence: what the table draws that is not a card. */
    internal val flinching = mutableStateListOf<Anchor>()
    internal val saying = mutableStateMapOf<String, String>()

    /** Cards being looked at, and by extension hidden from their own place while they are. */
    internal val peeking = mutableStateMapOf<Anchor, CardView>()

    /** A card held up in the middle of the table while its action happens. */
    internal var staged: CardView? by mutableStateOf(null)

    /** A rank a King is borrowing, shown in the middle while it does that card's job. */
    internal var borrowed: Rank? by mutableStateOf(null)

    /** How many cards the deck has just taken back, while that is being drawn. */
    internal var refilling: Int by mutableStateOf(0)

    /** Declarations answered: true for a right call, false for a wrong one. */
    internal val verdicts = mutableStateMapOf<Anchor, Boolean>()

    /** Seats the table is pointing at, and why. */
    internal val attention = mutableStateMapOf<String, Attention>()

    fun isPeeking(anchor: Anchor): Boolean = anchor in peeking

    fun verdictAt(anchor: Anchor): Boolean? = verdicts[anchor]

    fun attentionOn(playerId: String): Attention? = attention[playerId]

    fun place(anchor: Anchor, coordinates: LayoutCoordinates) {
        places[anchor] = coordinates.positionInRoot() - origin
    }

    /**
     * Places whose card is currently in the air.
     *
     * The table draws a gap at these, because the card is being drawn by the overlay instead.
     * Without it a card is in two places at once for the third of a second it is moving, and
     * the eye notices the copy rather than the movement.
     */
    val inFlight: Set<Anchor> get() = flying.mapTo(mutableSetOf()) { it.landingAt }

    fun isFlinching(anchor: Anchor): Boolean = anchor in flinching

    fun lineFor(playerId: String): String? = saying[playerId]

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
        val spin: Boolean = false,
    )

    /** The middle of the table, which is where a peeked card lifts towards. */
    internal fun centre(): Offset = locate(Anchor.Discard) ?: Offset.Zero
}

/** Reports this composable's position as [anchor], so a beat can be played at it. */
fun Modifier.anchoredAt(stage: Stage, anchor: Anchor): Modifier =
    onGloballyPositioned { stage.place(anchor, it) }

val LocalStage = compositionLocalOf { Stage() }

/**
 * The table, with a layer above it for everything in motion.
 *
 * Scenes are played one after another and the beats inside a scene together, which is what
 * makes a swap read as two cards crossing rather than as two separate moves. What arrives
 * faster than it can be played is dropped by the [AnimationQueue] rather than queued up — see
 * the design note there; a client that fell behind lands on the current state.
 *
 * Cards move by being drawn *over* the table rather than by the table rearranging itself. A
 * card going from a hand to the discard pile passes over three other hands, and a layout that
 * animated it in place would have to make room for it in every one of them.
 *
 * **The table the content draws is the one being animated, not the one the engine is on.**
 * The engine finishes all three bots' turns before a single card has moved — it has to, since
 * each move depends on the last — so a screen wired straight to the live view shows the
 * *final* position and then narrates the past over the top of it: cards fly out of hands they
 * are no longer in, towards a pile that already has them. Each frame carries the table its own
 * move left behind, and [content] is given that instead, catching up to the live view as soon
 * as there is nothing left to play.
 *
 * @param live where the game actually is. Shown whenever nothing is being animated, which is
 *   every moment the player can act.
 */
@Composable
fun CardStage(
    frames: Flow<List<Frame>>,
    live: PlayerView,
    sizes: TableSizes,
    modifier: Modifier = Modifier,
    content: @Composable (PlayerView) -> Unit,
) {
    val stage = remember { Stage() }
    val queue = remember { AnimationQueue<Frame>(takesTime = { it.hasSomethingToSee }) }

    // The move being shown, while it is behind the one the engine is on. Null means caught up,
    // which is both the resting state and the only state the player is asked to act in.
    var behind by remember { mutableStateOf<PlayerView?>(null) }

    // Drains only while there is something to play, and then stops.
    //
    // The obvious shape — a `while (true)` asking for a frame each time round — spins for the
    // life of the screen, and the cost is not the CPU: a composition that requests a frame
    // forever is a composition that is never idle, so `waitForIdle` in a UI test never
    // returns and neither does anything else built on idling. Collecting and draining per
    // batch has the same effect and goes quiet in between.
    LaunchedEffect(frames) {
        var next = 0L
        var lastActor: String? = null

        frames.collect { batch ->
            queue.submit(batch)

            while (true) {
                val frame = queue.next() ?: break

                // A pause when the turn passes. Without it three bot turns are one long
                // stream of cards with no way to tell whose move any of them was.
                if (frame.hasSomethingToSee) {
                    if (lastActor != null && frame.actorId != lastActor) delay(TURN_GAP_MS)
                    lastActor = frame.actorId
                }

                // The table steps to this move before its cards fly, because the overlay
                // draws a gap where a card is landing: the seat has to be showing the card
                // for the gap to be in the right place.
                behind = frame.view

                for (scene in frame.scenes) {
                    // One frame first, so the table has re-laid-out and reported where things
                    // now are. A scene is worked out from the move, and the drawing is always
                    // a frame behind — asking for positions in the same frame gets the
                    // previous ones, or none at all for a slot that has only just appeared.
                    withFrameNanos { }
                    next = stage.play(scene, next)
                    delay(BETWEEN_SCENES_MS)
                }
            }

            // Caught up — and the only path when a batch was dropped for being too far
            // behind, which is what makes the drop land on the present rather than nowhere.
            behind = null
        }
    }

    Box(modifier = modifier.fillMaxSize().onGloballyPositioned { stage.setOrigin(it) }) {
        CompositionLocalProvider(LocalStage provides stage) { content(behind ?: live) }

        stage.peeking.forEach { (anchor, card) -> BeingLookedAt(stage, anchor, card, sizes) }

        stage.flying.forEach { flight ->
            key(flight.id) { InFlight(flight, sizes, onArrival = { stage.flying.remove(flight) }) }
        }

        stage.staged?.let { HeldUp(it, sizes, stage.centre()) }
        stage.borrowed?.let { Borrowed(it, sizes, stage.centre()) }
        if (stage.refilling > 0) Reshuffling(stage.refilling, sizes, stage)
    }
}

/**
 * Starts every beat in a scene at once and returns when the longest has finished.
 *
 * Movement owns the clock; a flinch and a line run alongside it and clear themselves, because
 * neither is something the next scene has to wait for.
 */
private suspend fun Stage.play(scene: Scene, firstId: Long): Long {
    var id = firstId
    var longest = 0

    scene.forEach { beat ->
        longest = maxOf(longest, start(beat) { id++ })
    }

    if (longest > 0) delay(longest.toLong())
    flinching.clear()
    peeking.clear()
    verdicts.clear()
    attention.clear()
    staged = null
    borrowed = null
    refilling = 0
    if (saying.isNotEmpty()) {
        delay(SAY_MS.toLong())
        saying.clear()
    }
    return id
}

/**
 * Starts one beat and says how long the scene must wait for it.
 *
 * One branch per kind, each independent of the others — a flinch does not care that a card is
 * also moving. The scene takes the longest of them, which is what makes beats in a scene
 * simultaneous without any of them knowing about the rest.
 */
private fun Stage.start(beat: Beat, nextId: () -> Long): Int = when (beat) {
    is Beat.Move -> {
        val from = locate(beat.from)
        val to = locate(beat.to)

        // A beat between two places the table has not laid out has nowhere to go. Skipping it
        // is right: the card is already where it belongs.
        if (from == null || to == null || from == to) {
            0
        } else {
            flying += Stage.Flight(
                id = nextId(),
                card = beat.card.faceOrBack(),
                from = from,
                to = to,
                landingAt = beat.to,
                spin = beat.spin,
            )
            MOVE_MS
        }
    }

    is Beat.Peek -> {
        peeking[beat.at] = beat.card.faceOrBack()
        PEEK_MS
    }

    is Beat.Stage -> {
        staged = beat.card.faceOrBack()
        STAGE_MS
    }

    is Beat.Borrowed -> {
        borrowed = beat.rank
        STAGE_MS
    }

    is Beat.Reshuffle -> {
        refilling = beat.cards
        RESHUFFLE_MS
    }

    is Beat.Verdict -> {
        verdicts[beat.at] = beat.correct
        VERDICT_MS
    }

    is Beat.Attend -> {
        attention[beat.playerId] = beat.kind
        ATTEND_MS
    }

    is Beat.Flinch -> {
        flinching += beat.at
        FLINCH_MS
    }

    // A line stays up on its own clock; the next scene does not wait for somebody to stop
    // talking.
    is Beat.Say -> {
        saying[beat.playerId] = beat.line
        0
    }
}

private fun Card?.faceOrBack(): CardView =
    this?.let { CardView.Visible(it) } ?: CardView.Hidden

/**
 * A card of a given rank, for showing an action nobody actually played.
 *
 * The King's borrowed rank is a rank and not a card — there is no such card on the table —
 * so one is made up to draw. It never enters the game; `getCardConfig` supplies the value and
 * the text, which is the same source the rest of the app reads.
 */
private fun cardFor(rank: Rank): Card {
    val config = getCardConfig(rank)
    return Card(
        id = "borrowed_${rank.serialName}",
        rank = rank,
        value = config.value,
        actionText = config.shortDescription.takeIf { it.isNotEmpty() },
        played = false,
    )
}

@Composable
private fun BeingLookedAt(stage: Stage, anchor: Anchor, card: CardView, sizes: TableSizes) {
    val from = stage.locate(anchor) ?: return
    val centre = stage.centre()
    val lift = remember { Animatable(0f) }

    LaunchedEffect(anchor) {
        lift.animateTo(1f, tween(PEEK_LIFT_MS, easing = FastOutSlowInEasing))
    }

    val towards = LIFT_FRACTION * lift.value
    val at = Offset(
        x = from.x + (centre.x - from.x) * towards,
        y = from.y + (centre.y - from.y) * towards,
    )

    Box(modifier = Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }) {
        CardFace(card, sizes.mine, state = CardState(chosen = true))
    }
}

/** The action card, held up in the middle while what it does happens. */
@Composable
private fun HeldUp(card: CardView, sizes: TableSizes, centre: Offset) {
    val grow = remember { Animatable(START_SCALE) }
    LaunchedEffect(card) { grow.animateTo(1f, tween(STAGE_GROW_MS, easing = FastOutSlowInEasing)) }

    Box(
        modifier = Modifier
            .offset { IntOffset(centre.x.roundToInt(), centre.y.roundToInt()) }
            .graphicsLayer {
                scaleX = grow.value * STAGE_SCALE
                scaleY = grow.value * STAGE_SCALE
            },
    ) {
        CardFace(card, sizes.mine, state = CardState(chosen = true))
    }
}

/** The card a King is pretending to be, held up beside the King itself. */
@Composable
private fun Borrowed(rank: Rank, sizes: TableSizes, centre: Offset) {
    val shown = remember(rank) { CardView.Visible(cardFor(rank)) }
    val grow = remember { Animatable(START_SCALE) }
    LaunchedEffect(rank) { grow.animateTo(1f, tween(STAGE_GROW_MS, easing = FastOutSlowInEasing)) }

    Box(
        modifier = Modifier
            .offset { IntOffset((centre.x + BESIDE_PX).roundToInt(), centre.y.roundToInt()) }
            .graphicsLayer {
                scaleX = grow.value * STAGE_SCALE
                scaleY = grow.value * STAGE_SCALE
            },
    ) {
        CardFace(shown, sizes.mine, state = CardState(chosen = true))
    }
}

/**
 * The discard pile going back into the deck.
 *
 * Drawn as a handful of backs sweeping from the pile to the deck rather than as a number
 * changing, because what actually happened is that everything anybody remembered about the
 * pile stopped being true.
 */
@Composable
private fun Reshuffling(cards: Int, sizes: TableSizes, stage: Stage) {
    val from = stage.locate(Anchor.Discard) ?: return
    val to = stage.locate(Anchor.Deck) ?: return
    val sweep = remember { Animatable(0f) }

    LaunchedEffect(cards) { sweep.animateTo(1f, tween(RESHUFFLE_MS, easing = FastOutSlowInEasing)) }

    repeat(minOf(cards, SWEEP_CARDS)) { index ->
        // Staggered, so it reads as a stack going back rather than one card.
        val offset = (index * SWEEP_STAGGER).coerceAtMost(1f)
        val t = ((sweep.value - offset) / (1f - offset)).coerceIn(0f, 1f)
        val at = Offset(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t)

        Box(modifier = Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }) {
            CardFace(CardView.Hidden, sizes.theirs)
        }
    }
}

@Composable
private fun InFlight(flight: Stage.Flight, sizes: TableSizes, onArrival: () -> Unit) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(flight.id) {
        progress.animateTo(1f, tween(MOVE_MS, easing = FastOutSlowInEasing))
        onArrival()
    }

    val t = progress.value
    val at = Offset(
        x = flight.from.x + (flight.to.x - flight.from.x) * t,
        y = flight.from.y + (flight.to.y - flight.from.y) * t,
    )

    Box(
        modifier = Modifier
            .offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }
            // A full turn on the way, for a card that is somebody else's.
            .graphicsLayer { rotationZ = if (flight.spin) t * FULL_TURN else 0f },
    ) {
        CardFace(flight.card, sizes.mine)
    }
}

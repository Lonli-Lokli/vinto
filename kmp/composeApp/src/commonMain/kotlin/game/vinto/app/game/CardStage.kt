package game.vinto.app.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.toSize
import game.vinto.app.LocalReducedMotion
import game.vinto.app.theme.Feedback
import game.vinto.app.theme.LocalFeedback
import game.vinto.app.theme.LocalSounds
import game.vinto.app.theme.Sfx
import game.vinto.app.theme.Sounds
import game.vinto.client.Anchor
import game.vinto.client.AnimationQueue
import game.vinto.client.Attention
import game.vinto.client.Beat
import game.vinto.client.CardRef
import game.vinto.client.Frame
import game.vinto.client.Pacing
import game.vinto.client.heldUp
import game.vinto.client.revealedTo
import game.vinto.client.Scene
import game.vinto.client.Target
import game.vinto.client.tossedTogether
import game.vinto.engine.CardView
import game.vinto.engine.PlayerView
import game.vinto.shapes.Card
import game.vinto.shapes.Rank
import game.vinto.shapes.getCardConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * How long each movement takes.
 *
 * Tuned on a phone rather than in a desktop preview, and the first version was roughly twice
 * this fast. The speed a card *can* cross the table is not the speed at which anybody learns
 * anything from watching it. The pauses between the movements matter at least as much, and
 * they live in `Pacing` — where they can be tested, because they are a decision rather than a
 * drawing.
 */
/**
 * How long a card takes to cross the table, and how long a card being *shown* takes.
 *
 * The web app moves a card in 1500ms and swells a played one over 2500, which is where the
 * pace of this game was set and what it looks like: cards that travel, rather than cards that
 * are suddenly somewhere else. This was 460 — a third of that — and every report about the
 * table has been some version of "I could not see what happened".
 *
 * Both are scaled by the pace setting, so Brisk lands near the old speed and Calm past the
 * web's.
 */
/**
 * How long a card is shown off for as it is played.
 *
 * The web app gives it two seconds, which at a bot's pace is most of their turn; this is a
 * little under half of that, and the flight to the pile follows it.
 */
private const val FLOURISH_MS = 900

/**
 * How long a card turned face up for the table stays up.
 *
 * Longer than a peek, because a peek is one player looking and this is three of them being
 * told: a King named this card wrongly, and here is what it really was.
 */
private const val REVEAL_MS = 1_800

private const val MOVE_MS = 1_100
private const val SHOWN_MS = 1_600

/**
 * A dealt card's flight. A deal is twenty cards, and twenty at [MOVE_MS] is most of half a
 * minute watching the deck; nothing on a face-down card needs reading, so it travels at a
 * dealer's speed and the waves keep the rhythm.
 */
private const val DEAL_MS = 420
/** How long the table dwells on an aim — the rise and a beat to read it, not the lift's life. */
private const val PEEK_MS = 1100

/**
 * The rise is quick; how long the card then stays up is not a duration at all — the view
 * holds it (`holdUp`) until the action that took the card up resolves.
 */
private const val PEEK_LIFT_MS = 550

/**
 * And it goes back down again, rather than being taken away.
 *
 * A lifted card used to vanish the instant its scene ended, reappearing face-down in the hand
 * — which reads as the table blinking rather than as a player putting a card back. The card
 * lowers into its own place and turns over on the way, which is the same length as
 * `CardFace`'s flip so that it is face-down exactly as it lands.
 */
private const val PEEK_SETTLE_MS = 420
private const val STAGE_GROW_MS = 300
private const val STAGE_MS = 900
private const val VERDICT_MS = 900
private const val ATTEND_MS = 700
private const val LIFT_FRACTION = 0.28f
private const val STAGE_SCALE = 1.5f
private const val START_SCALE = 0.6f

/** How far a card swells at the top of its arc: a fifth, or half again when it is shown. */
private const val SWELL = 0.2f
private const val SHOWN_SWELL = 0.5f

/** The light a card travels in: a plain lift, or a green announcement. */
private val ShownGlow = Color(0xFF22C55E)
private const val LIFT = 0.28f
private const val SHOWN_GLOW = 0.85f
private const val GLOW_SPREAD = 0.8f
private const val RESHUFFLE_MS = 900
private const val SWEEP_CARDS = 5
private const val SWEEP_STAGGER = 0.12f
private const val BESIDE_PX = 150f
private const val FLINCH_MS = 420
private const val SAY_MS = 1400

/**
 * Where the table's fixed places are on screen, and what is currently happening at them.
 *
 * Filled by the table as it lays out — each card, pile and pending slot reports its position —
 * and read by the overlay, which is the only part that needs to know. The game works out
 * *that* a card moved from the deck to a hand; this is the only thing that knows where either
 * of those is, and it changes with every screen size.
 */
class Stage {
    private val berths = mutableStateMapOf<Anchor, Berth>()
    private var origin: Offset = Offset.Zero

    internal val flying = mutableStateListOf<Flight>()

    /** Hands mid-flinch, and seats mid-sentence: what the table draws that is not a card. */
    internal val flinching = mutableStateListOf<Anchor>()
    internal val saying = mutableStateMapOf<String, String>()

    /**
     * Cards held up by the running action, and by extension hidden from their own place.
     *
     * The *rise* of each is a beat; how long it stays up is the view's to say — an aim lasts
     * until the action resolves, on the game's clock, which is why this outlives any scene.
     * `holdUp` reconciles it against the shown view after every frame.
     */
    internal val peeking = mutableStateMapOf<Anchor, CardView>()

    /**
     * The lifted cards on their way back down — still lifted, so their hands keep the gap
     * open, but face-down and descending. Per card rather than one flag for the lot, because
     * lifts no longer share a scene's lifetime: after a dropped backlog, one action's cards
     * may be coming down at the moment another's are going up.
     */
    internal val settling = mutableStateListOf<Anchor>()


    /** A rank a King is borrowing, shown in the middle while it does that card's job. */
    internal var borrowed: Rank? by mutableStateOf(null)

    /** How many cards the deck has just taken back, while that is being drawn. */
    internal var refilling: Int by mutableStateOf(0)

    /** Declarations answered: true for a right call, false for a wrong one. */
    internal val verdicts = mutableStateMapOf<Anchor, Boolean>()

    /** Seats the table is pointing at, and why. */
    internal val attention = mutableStateMapOf<String, Attention>()

    /**
     * The player's chosen speed, as a multiplier on every duration here.
     *
     * One number for the lot, so a card, a peek and the pause between turns keep their
     * proportions to each other at either end of the dial — a table that sped up its
     * movements but not its pauses would read as jerky rather than as quick.
     */
    internal var pace: Float by mutableStateOf(1f)

    /**
     * No movement, same information. When set, everything that *travels* — flights, lifts,
     * the flourish's swell, the reshuffle's sweep — completes at once, while everything
     * that *says* something — a verdict ring, a borrowed rank, a line, every dwell — keeps
     * its time: those are the game being narrated, and reduced motion is not reduced
     * information. See `MotionChoice` in the client.
     */
    internal var reducedMotion: Boolean by mutableStateOf(false)

    /**
     * Whose phone this is, and what it can do about it.
     *
     * A penalty landing in a bot's hand is a thing to see; one landing in yours is a thing to
     * feel. The stage is where both are known at once — the beat says which seat, and this
     * says which seat is the one holding the phone.
     */
    internal var viewerId: String = ""
    internal var feedback: Feedback = Feedback(haptics = null, enabled = false)

    /** The table's sounds — silent unless the app root put real ones here. See [Sounds]. */
    internal var sounds: Sounds = Sounds(player = null, enabled = false)

    /** A duration, at the pace the player asked for. */
    internal fun ms(base: Int): Int = (base * pace).toInt()

    /** A *movement's* duration: zero under reduced motion, [ms] otherwise. */
    internal fun travel(base: Int): Int = if (reducedMotion) 0 else ms(base)

    /** The same, for the pauses between things, which are counted in milliseconds of delay. */
    internal fun paced(base: Long): Long = (base * pace).toLong()

    fun isPeeking(anchor: Anchor): Boolean = anchor in peeking

    fun verdictAt(anchor: Anchor): Boolean? = verdicts[anchor]

    fun attentionOn(playerId: String): Attention? = attention[playerId]

    /**
     * Where things are, in the stage's own coordinates, and how big they are.
     *
     * The berths are what the flights need — a place to fly from and one to land in. [bounds] is
     * what the coach's pointer needs, because a hand pointing at the *centre* of a full-width
     * button covers its label, and one pointing at a card has to know where the card's edge
     * is. Both are filled as the table lays out, by whatever drew the thing.
     */
    private val bounds = mutableStateMapOf<String, Rect>()

    /** The stage's own size, so a pointer near the bottom edge knows to point down instead. */
    internal var size: Size = Size.Zero
        private set

    /**
     * Records where a card lies and how it is drawn there. [card] is the size the *picture*
     * is drawn at, handed in by whoever drew it — see [Berth] for why it cannot be read off
     * the measured box, and why everything that flies or lifts navigates by these.
     */
    fun place(anchor: Anchor, coordinates: LayoutCoordinates, card: Size) {
        val topLeft = coordinates.positionInRoot() - origin
        val box = coordinates.size
        berths[anchor] = Berth(
            topLeft = topLeft,
            centre = topLeft + Offset(box.width / 2f, box.height / 2f),
            card = card,
            // A card is taller than it is wide, so a box wider than tall is a card lying
            // sideways, as they lie in front of the seats at the sides of the table.
            turned = box.width > box.height,
        )
        mark(anchor.key(), coordinates)
    }

    /** Records a piece of furniture — a button, a chip, a seat plate, the log — by name. */
    fun mark(id: String, coordinates: LayoutCoordinates) {
        bounds[id] = Rect(
            offset = coordinates.positionInRoot() - origin,
            size = coordinates.size.toSize(),
        )
    }

    internal fun boundsOf(id: String): Rect? = bounds[id]

    /**
     * Forgets where something was, because it is no longer anywhere.
     *
     * `onGloballyPositioned` reports a position and never reports its absence, so a button
     * that has left the rail leaves its rectangle behind — and the coach goes on pointing at
     * a place where nothing is. Composables that report themselves say so on the way out.
     */
    fun forget(id: String) {
        bounds.remove(id)
    }

    /**
     * Places whose card is currently in the air.
     *
     * The table draws a gap at these, because the card is being drawn by the overlay instead.
     * Without it a card is in two places at once for the third of a second it is moving, and
     * the eye notices the copy rather than the movement.
     */
    val inFlight: Set<Anchor> get() =
        flying.mapTo(mutableSetOf()) { it.landingAt } + expecting.keys

    /**
     * Places a card is about to arrive at, from the moment the table steps to the move.
     *
     * The table shows the position a move produced *before* the cards fly, so that a card has
     * a laid-out place to land in. For the frame or two in between, the engine's answer is
     * already on screen while the card is still where it started — which drew the discard's
     * new top card on the pile, then took it away again when the flight began, then put it
     * back when the flight landed. One card, three appearances, and the middle one a blink.
     *
     * Filled from the frame's own beats before the view steps, and emptied as each flight
     * takes over, so every place that asks "is something arriving here?" is told the truth
     * from the first frame rather than the third.
     */
    internal val expecting = mutableStateMapOf<Anchor, CardView>()

    /**
     * Cards the stepped view already shows face-up whose reveal has not been *played* yet.
     *
     * The table steps to the position a move produced before its scenes play, and at
     * scoring that position has every hand face-up — so without this the whole table
     * flashed over in one frame and the seat-by-seat reveal turned cards that were already
     * showing. A concealed card draws its back until its reveal scene starts, and the flip
     * is then `CardFace`'s own animation, in place, one seat at a time.
     */
    internal val concealing = mutableStateListOf<Anchor>()

    fun isConcealing(anchor: Anchor): Boolean = anchor in concealing

    /**
     * The hands a card is currently leaving, and from which slot.
     *
     * A hand closes up the instant a card leaves it, because the table has already stepped to
     * the position the move produced — so a card thrown in from the middle of a hand slides
     * the rest of that hand sideways *while it is still in the air*, and one thrown from the
     * end leaves from a slot that no longer exists at all.
     *
     * The web app draws a transparent card in the space instead. This is the same idea from
     * the other end: the hand keeps the gap until the flight lands, so nothing shifts under a
     * card that is still moving, and the card leaves from where it actually was.
     */
    /** The card on its way to [anchor], if one is. The pile asks so it never draws it twice. */
    fun landingOn(anchor: Anchor): CardView? =
        flying.firstOrNull { it.landingAt == anchor }?.card ?: expecting[anchor]

    /** Whether the card being shown off is lying at [anchor], and so is drawn by the flourish. */
    fun isFlourishing(anchor: Anchor): Boolean = flourish?.second == anchor

    /** Whether a card is on its way *out* of [anchor], and so must not be drawn there. */
    fun isLeaving(anchor: Anchor): Boolean = flying.any { it.leftFrom == anchor }

    val leaving: Map<String, Set<Int>>
        get() = flying
            .mapNotNull { it.leftFrom as? Anchor.Seat }
            .groupBy({ it.playerId }, { it.position })
            .mapValues { (_, positions) -> positions.toSet() }

    fun isFlinching(anchor: Anchor): Boolean = anchor in flinching

    fun lineFor(playerId: String): String? = saying[playerId]

    internal fun setOrigin(coordinates: LayoutCoordinates) {
        origin = coordinates.positionInRoot()
        size = coordinates.size.toSize()
    }

    /** A card being shown off where it lies, with how far through that it is. */
    /**
     * A card being shown off where it lies, or null. Read by the pile as well as drawn: while
     * a card is being flourished it has not landed anywhere, so nothing else may draw it.
     */
    var flourish: Pair<CardView, Anchor>? by mutableStateOf(null)

    internal fun locate(anchor: Anchor): Offset? = berths[anchor]?.topLeft

    internal fun berthOf(anchor: Anchor): Berth? = berths[anchor]

    /**
     * Where a card lies, or failing that the nearest place in the same hand.
     *
     * A card leaving a hand is measured against a table that has already lost it: the scene
     * is played after the table steps to the move, so a card thrown in from the last slot of
     * a hand has no slot to leave from and the flight was quietly dropped. Toss in your fifth
     * card and it did not move at all — it was simply gone from one place and present in
     * another, which is the one thing this table is not allowed to do.
     *
     * The nearest remaining slot of the same hand is a few points away from the truth and
     * infinitely closer than not animating it.
     */
    internal fun berthOrNearby(anchor: Anchor): Berth? = berths[anchor] ?: when (anchor) {
        is Anchor.Seat -> berths.keys
            .filterIsInstance<Anchor.Seat>()
            .filter { it.playerId == anchor.playerId }
            .minByOrNull { abs(it.position - anchor.position) }
            ?.let(berths::get)

        else -> null
    }

    internal data class Flight(
        val id: Long,
        val card: CardView,
        /** The centres it travels between — see [Berth] for why centres, never corners. */
        val from: Offset,
        val to: Offset,
        val landingAt: Anchor,
        /** Where it set off from, so the hand it left can hold the gap open. */
        val leftFrom: Anchor,
        /** How large the card is drawn where it started and where it lands, in pixels. */
        val fromCard: Size,
        val toCard: Size,
        /** And which way up it lies at each end: the seats at the sides lie theirs sideways. */
        val fromTurn: Float,
        val toTurn: Float,
        /** Lit on the way, for a card the table is being shown rather than merely moved. */
        val shown: Boolean = false,
        /** Flown at the dealer's tempo. See [DEAL_MS]. */
        val quick: Boolean = false,
    )

    /** The middle of the table, which is where a lifted card rises towards. */
    internal fun tableCentre(): Offset = berths[Anchor.Discard]?.centre ?: Offset.Zero

    /** Where a card lifted from [berth] hovers, [fraction] of the way up. */
    internal fun liftedCentre(berth: Berth, fraction: Float = 1f): Offset =
        berth.centre + (tableCentre() - berth.centre) * (LIFT_FRACTION * fraction)
}

/**
 * A place on the table a card can lie, measured by whatever drew it.
 *
 * Everything an overlay needs to draw a card *as it rests there*: where the slot is, where
 * its middle is, how large the card's picture is drawn in it, and which way it lies. The
 * picture size is handed in by the drawer rather than read off the measured box, because the
 * box is padded out to the 44dp tap target — a flight that trusted the box landed at
 * tap-target size on every card drawn smaller than a thumb, which is every opponent's card.
 *
 * Overlays align **centres** to a berth, never corners: the resting picture is centred in
 * its padded box and a side seat's quarter-turn spins about the middle, so the centre is the
 * one point every drawing of the card agrees on. Aligning the top-left corners of boxes of
 * different sizes is how a landing card used to jump by half the size difference the moment
 * it arrived. `LandingTest` measures both failures.
 */
internal data class Berth(
    val topLeft: Offset,
    val centre: Offset,
    val card: Size,
    val turned: Boolean,
)

/**
 * What a lesson is asking of the stage.
 *
 * @param hold whether to stop before playing the next move. A lesson holds it while the coach
 *   has something to read. Nothing about the *game* pauses — the engine finished those moves
 *   before any of them was drawn — it is the telling that waits, which is the one thing a
 *   screen may take its time over.
 * @param pointer what the coach's hand is pointing at. Drawn by the stage rather than by the
 *   screen because this is the layer *above* the table: a pointer laid out as a sibling of the
 *   felt ends up under the very card it is naming, and z-order between sibling subtrees is not
 *   a fight worth having when there is already a layer whose whole job is being on top.
 */
data class Coaching(
    val hold: () -> Boolean = { false },
    val pointer: Target? = null,
)

/**
 * Reports this composable's position as [anchor], so a beat can be played at it.
 *
 * [card] is the size the card's picture is drawn at in this place, and it travels with the
 * anchor because the measured box cannot say it — see [Berth]. Every place a card can lie
 * knows its own scale, so the drawer states it and the flights inherit the truth.
 */
@Composable
fun Modifier.anchoredAt(stage: Stage, anchor: Anchor, card: CardScale): Modifier {
    val drawn = with(LocalDensity.current) { Size(card.width.toPx(), card.height.toPx()) }
    return onGloballyPositioned { stage.place(anchor, it, drawn) }
}

/**
 * Reports this composable's position under a name, so the coach can point at it.
 *
 * For everything that is not a card: buttons, rank chips, seat plates, the deck count, the
 * box of recent moves. None of them is a place a card can be, which is why they are not
 * [Anchor]s, and all of them are things a player has to be shown once.
 */
@Composable
fun Modifier.markedAs(stage: Stage, id: String): Modifier {
    // The pair matters: one half says where this is, the other says when it stopped being
    // anywhere. Without the second, the pointer keeps aiming at the ghost of a button.
    DisposableEffect(stage, id) { onDispose { stage.forget(id) } }
    return onGloballyPositioned { stage.mark(id, it) }
}

/** The name a place on the table goes by, so anchors and furniture share one registry. */
fun Anchor.key(): String = when (this) {
    Anchor.Deck -> "deck"
    Anchor.Discard -> "discard"
    Anchor.Pending -> "pending"
    is Anchor.Seat -> "card:$playerId:$position"
}

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
@Suppress("LongParameterList") // A stage has many optional knobs; callers name what they set.
@Composable
fun CardStage(
    frames: Flow<List<Frame>>,
    live: PlayerView,
    sizes: TableSizes,
    pace: Float,
    modifier: Modifier = Modifier,
    /** What a lesson is asking of the stage, if one is running. See [Coaching]. */
    coaching: Coaching = Coaching(),
    /**
     * Scenes to play once, before any frame: the opening deal (`dealScenes`). Not frames,
     * because the deal is not an action — the dealt table precedes the first one — and not
     * replayed on a resume, which is the caller's `freshlyDealt` to decide.
     */
    opening: List<Scene> = emptyList(),
    content: @Composable (PlayerView) -> Unit,
) {
    val stage = remember { Stage() }
    val feedback = LocalFeedback.current
    val reducedMotion = LocalReducedMotion.current
    val sounds = LocalSounds.current
    SideEffect {
        stage.pace = pace
        stage.viewerId = live.viewerId
        stage.feedback = feedback
        stage.reducedMotion = reducedMotion
        stage.sounds = sounds
    }
    val queue = remember { AnimationQueue<Frame>(takesTime = { it.hasSomethingToSee }) }

    // The move being shown, while it is behind the one the engine is on. Null means caught up,
    // which is both the resting state and the only state the player is asked to act in.
    var behind by remember { mutableStateOf<PlayerView?>(null) }

    // The view being shown right now, current across recompositions: the frame loop below
    // reconciles the lifted cards against it after a drop, and a stale capture would lift
    // what *was* aimed at rather than what is.
    val current by rememberUpdatedState(live)

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

        // The deal, before anything else — see [playOpening].
        next = stage.playOpening(opening, next)

        // A game opened mid-action — a resume, a reconnect — may already be holding cards
        // up, and no frame is coming to say so: the lift is a state, so it is read.
        stage.holdUp(current)

        frames.collect { batch ->
            queue.submit(batch.tossedTogether())

            while (true) {
                val frame = queue.next() ?: break

                // Wait out whatever the coach is saying before showing the next move.
                snapshotFlow(coaching.hold).first { !it }

                // The beat where a person would be thinking. Without it, three bot turns are
                // one long stream of cards with no way to tell whose move any of them was —
                // and with it, a turn arriving *feels* like somebody taking one.
                delay(stage.paced(Pacing.thinkBefore(frame, lastActor, live.viewerId)))
                if (frame.hasSomethingToSee) lastActor = frame.actorId

                // What this move is about to move and to reveal, marked before the table
                // steps to it — see [prepareFor].
                stage.prepareFor(frame)

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
                    delay(stage.paced(Pacing.BETWEEN_SCENES_MS))
                }

                // Nothing is expected any more: every flight this move had has started, and
                // a beat that never flew (a card already where it was going) must not leave a
                // place waiting for it. Concealment ends the same way: a reveal that never
                // played must not leave a card wearing its back forever.
                stage.expecting.clear()
                stage.concealing.clear()

                // The lifted cards, brought into line with the table this move left behind.
                // After the scenes rather than between them, so a card stays up through the
                // whole of a verdict — a wrong King's target hovers through the red ring and
                // the penalty and turns over where it hovers — and comes down once, at the
                // end, if the view has let go of it.
                stage.holdUp(frame.view)

                // And the beat after, so the table can be read before the next thing happens.
                delay(stage.paced(Pacing.dwellAfter(frame, live.viewerId)))

                // Only now does the table stop pointing. A seat is pointed at for the whole
                // of what is happening to it — an Ace names a victim in one scene and the
                // card flies to them in the next, and a ring cleared between the two blinks
                // off at the exact moment the player looks up to see who was named.
                stage.attention.clear()
            }

            // Caught up — and the only path when a batch was dropped for being too far
            // behind, which is what makes the drop land on the present rather than nowhere.
            // The lifts land on the present too: whatever the current view holds up is up,
            // and nothing else is.
            behind = null
            stage.holdUp(current)
        }
    }

    Box(modifier = modifier.fillMaxSize().onGloballyPositioned { stage.setOrigin(it) }) {
        CompositionLocalProvider(LocalStage provides stage) { content(behind ?: live) }

        // Keyed by anchor: two cards can hover at once now, and positional memoization over
        // a map's iteration order would let one card's rise adopt another's animation state.
        stage.peeking.forEach { (anchor, card) ->
            key(anchor) { BeingLookedAt(stage, anchor, card, sizes) }
        }

        stage.flourish?.let { (card, at) ->
            Flourish(card, sizes, stage.berthOf(at), stage.tableCentre(), stage.travel(FLOURISH_MS))
        }

        stage.flying.forEach { flight ->
            key(flight.id) {
                // The same clock the scene is waiting on: a lit flight is given longer, and
                // was flying in the shorter time and then sitting still for the difference.
                val ms = stage.travel(flightMs(shown = flight.shown, quick = flight.quick))
                InFlight(flight, sizes, ms) {
                    stage.sounds.play(Sfx.LAND)
                    stage.flying.remove(flight)
                }
            }
        }

        stage.borrowed?.let { Borrowed(it, sizes, stage.tableCentre(), stage.travel(STAGE_GROW_MS)) }
        // The sweep is pure movement; under reduced motion the count still shows for the
        // scene's dwell, but nothing crosses the table.
        if (stage.refilling > 0 && !stage.reducedMotion) Reshuffling(stage.refilling, sizes, stage)

        Pointer(stage = stage, target = coaching.pointer)
    }
}

/**
 * The opening deal, played before any frame.
 *
 * Every slot a card will land in is marked as expecting first, so the whole table opens as
 * gaps and fills wave by wave — the same machinery a mid-game arrival uses, played over the
 * freshly dealt view.
 */
private suspend fun Stage.playOpening(opening: List<Scene>, firstId: Long): Long {
    if (opening.isEmpty()) return firstId

    opening.flatten().filterIsInstance<Beat.Move>().forEach { move ->
        expecting[move.to] = move.card.faceOrBack()
    }
    var next = firstId
    for (scene in opening) {
        withFrameNanos { }
        next = play(scene, next)
        delay(paced(Pacing.BETWEEN_SCENES_MS))
    }
    expecting.clear()
    return next
}

/**
 * Marks everything a frame is about to move or reveal, before the table steps to it.
 *
 * The places expecting a card must know from the same frame the new position appears, or
 * they draw the arrival early and blink when the flight starts. And everything the move is
 * about to *reveal* that the stepped view will already be showing — the scoring reveal —
 * stays concealed until its scene turns it. Only reveals the view itself makes visible: a
 * King's named card is transient and never in `revealedTo`, so it lifts as it always has.
 */
private fun Stage.prepareFor(frame: Frame) {
    expecting.clear()
    frame.scenes.flatten().filterIsInstance<Beat.Move>().forEach { move ->
        // The card as well as the place: a pile deciding what to draw underneath an
        // arrival has to know *which* card is arriving, or it cannot tell the card on its
        // way from the card already lying there.
        expecting[move.to] = move.card.faceOrBack()
    }

    concealing.clear()
    val visible = revealedTo(frame.view)
    frame.scenes.flatten().filterIsInstance<Beat.Reveal>().forEach { reveal ->
        val seat = reveal.at as? Anchor.Seat ?: return@forEach
        if (CardRef(seat.playerId, seat.position) in visible) {
            concealing += reveal.at
        }
    }
}

/**
 * Starts every beat in a scene at once and returns when the longest has finished.
 *
 * Movement owns the clock; a flinch and a line run alongside it and clear themselves, because
 * neither is something the next scene has to wait for. What a scene's end deliberately does
 * *not* do is lower the lifted cards: a lift is a state, not a moment, and it outlives every
 * scene until [holdUp] finds the view has let go of it or a flight takes the card away.
 */
private suspend fun Stage.play(scene: Scene, firstId: Long): Long {
    var id = firstId
    var longest = 0

    scene.forEach { beat ->
        longest = maxOf(longest, start(beat) { id++ })
    }

    if (longest > 0) delay(longest.toLong())
    flourish = null
    flinching.clear()
    verdicts.clear()
    borrowed = null
    refilling = 0
    if (saying.isNotEmpty()) {
        delay(ms(SAY_MS).toLong())
        saying.clear()
    }
    return id
}

/**
 * Brings the lifted cards into line with the view being shown.
 *
 * The rise of a lift is a beat; its lifetime is the view's (`heldUp`), which is why this
 * reconciles rather than remembers: whatever the view holds up stays up — a Queen's first
 * card through the choosing of her second, an aim through every toss-in that interrupts it —
 * and whatever it has let go of lowers home together, face-down on the way, into the space
 * its hand has held open. A card a flight took was already released by [fly], mid-air rather
 * than lowering. Reconciliation is also what makes a dropped backlog safe: the animation
 * queue lands on the present, and the present says exactly which cards are in the air.
 */
private suspend fun Stage.holdUp(view: PlayerView) {
    val held = view.heldUp()

    val released = peeking.keys.filter { it !in held }
    if (released.isNotEmpty()) {
        settling.addAll(released)
        delay(travel(PEEK_SETTLE_MS).toLong())
        released.forEach { anchor ->
            peeking.remove(anchor)
            settling.remove(anchor)
        }
    }

    // Cards the view holds that no beat has raised — the path a dropped or missing frame
    // takes. A beat-raised card is already here, and stays exactly as the beat drew it.
    held.forEach { (anchor, card) ->
        if (anchor !in peeking) peeking[anchor] = card
    }
}

/**
 * Starts one beat and says how long the scene must wait for it.
 *
 * One branch per kind, each independent of the others — a flinch does not care that a card is
 * also moving. The scene takes the longest of them, which is what makes beats in a scene
 * simultaneous without any of them knowing about the rest.
 */
private fun Stage.start(beat: Beat, nextId: () -> Long): Int = when (beat) {
    is Beat.Move -> fly(beat, nextId)

    // Two kinds of reveal, told apart by whether the table's own view already shows the
    // face. A transient one — a King's named card, a throw that missed — lifts the card for
    // the table and puts it back. A *concealed* one is the scoring reveal: the stepped view
    // has the card face-up and this stage has been drawing its back, so removing the
    // concealment IS the flip — `CardFace` animates it in place, and the scene dwells on it.
    is Beat.Reveal -> if (concealing.remove(beat.at)) {
        ms(REVEAL_MS)
    } else {
        lift(beat.at, CardView.Visible(beat.card), ms(REVEAL_MS))
    }

    is Beat.Peek -> lift(beat.at, beat.card.faceOrBack(), ms(PEEK_MS))

    is Beat.Flourish -> {
        flourish = beat.card.faceOrBack() to beat.at
        ms(FLOURISH_MS)
    }

    is Beat.Borrowed -> {
        borrowed = beat.rank
        ms(STAGE_MS)
    }

    is Beat.Reshuffle -> {
        refilling = beat.cards
        ms(RESHUFFLE_MS)
    }

    is Beat.Verdict -> {
        verdicts[beat.at] = beat.correct
        ms(VERDICT_MS)
    }

    is Beat.Attend -> {
        attention[beat.playerId] = beat.kind
        ms(ATTEND_MS)
    }

    is Beat.Flinch -> {
        flinching += beat.at
        // Only for the hand it happened to. A buzz for somebody else's penalty is a buzz for
        // something that is not your problem, which is how a phone teaches you to ignore it.
        // The thud is for everyone, though: a penalty is a public event at a real table.
        if ((beat.at as? Anchor.Seat)?.playerId == viewerId) feedback.refuse()
        sounds.play(Sfx.THUD)
        ms(FLINCH_MS)
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
    val berth = stage.berthOf(anchor) ?: return
    val lift = remember { Animatable(0f) }
    val settling = anchor in stage.settling

    LaunchedEffect(anchor, settling) {
        val to = if (settling) 0f else 1f
        val over = stage.travel(if (settling) PEEK_SETTLE_MS else PEEK_LIFT_MS)
        lift.animateTo(to, tween(over, easing = FastOutSlowInEasing))
    }

    // At the size and angle of the seat it lifted from, centred exactly over the resting
    // card at either end of the journey — see [Berth]. Drawn at your own hand's size and
    // upright, an opponent's card jumped bigger and straightened out as it lifted, and a seat
    // at the side of the table — whose cards lie sideways — snapped a quarter turn each way.
    val base = with(LocalDensity.current) { sizes.mine.width.toPx() }

    Box(
        modifier = Modifier.graphicsLayer {
            val span = berth.card.width
            cardTransform(
                centre = stage.liftedCentre(berth, lift.value),
                scale = if (base > 0f && span > 0f) span / base else 1f,
                turn = if (berth.turned) QUARTER else 0f,
            )
        },
    ) {
        // Face-down on the way back: the card turns over as it lowers, which is what a
        // player does with a card they have finished looking at. `CardFace` animates the
        // change itself, so handing it a hidden card *is* the flip. And a lifted card can
        // flinch — a Queen's pair jolts where it hovers when its player declines the swap.
        CardFace(
            if (settling) CardView.Hidden else card,
            sizes.mine,
            state = CardState(chosen = !settling, flinching = stage.isFlinching(anchor)),
        )
    }
}


/**
 * Sends a card across the table, and says how long the scene should wait for it.
 *
 * A beat between two places the table has not laid out has nowhere to go, and skipping it is
 * right: the card is already where it belongs.
 */
private fun Stage.fly(beat: Beat.Move, nextId: () -> Long): Int {
    val from = berthOrNearby(beat.from)
    val to = berthOrNearby(beat.to)
    if (from == null || to == null || from.centre == to.centre) return 0

    // The handoff: a card the flight is taking out of the air is released in the same call
    // that starts the flight, and the flight sets off from where the card is hovering — a
    // Queen's swap crosses her two lifted cards from their lifted places, not from the slots
    // they rose out of. One drawing owns the card at every moment.
    expecting.remove(beat.to)
    val wasLifted = peeking.remove(beat.from) != null
    if (wasLifted) settling.remove(beat.from)

    sounds.play(Sfx.DEAL)
    flying += Stage.Flight(
        id = nextId(),
        card = beat.card.faceOrBack(),
        from = if (wasLifted) liftedCentre(from) else from.centre,
        to = to.centre,
        landingAt = beat.to,
        leftFrom = beat.from,
        fromCard = from.card,
        toCard = to.card,
        fromTurn = if (from.turned) QUARTER else 0f,
        toTurn = if (to.turned) QUARTER else 0f,
        shown = beat.shown,
        quick = beat.quick,
    )
    return travel(flightMs(shown = beat.shown, quick = beat.quick))
}

/** How long a flight takes: a dealt card is brisk, a shown one lingers, the rest walk. */
private fun flightMs(shown: Boolean, quick: Boolean): Int = when {
    quick -> DEAL_MS
    shown -> SHOWN_MS
    else -> MOVE_MS
}

/**
 * Holds a card up where it lies, and says how long the scene should wait for it.
 *
 * One card lifting is two different moments: a peek, which is one player looking and which
 * shows the face only to them, and a reveal, which is the table being shown something.
 */
private fun Stage.lift(at: Anchor, card: CardView, holdMs: Int): Int {
    // A card caught on its way down turns and rises again — one entry, one drawing.
    settling.remove(at)
    peeking[at] = card
    return holdMs
}

/** The card a King is pretending to be, held up beside the King itself. */
@Composable
private fun Borrowed(rank: Rank, sizes: TableSizes, centre: Offset, growMs: Int) {
    val shown = remember(rank) { CardView.Visible(cardFor(rank)) }
    val grow = remember { Animatable(START_SCALE) }
    LaunchedEffect(rank) { grow.animateTo(1f, tween(growMs, easing = FastOutSlowInEasing)) }

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

    LaunchedEffect(cards) {
        sweep.animateTo(1f, tween(stage.ms(RESHUFFLE_MS), easing = FastOutSlowInEasing))
    }

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
private fun InFlight(
    flight: Stage.Flight,
    sizes: TableSizes,
    moveMs: Int,
    onArrival: () -> Unit,
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(flight.id) {
        progress.animateTo(1f, tween(moveMs, easing = FastOutSlowInEasing))
        onArrival()
    }

    val halo = remember(flight.shown) {
        Brush.radialGradient(
            colors = listOf(if (flight.shown) ShownGlow else Color.Black, Color.Transparent),
        )
    }

    // A card is drawn at the size of the place it is going to, so it never has to change size
    // on arrival. The table has three of them — your hand, the seats opposite, the piles — and
    // a flight drawn at one and landing in another popped the moment it got there.
    val drawn = sizes.mine
    val base = with(LocalDensity.current) { drawn.width.toPx() }

    Box(
        modifier = Modifier
            // Everything the flight does happens in the layer: position, lift and size. Read
            // from the animation *here*, in the draw phase, none of it recomposes — which is
            // the difference between a card that glides and a card that arrives in steps.
            .graphicsLayer {
                val t = progress.value
                val arc = sin(t * PI).toFloat()

                // Between the size it left and the size it is landing at, with the arc's lift
                // on top — zero at both ends, so the landing frame is the resting size.
                val span = flight.fromCard.width + (flight.toCard.width - flight.fromCard.width) * t
                val fit = if (base > 0f && span > 0f) span / base else 1f
                val swell = fit * (1f + arc * (if (flight.shown) SHOWN_SWELL else SWELL))

                // The card's *centre* rides the line between the two berths' centres — see
                // [Berth] for the jump that closes — turning only as far as it has to: a
                // card landing in a seat at the side of the table lies sideways there, so it
                // arrives already turned rather than snapping a quarter circle on touchdown.
                cardTransform(
                    centre = Offset(
                        flight.from.x + (flight.to.x - flight.from.x) * t,
                        flight.from.y + (flight.to.y - flight.from.y) * t,
                    ),
                    scale = swell,
                    turn = flight.fromTurn + (flight.toTurn - flight.fromTurn) * t,
                )
            }
            .drawBehind {
                val arc = sin(progress.value * PI).toFloat()
                drawCircle(
                    brush = halo,
                    radius = size.maxDimension * GLOW_SPREAD,
                    alpha = if (flight.shown) arc * SHOWN_GLOW else LIFT,
                )
            },
    ) {
        CardFace(flight.card, drawn)
    }
}

/** Degrees in a half turn, for turning the layer's angle into radians. */
private const val STRAIGHT_DEG = 180f

/**
 * Puts this layer's card at [centre], scaled and turned, about a pivot that cannot be wrong.
 *
 * The obvious way — centre-origin scale and rotation plus a corner translation — is correct
 * only once the layer knows its own size, because the pivot is `transformOrigin × size`. On
 * the frame a layer is *born* its transform can be applied about an unset pivot: the drawn
 * properties were evaluated with the right values, but the measured bounds of a card that is
 * scaled or turned come out somewhere else entirely — a side seat's card read forty-six
 * pixels from where it was drawn, for exactly the frames `QueenAimTest` samples. So the
 * pivot is folded into the translation instead: with the origin pinned to the top-left, the
 * matrix is the same affine map whether or not the size has reached the pivot machinery, and
 * a flight, lift or flourish measures where it draws from its first frame.
 */
private fun GraphicsLayerScope.cardTransform(centre: Offset, scale: Float, turn: Float) {
    transformOrigin = TransformOrigin(0f, 0f)
    scaleX = scale
    scaleY = scale
    rotationZ = turn

    val rad = turn * (PI.toFloat() / STRAIGHT_DEG)
    val toCentreX = size.width / 2f * scale
    val toCentreY = size.height / 2f * scale
    translationX = centre.x - (toCentreX * cos(rad) - toCentreY * sin(rad))
    translationY = centre.y - (toCentreX * sin(rad) + toCentreY * cos(rad))
}

/**
 * A card played for its action, shown off where it lies.
 *
 * The web app's most emphatic moment: the card swells to two and a half times its size, lit
 * green, and settles again — and it is the thing that tells the rest of the table this card
 * was *used* rather than put down, which changes what they may do with the pile next.
 *
 * It swells *from the resting card*: centred on the berth, starting and ending at the size
 * the card is drawn there, so the moment before the flourish and the moment after are the
 * same pixels. It used to start at your own hand's size at the slot's corner, which on the
 * pile was a card jumping larger and sideways in the instant it began to show off.
 */
@Composable
private fun Flourish(
    card: CardView,
    sizes: TableSizes,
    berth: Berth?,
    fallback: Offset,
    growMs: Int,
) {
    val bloom = remember { Animatable(0f) }
    LaunchedEffect(card) { bloom.animateTo(1f, tween(growMs, easing = FastOutSlowInEasing)) }

    val halo = remember {
        Brush.radialGradient(colors = listOf(ShownGlow, Color.Transparent))
    }
    val base = with(LocalDensity.current) { sizes.mine.width.toPx() }
    val at = berth?.centre ?: fallback

    Box(
        modifier = Modifier
            .graphicsLayer {
                val arc = sin(bloom.value * PI).toFloat()
                val span = berth?.card?.width ?: 0f
                val fit = if (base > 0f && span > 0f) span / base else 1f
                cardTransform(
                    centre = at,
                    scale = fit * (1f + arc * (FLOURISH_SWELL - 1f)),
                    turn = 0f,
                )
            }
            .drawBehind {
                drawCircle(
                    brush = halo,
                    radius = size.maxDimension * GLOW_SPREAD,
                    alpha = sin(bloom.value * PI).toFloat() * SHOWN_GLOW,
                )
            },
    ) {
        CardFace(card, sizes.mine)
    }
}

/** A card lying sideways, as the seats at the sides of the table lie theirs. */
private const val QUARTER = 90f

/** How large a played card grows before it settles. The web app uses two and a half. */
private const val FLOURISH_SWELL = 2.4f

package game.vinto.app.game

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import game.vinto.app.Hinge
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The table changing shape, and everything on it travelling to where it now is.
 *
 * A fold opened or closed, a tablet turned, a desktop window dragged across the line between a
 * phone and a tablet: in the next frame every seat was somewhere else and the controls were on
 * another edge, and a player who had been reading their hand had to find it again. The owner's
 * request, 2026-10-12: "adding animations like our names slowly moving into new position, plus
 * some good animation of moving controls part".
 *
 * **Where it was, then where it is** — the layout is final at once, and what moves is only the
 * picture: each piece that [glides] is drawn from where it stood, as a share of the old table laid
 * over the new one, back to its own place. A share rather than a point, because a fold changes the
 * screen as well as the table, and the Duo's outer screen is not the left half of its inner one.
 * The hands and the piles arrive first; the plates follow slowly, turning as they go where the name
 * changes direction, and settle beside their cards; the rail swings round the felt's corner to its
 * new edge rather than crossing the cards. `TheTableGlidesTest` holds each of those.
 *
 * **Only for a change of kind** ([TableLayout.shape]). A window resized by a few points is the same
 * table with different numbers, and gliding on every frame of a drag would be a table that never
 * stands still. And never under reduced motion, when the new table is simply there.
 *
 * One per table, from [TableScreen]; the pieces find it through [LocalReshape].
 */
class Reshape {
    private var shape: TableShape? = null

    /** Which change this is, and the change each piece last answered. */
    private var change = 0
    private val answered = HashMap<String, Int>()

    /** Where each piece stands now, and where each stood before the last change. */
    private val now = HashMap<String, Spot>()
    private var was: Map<String, Spot> = emptyMap()

    /** The table everything is measured against: a share of it is what survives a new screen. */
    internal var table: LayoutCoordinates? = null

    /** How large the table was before the last change: whether the new one is that, opened ([pageOf]). */
    internal var wasSize: Size = Size.Zero
        private set

    /** The table's arrangement now, for where its felt lies across it ([pageOf]). */
    internal var layout: TableLayout? = null

    /**
     * The hinge, live, and where it stood when the table changed shape: a glide that starts with the
     * phone half open follows the hinge from there to flat ([Hinge]).
     */
    internal var hinge: State<Hinge?>? = null
    internal var openedAt: Float? = null
        private set

    /**
     * The table's kind, every composition. Run as a side effect, which lands after the composition
     * that asked for the new layout and before that layout is placed — so what [now] holds at that
     * moment is the old table, whole.
     */
    fun shapeIs(next: TableShape, still: Boolean) {
        val before = shape
        shape = next
        if (before != null && before != next && !still) {
            was = HashMap(now)
            wasSize = table?.takeIf { it.isAttached }?.size?.toSize() ?: Size.Zero
            // Only an opening is carried by the hinge: from where the screen changed over to flat.
            // A table folding into a book or a laptop is going the other way, with no flat to
            // reach, and following the hinge held it still until the hand rested; it glides on
            // the clock instead.
            openedAt = hinge?.value?.takeIf { it.partiallyOpen && !next.posture.folded }?.degrees
            change++
        }
    }

    /** Where [key] starts from, once per change, or null when there is nothing to answer. */
    internal fun startFor(key: String): Spot? {
        if (answered[key] == change) return null
        answered[key] = change
        return was[key]
    }

    internal fun record(key: String, spot: Spot) {
        now[key] = spot
    }
}

/**
 * Where a piece stood — as a share of the table, so that a new screen can lay it over itself — and
 * how far it was turned.
 */
internal class Spot(val share: Rect, val turn: Float)

/** The table a piece glides on, if it is on one. */
val LocalReshape = staticCompositionLocalOf<Reshape?> { null }

/**
 * How each kind of piece travels.
 *
 * The cards are the thing the player was looking at, so they go first and briskly. The plates
 * follow, starting a moment later and slowing all the way in, which is what "names slowly moving
 * into new position" looks like — a name settling beside the cards it belongs to rather than
 * racing them there. The rail swings round the corner ([arc]): it is the one piece that changes
 * edge, and straight across would carry it over the cards.
 */
enum class Glide(val millis: Int, val delay: Int, val easing: Easing, val arc: Boolean) {
    CARDS(millis = 550, delay = 0, easing = FastOutSlowInEasing, arc = false),
    PLATE(millis = 1100, delay = 150, easing = LinearOutSlowInEasing, arc = false),
    RAIL(millis = 650, delay = 0, easing = FastOutSlowInEasing, arc = true),
}

/**
 * This piece of the table glides to its new place when the table changes shape. [key] names it
 * across the change — the same seat's plate in either arrangement — and [turn] is how far it is
 * turned, so a plate whose name changes direction turns on the way.
 */
fun Modifier.glides(reshape: Reshape?, key: String, glide: Glide, turn: Float = 0f): Modifier =
    if (reshape == null) this else this then GlideElement(reshape, key, glide, turn)

private data class GlideElement(
    val reshape: Reshape,
    val key: String,
    val glide: Glide,
    val turn: Float,
) : ModifierNodeElement<GlideNode>() {
    override fun create() = GlideNode(reshape, key, glide, turn)

    override fun update(node: GlideNode) {
        node.reshape = reshape
        node.key = key
        node.glide = glide
        node.turn = turn
    }
}

private class GlideNode(
    var reshape: Reshape,
    var key: String,
    var glide: Glide,
    var turn: Float,
) : Modifier.Node(), LayoutModifierNode {

    /** How far along the glide is, 1 when it is home. Read by the layer alone, so a frame costs no layout. */
    private val progress = mutableFloatStateOf(1f)

    /** Where it starts, as an offset from home, and the point the path bends round. */
    private var from = Offset.Zero
    private var bend = Offset.Zero
    private var scaleFrom = 1f
    private var turnFrom = 0f
    private var running: Job? = null

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        val density: Density = this
        val direction = layoutDirection
        return layout(placeable.width, placeable.height) {
            coordinates?.let { arrive(it, density, direction) }
            placeable.placeWithLayer(0, 0) {
                val t = glide.easing.transform(progress.floatValue)
                if (t < 1f) {
                    val u = 1f - t
                    // A quadratic curve from the start, round the bend, to home.
                    val at = from * (u * u) + bend * (2 * u * t)
                    translationX = at.x
                    translationY = at.y
                    val scale = scaleFrom + (1f - scaleFrom) * t
                    scaleX = scale
                    scaleY = scale
                    rotationZ = turnFrom * u
                }
            }
        }
    }

    /** Placed: note where, and if the table has just changed shape, start from where it was. */
    private fun arrive(here: LayoutCoordinates, density: Density, direction: LayoutDirection) {
        val table = reshape.table?.takeIf { it.isAttached } ?: return
        if (!here.isAttached) return
        val box = table.size.toSize()
        if (box.width <= 0f || box.height <= 0f) return
        val home = Rect(table.localPositionOf(here, Offset.Zero), here.size.toSize())
        val start = reshape.startFor(key)
        val share = Rect(
            left = home.left / box.width,
            top = home.top / box.height,
            right = home.right / box.width,
            bottom = home.bottom / box.height,
        )
        reshape.record(key, Spot(share, turn))
        if (start == null || home.width <= 0f || home.height <= 0f) return
        // Where the felt lies across the new table, in its pixels: on a table on its side, the
        // felt and not the rail beside it; in a book, the felt's own page.
        val layout = reshape.layout
        val felt = with(density) {
            layout?.feltSpan(direction)?.let { it.left.toPx()..(it.left + it.width).toPx() }
        }
        val page = pageOf(reshape.wasSize, box, felt, book = layout?.posture == Posture.BOOK)
        if (!startFrom(start, home, page)) return

        progress.floatValue = 0f
        running?.cancel()
        running = coroutineScope.launch { travel() }
    }

    /** Where the glide starts, as an offset from [home], the old table laid over [frame]; false if home. */
    private fun startFrom(start: Spot, home: Rect, frame: Rect): Boolean {
        val was = Rect(
            frame.left + start.share.left * frame.width,
            frame.top + start.share.top * frame.height,
            frame.left + start.share.right * frame.width,
            frame.top + start.share.bottom * frame.height,
        )
        from = was.center - home.center
        // Straight, the bend is halfway and the curve is a line. Round a corner, it is the corner:
        // a rail coming up from under the felt runs along the bottom first and then up the side,
        // and one going down runs down the side first.
        bend = when {
            !glide.arc -> from * HALF
            from.y > 0f -> Offset(0f, from.y)
            else -> Offset(from.x, 0f)
        }
        scaleFrom = sqrt(was.width * was.height / (home.width * home.height)).coerceIn(SMALLEST, LARGEST)
        turnFrom = start.turn - turn
        return from.getDistance() >= STILL || turnFrom != 0f
    }

    /**
     * The glide itself: with the hand that opens the phone while there is a hinge to follow
     * ([followTheHinge]), and on the clock for the rest of the way.
     */
    private suspend fun travel() {
        followTheHinge()
        val left = 1f - progress.floatValue
        animate(
            progress.floatValue,
            1f,
            animationSpec = tween(
                durationMillis = (glide.millis * left).toInt(),
                delayMillis = if (left < 1f) 0 else glide.delay,
                easing = LinearEasing,
            ),
        ) { value, _ ->
            progress.floatValue = value
        }
    }

    /**
     * From the angle the table changed shape at, to flat, as the hinge moves. The plates lag the
     * cards ([HINGE_LAG]) as they do on the clock. Until the hinge lies flat, goes, or rests a
     * moment — a book held half open is a pose, and the table finishes on its own rather than
     * standing half spread. Nothing at all where the change did not come with a hinge half open.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun followTheHinge() {
        val hinge = reshape.hinge ?: return
        val openedAt = reshape.openedAt?.takeIf { it < FLAT } ?: return
        snapshotFlow { hinge.value }
            .mapLatest { now ->
                if (now != null && now.partiallyOpen) {
                    val opened = ((now.degrees - openedAt) / (FLAT - openedAt)).coerceIn(0f, 1f)
                    progress.floatValue = if (glide == Glide.PLATE) opened.pow(HINGE_LAG) else opened
                    delay(RESTING_MS)
                }
            }
            .first()
    }

    private companion object {
        const val HALF = 0.5f

        /** Flat, in the hinge's degrees. */
        const val FLAT = 180f

        /** How much the plates lag the cards while the hinge carries them: the name after its cards. */
        const val HINGE_LAG = 1.6f

        /** A hinge still for this long is resting, and the table finishes on its own. */
        const val RESTING_MS = 600L

        /** A piece that changes size does so on the way, within reason: past this it reads as a zoom. */
        const val SMALLEST = 0.6f
        const val LARGEST = 1.6f

        /** Under a pixel is not a move. */
        const val STILL = 1f
    }
}

/**
 * Where the old table lies over the new one: the right-hand page, when the new table is the old one
 * opened, and the whole of it otherwise.
 *
 * The iPhone Duo opens like a book into two pages the closed screen's shape (466x678 closed, 951x669
 * open: two pages of about 475x669), so a table that started out on the whole new screen would have
 * been stretched sideways. Laid on the page the player's eyes were on, it starts at its own size and
 * spreads across both. Told by shape alone — the new table about twice as wide for its height as the
 * old one — so it holds for any phone that opens like that, and folding back is the whole screen.
 *
 * The right-hand half of the [felt], where it lies across only part of the new table (from its left
 * to its right, in the table's pixels): an open fold stands its rail down the right, and laid over
 * the screen's right half, a third of the closed table started under the rail and the opening read
 * as a table sliding in from the edge.
 *
 * **The whole of the felt in a [book]**, whose felt is a page of its own and already the closed
 * screen's shape: the closed table lands on it at its own size, and only moves to its seats there.
 * Half of it would have started the table squeezed into a quarter of the screen beside the crease.
 */
internal fun pageOf(
    was: Size,
    now: Size,
    felt: ClosedFloatingPointRange<Float>? = null,
    book: Boolean = false,
): Rect {
    val whole = Rect(0f, 0f, now.width, now.height)
    if (was.width <= 0f || was.height <= 0f || now.height <= 0f) return whole
    val widened = now.width / now.height / (was.width / was.height)
    if (widened !in PAGE_FROM..PAGE_TO) return whole
    val left = felt?.start ?: 0f
    val right = felt?.endInclusive ?: now.width
    return Rect(if (book) left else (left + right) / 2, 0f, right, now.height)
}

/** How much wider for its height the new table must be to be the old one opened like a book. */
private const val PAGE_FROM = 1.6f
private const val PAGE_TO = 2.6f

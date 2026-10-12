package game.vinto.app.game

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
        return layout(placeable.width, placeable.height) {
            coordinates?.let(::arrive)
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
    private fun arrive(here: LayoutCoordinates) {
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

        val was = Rect(
            start.share.left * box.width,
            start.share.top * box.height,
            start.share.right * box.width,
            start.share.bottom * box.height,
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
        if (from.getDistance() < STILL && turnFrom == 0f) return

        progress.floatValue = 0f
        running?.cancel()
        running = coroutineScope.launch {
            animate(0f, 1f, animationSpec = tween(glide.millis, glide.delay, LinearEasing)) { value, _ ->
                progress.floatValue = value
            }
        }
    }

    private companion object {
        const val HALF = 0.5f

        /** A piece that changes size does so on the way, within reason: past this it reads as a zoom. */
        const val SMALLEST = 0.6f
        const val LARGEST = 1.6f

        /** Under a pixel is not a move. */
        const val STILL = 1f
    }
}

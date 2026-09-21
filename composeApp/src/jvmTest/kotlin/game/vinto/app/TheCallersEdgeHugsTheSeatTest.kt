package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.game.seatingFor
import game.vinto.app.theme.Slate
import game.vinto.app.theme.VintoTheme
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The caller's edge is drawn round the **seat**, not round the room the seat was given.
 *
 * Reported from a store screenshot: *"why is Dune border so big now?"* — a gold rectangle
 * enclosing a plate, two cards and a hand's worth of bare felt beside them. The seat had not
 * grown; the border had. A side seat is laid out inside a share of the middle row, because
 * `HandLine` decides how many columns it may use from the width it is told it has, and the
 * border had been put on that share instead of on the column inside it.
 *
 * **Nothing in the layout tree says so**, which is why the edge itself is read out of the
 * pixels: a side seat aligns its cards and its plate against its own rim either way, so every
 * one of them sits at exactly the same coordinates in both versions and every bounds
 * assertion passes. The only thing that moved was a drawn line, and a golden can only say
 * that *something* moved.
 *
 * So: the edge is measured from the render, the seat is measured from the composition, and
 * the first may exceed the second only by the padding that is there to keep them apart.
 */
@OptIn(ExperimentalTestApi::class)
class TheCallersEdgeHugsTheSeatTest {

    @Test
    fun aSideCallersEdgeIsNoWiderThanTheSeatInsideIt() {
        val view = sideCallerHoldingTwo()
        val caller = view.players.first { it.id == view.vintoCallerId }

        val seat = widthOfEverythingDrawnFor(caller.name, view)
        val edge = edgeWidth(render(view))

        // The edge sits [CallerRing] outside [CallerPad] outside the seat, on both rims.
        val allowed = seat + 2 * (CALLER_PAD + CALLER_RING) + SLACK
        assertTrue(
            edge <= allowed,
            "${caller.name} called Vinto holding $HELD cards and the edge round them is " +
                "${edge}px wide, over a seat that is only ${seat}px — the border is drawn " +
                "round the seat's share of the middle row rather than round the seat",
        )
    }

    /** A table in the final round whose caller sits at the side and holds two cards. */
    private fun sideCallerHoldingTwo(): PlayerView {
        val whole = teachingSession().view.value
        val mine = whole.players.first { it.id == whole.viewerId }
        val caller = seatingFor(whole.players, mine).right
            ?: error("four players, so somebody is in the right-hand chair")

        return whole.copy(
            vintoCallerId = caller.id,
            players = whole.players.map { seat ->
                if (seat.id != caller.id) {
                    seat
                } else {
                    // Two, which is what made the report a report: at five the edge was near
                    // enough the seat's own width that nobody could see it was not it.
                    seat.copy(isVintoCaller = true, cards = seat.cards.take(HELD))
                }
            },
        )
    }

    /**
     * The widest thing the seat actually draws: its cards, and the plate carrying its name.
     *
     * The plate is found as the smallest pressable thing around the name — a seat plate is a
     * target, because a Nine looks at one and a Jack swaps into one, so it is the one part of
     * a seat with a click action of its own.
     */
    private fun widthOfEverythingDrawnFor(name: String, view: PlayerView): Int {
        var widest = 0f
        runComposeUiTest {
            show(view)

            val cards = describedNodes()
                .filter { (label, _) -> label.startsWith("$name,") && label.contains(", card ") }
                .map { (_, box) -> box }
            assertEquals(HELD, cards.size, "$name should be holding $HELD cards on the felt")

            val plate = onAllNodes(hasClickAction()).fetchSemanticsNodes()
                .map { it.boundsInRoot }
                .filter { box -> textBounds(name).any { box.contains(it.center) } }
                .minByOrNull { it.width }
                ?: error("$name's plate is drawn but is not a target")

            widest = (cards + plate).maxOf { it.width }
        }
        return widest.toInt()
    }

    private fun ComposeUiTest.show(view: PlayerView) {
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W.dp, PHONE_H.dp)) {
                    TableScreen(
                        state = TableState(view, tableFor(view), null, emptyList(), 1),
                        layout = TableLayout.forScreen(PHONE_H.dp),
                        onMove = {},
                        onHelp = {},
                        onSettings = {},
                    )
                }
            }
        }
        waitForIdle()
    }

    /** Everything on the felt that a screen reader would name, with where it is. */
    private fun ComposeUiTest.describedNodes(): List<Pair<String, Rect>> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .fetchSemanticsNodes()
            .mapNotNull { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription)
                    ?.firstOrNull()
                    ?.let { it to node.boundsInRoot }
            }

    private fun ComposeUiTest.textBounds(text: String): List<Rect> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .filter { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
            }
            .map { it.boundsInRoot }

    private fun render(view: PlayerView): PixelMap =
        ImageComposeScene(width = PHONE_W, height = PHONE_H, density = Density(1f)) {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W.dp, PHONE_H.dp)) {
                    TableScreen(
                        state = TableState(view, tableFor(view), null, emptyList(), 1),
                        layout = TableLayout.forScreen(PHONE_H.dp),
                        onMove = {},
                        onHelp = {},
                        onSettings = {},
                    )
                }
            }
        }.use { scene ->
            // The portraits and the card art arrive asynchronously, and a line drawn over a
            // hole is still a line — but the edge is only findable once the felt beneath it is.
            var image = scene.render(0L)
            repeat(WARM_FRAMES) {
                Thread.sleep(WARM_SLEEP_MS)
                image = scene.render((it + 1) * WARM_STEP_NANOS)
            }
            image.toComposeImageBitmap().toPixelMap()
        }

    /**
     * How far apart the edge's two uprights are, read off the render.
     *
     * A gold **column** rather than a gold pixel: the wordmark, the crown badge and the orange
     * in the card art are all near enough gold to match a pixel at a time, and none of them is
     * a line as tall as a seat.
     */
    private fun edgeWidth(pixels: PixelMap): Int {
        val uprights = (0 until pixels.width).filter { x -> tallestGoldRun(pixels, x) >= UPRIGHT }
        // Antialiasing spreads a 2dp line over three columns, so group what is adjacent.
        val strokes = uprights.fold(mutableListOf<MutableList<Int>>()) { runs, x ->
            val last = runs.lastOrNull()
            if (last != null && x - last.last() <= 1) last.add(x) else runs.add(mutableListOf(x))
            runs
        }
        assertEquals(
            2,
            strokes.size,
            "one seat called Vinto, so the felt should carry one edge with two uprights; " +
                "found ${strokes.size} at ${strokes.map { it.first()..it.last() }}",
        )
        return strokes.last().last() - strokes.first().first() + 1
    }

    /** The tallest unbroken run of gold down column [x]. */
    private fun tallestGoldRun(pixels: PixelMap, x: Int): Int {
        var run = 0
        var tallest = 0
        for (y in 0 until pixels.height) {
            run = if (pixels[x, y].near(Slate.gold)) run + 1 else 0
            tallest = max(tallest, run)
        }
        return tallest
    }

    private fun Color.near(other: Color): Boolean =
        max(max(abs(red - other.red), abs(green - other.green)), abs(blue - other.blue)) *
            CHANNEL_SCALE <= CHANNEL_TOLERANCE

    private companion object {
        /** Two cards, as in the screenshot the report came from. */
        const val HELD = 2

        const val PHONE_W = 411
        const val PHONE_H = 740

        /** `CallerPad` and `CallerRing`, which are 3.dp and 2.dp and so pixels at density 1. */
        const val CALLER_PAD = 3
        const val CALLER_RING = 2

        /** A rounded corner and an antialiased line, in pixels. */
        const val SLACK = 3

        /** Taller than any gold glyph, badge or card accent; shorter than the shortest seat. */
        const val UPRIGHT = 60

        const val CHANNEL_SCALE = 255f

        /** The line is flat gold; this is rounding, not a shade. */
        const val CHANNEL_TOLERANCE = 12f

        const val WARM_FRAMES = 20
        const val WARM_SLEEP_MS = 50L
        const val WARM_STEP_NANOS = 16_000_000L
    }
}

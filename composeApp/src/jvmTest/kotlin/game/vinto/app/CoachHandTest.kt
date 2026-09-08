package game.vinto.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import game.vinto.app.game.Pointer
import game.vinto.app.game.Stage
import game.vinto.client.Target
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The coach's hand, as a player on a phone sees it.
 *
 * Two things were reported about it in the lesson, and they are the same complaint twice: the
 * hand is hard to find. It is white, it is often over a white card, and what was supposed to
 * keep it off that ground — its dark outline — was measured in **raw pixels** rather than in
 * points, so it thinned by exactly the screen's density: chunky on the desktop the drawing
 * was tuned on, a hairline on a 3× phone. And the bob that was meant to draw the eye to it was
 * four points, which at arm's length is not a movement, it is a stillness.
 *
 * Both are measured here rather than described: the hand is drawn on white at two densities
 * and its ink weighed, and it is watched over a second and a half to see how far it actually
 * travels.
 */
@OptIn(ExperimentalTestApi::class)
class CoachHandTest {

    /**
     * The hand is one drawing, so it must weigh the same at every density.
     *
     * A shape whose outline is in pixels and whose body is in points is a different drawing on
     * every phone — which is the defect, stated as arithmetic. The measure is the ink inside
     * the hand's own bounding box, so it says nothing about how big the hand is and everything
     * about how much of it is dark.
     */
    @Test
    fun theHandKeepsItsWeightOnADenseScreen() {
        val dense = inkShare(density = 4f)
        val plain = inkShare(density = 2f)

        assertTrue(
            abs(dense - plain) <= SAME,
            "the coach's hand is ${asPercent(dense)} ink on a 4x screen and ${asPercent(plain)} on a 2x one — " +
                "its outline is in pixels rather than points, so it thins as the screen sharpens",
        )
    }

    /** And there has to be enough of it to see at all: a wireframe on a white card is not a hand. */
    @Test
    fun theHandIsMoreThanAnOutlineOnAWhiteCard() {
        val share = inkShare(density = 3f)

        assertTrue(
            share >= LEAST_INK,
            "only ${asPercent(share)} of the coach's hand is ink on a white card, which reads as an " +
                "outline of nothing rather than as a hand",
        )
    }

    /**
     * It moves, and by enough to catch an eye that is not looking at it.
     *
     * The lesson points at a card on a table full of cards; the movement is how the hand is
     * found in the first place. Four points is under a card's corner radius.
     */
    @Test
    fun theHandMovesFarEnoughToBeFound() {
        val travel = travelDp(reduced = false)

        assertTrue(
            travel >= ENOUGH_TRAVEL,
            "the coach's hand travels $travel dp, which is too small a movement to find it by",
        )
    }

    /** Unless motion is off, where the rule is no movement and the same information. */
    @Test
    fun theHandHoldsStillWhenMotionIsOff() {
        val travel = travelDp(reduced = true)

        assertTrue(travel == 0f, "the coach's hand still bobs $travel dp with motion turned off")
    }

    // ------------------------------------------------------------------ the measuring

    /** How much of the hand's own bounding box is ink, drawn on white and held still. */
    private fun inkShare(density: Float): Double {
        var share = 0.0
        runComposeUiTest {
            setContent { Felt(density, reduced = true) }
            waitForIdle()
            val box = handBox(density) ?: error("the coach's hand drew nothing on the white card")
            share = box.ink.toDouble() / (box.wide * box.deep)
        }
        return share
    }

    /** How far the hand travels over a second and a half, in points. */
    private fun travelDp(reduced: Boolean): Float {
        var travel = 0f
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent { Felt(WATCHED_AT, reduced = reduced) }
            mainClock.advanceTimeByFrame()

            val tops = mutableListOf<Int>()
            repeat(SAMPLES) {
                mainClock.advanceTimeBy(EVERY_MS)
                handBox(WATCHED_AT)?.let { tops += it.top }
            }
            travel = (tops.max() - tops.min()) / WATCHED_AT
        }
        return travel
    }

    /** The white card, with the hand pointing down at it from above. */
    @Composable
    private fun Felt(density: Float, reduced: Boolean) {
        CompositionLocalProvider(
            LocalDensity provides Density(density),
            LocalReducedMotion provides reduced,
        ) {
            val stage = remember { Stage() }
            Box(
                modifier = Modifier
                    .size(Side)
                    .background(Color.White)
                    .onGloballyPositioned { stage.setOrigin(it) },
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(CardSide)
                        .onGloballyPositioned { stage.mark(TARGET, it) },
                )
                Pointer(stage = stage, target = Target.Furniture(TARGET))
            }
        }
    }

    /** Where the hand is on the white square, and how much of that rectangle it inks. */
    private fun ComposeUiTest.handBox(density: Float): HandBox? {
        val map = onRoot().captureToImage().toPixelMap()
        val side = (Side.value * density).toInt().coerceAtMost(minOf(map.width, map.height))
        return handBox(map, side)
    }

    private fun handBox(map: PixelMap, side: Int): HandBox? {
        val ink = buildList {
            for (y in 0 until side) {
                for (x in 0 until side) {
                    if (map[x, y].isInk()) add(IntOffset(x, y))
                }
            }
        }
        if (ink.isEmpty()) return null
        val top = ink.minOf { it.y }
        return HandBox(
            top = top,
            wide = ink.maxOf { it.x } - ink.minOf { it.x } + 1,
            deep = ink.maxOf { it.y } - top + 1,
            ink = ink.size,
        )
    }

    /**
     * Dark enough to read as a mark on white — SC 1.4.11's 3:1, which is what a shape has to
     * clear to be information rather than decoration.
     */
    private fun Color.isInk(): Boolean = Wcag.contrast(this, Color.White) >= Wcag.UI

    private data class HandBox(val top: Int, val wide: Int, val deep: Int, val ink: Int)

    private fun asPercent(share: Double): String = "%.0f%%".format(share * PERCENT)

    private companion object {
        const val TARGET = "card"
        val Side: Dp = 160.dp
        val CardSide: Dp = 40.dp

        /** Two drawings of one shape agree to within a rounding of their edges. */
        const val SAME = 0.05

        /** Under this and the hand is a wireframe: an outline round a card-coloured hole. */
        const val LEAST_INK = 0.22

        /** A movement smaller than this is one nobody notices they have seen. */
        const val ENOUGH_TRAVEL = 8f

        const val WATCHED_AT = 3f
        const val SAMPLES = 30
        const val EVERY_MS = 50L
        const val PERCENT = 100
    }
}

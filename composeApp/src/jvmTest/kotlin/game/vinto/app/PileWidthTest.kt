package game.vinto.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.EmptySlot
import game.vinto.app.game.TableLayout
import game.vinto.app.theme.VintoTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * An empty place on the table is drawn exactly as wide as the card that lands in it.
 *
 * Every card's *footprint* is padded out to a 44dp thumb, so that nothing on the table shifts
 * when a slot fills or empties. But a card drawn smaller than a thumb — which is every card
 * outside your own hand — is then narrower than its own box, and the outline of an empty slot
 * was drawn on the box. So the draw, discard and toss-in places stood visibly wider than the
 * cards that go in them: three cards of the wrong size, on a table whose subject is cards.
 *
 * Measured in pixels rather than semantics, because an outline is not a node: the slot is
 * drawn on a known ground and the drawn ink is looked for column by column.
 */
@OptIn(ExperimentalTestApi::class)
class PileWidthTest {

    @Test
    fun anEmptySlotIsAsWideAsTheCardThatFillsIt() = runComposeUiTest {
        val card = TableLayout.forScreen(PHONE_H).sizes.theirs
        var density = 1f

        setContent {
            VintoTheme {
                density = androidx.compose.ui.platform.LocalDensity.current.density
                Box(modifier = Modifier.background(Ground).padding(PAD)) {
                    EmptySlot(card, "")
                }
            }
        }
        waitForIdle()

        val pixels = onRoot().captureToImage().toPixelMap()
        val inked = (0 until pixels.width).filter { x ->
            (0 until pixels.height).any { y -> pixels[x, y] != Ground }
        }
        assertTrue(inked.isNotEmpty(), "the slot drew nothing at all")

        val drawn = (inked.last() - inked.first() + 1) / density
        assertTrue(
            abs(drawn - card.width.value) <= SLACK,
            "an empty slot is ${drawn.roundToInt()}dp wide where its card is " +
                "${card.width.value.roundToInt()}dp",
        )
    }

    private companion object {
        val Ground = Color(0xFF123456)
        val PAD = 12.dp
        const val SLACK = 2f
        val PHONE_H = 740.dp
    }
}

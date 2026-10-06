package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.CardFace
import game.vinto.app.game.CardScale
import game.vinto.app.theme.VintoTheme
import game.vinto.engine.CardView
import game.vinto.shapes.Card
import game.vinto.shapes.Rank
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A card turned over spins to show its face, and does not when the reader has asked for less
 * motion.
 *
 * The flip is a turn about the card's upright axis, the length of a breath, on every peek and every
 * reveal: the most frequent movement in the game. It ignored Reduce Motion while the flights and
 * the coach's hand honoured it, so a player who had asked iOS for less motion still watched every
 * card spin. Measured the way `ThinkingCloudTest` measures its cloud: the same card photographed a
 * moment into the turn and once it has finished. Turning in one step, the two are the same.
 */
@OptIn(ExperimentalTestApi::class)
class CardTurnTest {

    @Test
    fun aCardTurnsOverWhenMotionIsOn() {
        assertTrue(midTurn(reduced = false), "a card turned over with motion on did not visibly turn")
    }

    @Test
    fun aCardTurnsInOneStepWhenMotionIsOff() {
        assertTrue(!midTurn(reduced = true), "a card still spins with motion turned off")
    }

    /** Whether the card a moment into its turn looks different from the card once it has turned. */
    private fun midTurn(reduced: Boolean): Boolean {
        var different = false
        runComposeUiTest {
            mainClock.autoAdvance = false
            val card = mutableStateOf<CardView>(CardView.Hidden)
            setContent {
                CompositionLocalProvider(LocalReducedMotion provides reduced) {
                    VintoTheme(dark = false) {
                        Box(modifier = Modifier.size(BOX).testTag(TAG)) {
                            CardFace(card = card.value, scale = SCALE)
                        }
                    }
                }
            }
            mainClock.advanceTimeByFrame()

            card.value = CardView.Visible(Card(id = "c", rank = Rank.SEVEN, value = 7, played = false))
            mainClock.advanceTimeBy(EARLY_MS)
            val early = onNodeWithTag(TAG).captureToImage().toPixelMap().pixels()
            mainClock.advanceTimeBy(SETTLED_MS)
            val settled = onNodeWithTag(TAG).captureToImage().toPixelMap().pixels()
            different = early != settled
        }
        return different
    }

    private fun PixelMap.pixels(): List<Int> = buildList {
        for (y in 0 until height) {
            for (x in 0 until width) add(this@pixels[x, y].hashCode())
        }
    }

    private companion object {
        const val TAG = "turning"
        val SCALE = CardScale(48.dp, 67.dp)
        val BOX = 120.dp

        /** A third of the way into the turn, when a spinning card is edge-on and narrow. */
        const val EARLY_MS = 120L

        /** Well past the end of the turn. */
        const val SETTLED_MS = 1_500L
    }
}

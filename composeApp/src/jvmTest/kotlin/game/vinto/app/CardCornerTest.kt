package game.vinto.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import game.vinto.app.art.Res
import game.vinto.app.art.card_4
import game.vinto.app.game.CardFace
import game.vinto.app.game.CardScale
import game.vinto.app.theme.VintoTheme
import game.vinto.engine.CardView
import game.vinto.shapes.Card
import game.vinto.shapes.Rank
import org.jetbrains.compose.resources.painterResource
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The frame a card is drawn in never cuts the card.
 *
 * **Every card in the deck rounds its own corners.** `card_*.xml` is an 825x1125 viewport whose
 * first path is a rounded rectangle of radius 44 — five and a third per cent of the width — and
 * the dark outline just inside it is drawn to match. So the art arrives already the shape a card
 * is, at whatever size it is drawn.
 *
 * `CardFace` then rounded it a second time, to a flat `TableSizes.Corner` of 8 points. That is
 * not the same curve and it does not scale: on a phone's 48-point card the art's own corner is
 * 2.6 points and the clip is 8, so the clip is three times the rounder and takes a bite out of
 * every corner — through the card's own outline and into its face. Reported as cards looking
 * truncated on the felt.
 *
 * Measured against the art itself rather than against a number: whatever the deck draws, the
 * frame has to keep. That way this stays true if the masters are ever redrawn with a different
 * corner, which is exactly the change that would otherwise reintroduce it silently.
 */
@OptIn(ExperimentalTestApi::class)
class CardCornerTest {

    @Test
    fun theFrameNeverCutsTheCard() {
        for (scale in SIZES) {
            val drawn = pixels(scale) {
                CardFace(
                    card = CardView.Visible(Card(id = "c", rank = Rank.FOUR, value = 4, played = false)),
                    scale = scale,
                )
            }
            val art = pixels(scale) {
                Image(
                    painter = painterResource(Res.drawable.card_4),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Every pixel the deck draws has to survive the frame. The other direction is fine:
            // a ring or a shadow may add ink, and only losing it is a card that looks cut.
            val lost = art.indices.count { art[it] && !drawn[it] }
            assertTrue(
                lost <= art.count { it } / TOLERANCE,
                "a ${scale.width}x${scale.height} card loses $lost of ${art.count { it }} " +
                    "drawn pixels to its own frame — the corners are being cut",
            )
        }
    }

    /** Which pixels are inked, for a card drawn alone at [scale]. */
    private fun pixels(scale: CardScale, content: @Composable () -> Unit): List<Boolean> {
        var map = emptyList<Boolean>()
        runComposeUiTest {
            setContent {
                // Six device pixels to the point, so a corner two or three points across is
                // measured in tens of pixels rather than in antialiasing.
                CompositionLocalProvider(LocalDensity provides Density(DENSITY)) {
                    VintoTheme(dark = false) {
                        Box(modifier = Modifier.size(scale.width, scale.height)) { content() }
                    }
                }
            }
            waitForIdle()
            val shot = onRoot().captureToImage().toPixelMap()
            map = buildList {
                for (y in 0 until shot.height) {
                    for (x in 0 until shot.width) add(shot[x, y].alpha > HALF)
                }
            }
        }
        return map
    }

    private companion object {
        /** The three sizes a card is drawn at on a phone, and the desktop's largest. */
        val SIZES = listOf(
            CardScale(36.dp, 50.dp),
            CardScale(48.dp, 67.dp),
            CardScale(56.dp, 78.dp),
            CardScale(100.dp, 139.dp),
        )
        const val DENSITY = 6f
        const val HALF = 0.5f

        /** A fringe of antialiasing along the art's own edge is not a cut corner. */
        const val TOLERANCE = 500
    }
}

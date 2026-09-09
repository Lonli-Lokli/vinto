package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.app.share.OffscreenLayer
import game.vinto.app.share.ShareCardWidth
import game.vinto.app.share.ShareQrCard
import game.vinto.app.share.VintoQr
import game.vinto.app.share.captureInto
import game.vinto.app.share.rememberShareLayer
import game.vinto.app.share.toPngBytes
import game.vinto.app.theme.VintoTheme
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The half of a share that has to be a picture.
 *
 * A share button is the one control in this app whose result nobody in the room can see: it hands
 * bytes to a platform, and whether those bytes are a scannable code or a blank rectangle is
 * decided somewhere no screen shows. The off-screen capture makes that worse — the card is drawn
 * at zero alpha, so a card that rendered as nothing at all would look exactly like one that
 * rendered correctly, on every device, forever.
 *
 * So this measures the bytes. That they are a PNG at all, that the picture has the shape of the
 * card rather than of a collapsed layout, and that both of the chip's two colours are actually in
 * it — which is what says a code was drawn and not just its cream tile.
 */
@OptIn(ExperimentalTestApi::class)
class ShareQrCardTest {

    @Test
    fun theCardThatTravelsIsARealPngWithARealCodeInIt() {
        val png = capture()

        assertTrue(png.size > SMALLEST_BELIEVABLE, "the shared picture is ${png.size} bytes; it is not an image")
        assertEquals(
            PNG_SIGNATURE.toList(),
            png.take(PNG_SIGNATURE.size),
            "the bytes handed to the share sheet are not a PNG",
        )

        val image = ImageIO.read(ByteArrayInputStream(png))
        assertTrue(image != null, "the PNG does not decode")

        // Square-ish and the width the card was laid out at. The failure this catches is the one
        // `OffscreenLayer` exists to prevent: a zero-height capture from a zero-sized parent,
        // which throws on some targets and yields a sliver on others.
        assertEquals(ShareCardWidth.value.toInt(), image.width, "the card was not captured at its own width")
        assertTrue(image.height > image.width / 2, "the card came out a sliver: ${image.width}x${image.height}")

        val colours = buildSet {
            for (y in 0 until image.height) for (x in 0 until image.width) add(image.getRGB(x, y))
        }
        assertTrue(VintoQr.GROUND.toArgb() in colours, "the chip's cream tile is missing from the picture")
        assertTrue(VintoQr.MODULE.toArgb() in colours, "no module colour in the picture: nothing was drawn to scan")
    }

    /** Renders the card the way `CodeToShare` does, and asks the layer for its bytes. */
    private fun capture(): ByteArray {
        var layer: GraphicsLayer? = null
        var bytes: ByteArray? = null
        runComposeUiTest {
            setContent {
                VintoTheme(dark = false) {
                    val recorded = rememberShareLayer()
                    val logo = VintoQr.ringedMark()
                    layer = recorded
                    Box {
                        OffscreenLayer(width = ShareCardWidth) {
                            ShareQrCard(
                                url = "https://vinto.kupalinka.app/r/7KQ2MP",
                                caption = "7KQ2MP",
                                logo = logo,
                                modifier = Modifier.alpha(0f).captureInto(recorded),
                            )
                        }
                    }
                }
            }
            waitForIdle()
            runBlocking { bytes = requireNotNull(layer).toPngBytes() }
        }
        return requireNotNull(bytes) { "nothing was captured" }
    }

    private companion object {
        /** The eight bytes every PNG begins with. */
        val PNG_SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)

        /** Below this it is a header and no picture — a blank capture still costs more. */
        const val SMALLEST_BELIEVABLE = 500
    }
}

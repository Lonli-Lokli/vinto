package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.share.MIN_SCAN_CONTRAST
import game.vinto.app.share.QrChip
import game.vinto.app.share.VintoQr
import game.vinto.app.theme.Brand
import game.vinto.app.theme.Felt
import game.vinto.app.theme.VintoTheme
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The scan-to-play chip: dark enough to read, and it actually paints.
 *
 * A QR that does not scan is worse than no QR, because it fails silently in somebody else's hand —
 * across a table, in a lobby, with one chance to make an impression. Scanners binarize the camera
 * image and care about luminance separation, not hue, so a brand colour earns the module slot only
 * if it is dark enough on the chip's cream. That is the whole of what these measure; the rest of
 * the rules the chip encodes (High error correction behind a logo, a light tile in both palettes)
 * are structural and live in `share/QrChip.kt`.
 */
@OptIn(ExperimentalTestApi::class)
class QrChipTest {

    @Test
    fun theModuleColourIsDarkEnoughOnTheChipToScan() {
        val ratio = Wcag.contrast(VintoQr.MODULE, VintoQr.GROUND)
        assertTrue(
            ratio >= MIN_SCAN_CONTRAST,
            "the QR module is too light to scan: ${(ratio * 100).roundToInt() / 100.0} " +
                "(needs $MIN_SCAN_CONTRAST)",
        )
    }

    /**
     * And the bright green would not — which is why the felt is the one that is used.
     *
     * The trap this guards is a reasonable-looking edit: `Brand` is the colour the app puts on a
     * dark board and the obvious thing to reach for when a code should "look like Vinto". It
     * measures about 1.7:1 on cream, which is a code no camera resolves. Asserting the failure
     * keeps the choice of the felt from reading as arbitrary to whoever edits this next.
     */
    @Test
    fun theBrightBrandGreenWouldNotScanWhichIsWhyItIsNotUsed() {
        val bright = Wcag.contrast(Brand, VintoQr.GROUND)
        assertTrue(
            bright < MIN_SCAN_CONTRAST,
            "the bright brand green now clears the scan floor (${(bright * 100).roundToInt() / 100.0}); " +
                "the chip's use of the felt may read as arbitrary — document it or revisit",
        )
    }

    /** The module is the theme's felt, read from it rather than copied, so a repaint carries. */
    @Test
    fun theModuleIsTheThemesOwnFeltRatherThanACopyOfIt() {
        assertEquals(Felt, VintoQr.MODULE)
    }

    /**
     * It paints, with its mark in it, and says what it is.
     *
     * Rendering rather than trusting the library: the chip composes a qrose painter, a resource
     * and a custom `Painter` together, and any one of them throwing would take out the invite
     * sheet — the screen a player reaches when somebody is waiting for them.
     */
    @Test
    fun theChipRendersAndCarriesItsLabel() {
        var found = 0
        runComposeUiTest {
            setContent {
                VintoTheme(dark = false) {
                    Box(Modifier.size(200.dp)) {
                        QrChip(url = "https://vinto.kupalinka.app/r/7KQ2MP", logo = VintoQr.ringedMark(), label = LABEL)
                    }
                }
            }
            waitForIdle()
            found = onAllNodesWithContentDescription(LABEL).fetchSemanticsNodes().size
        }
        assertEquals(1, found, "the chip is not on screen, or does not say what it is")
    }

    private companion object {
        const val LABEL = "Or scan this code"
    }
}

package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import game.vinto.app.game.HelpSheet
import game.vinto.app.game.SeatBadge
import game.vinto.app.game.spoken
import game.vinto.app.theme.VintoTheme
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The seat marks in the help sheet's legend, measured on the sheet they are drawn on.
 *
 * The legend draws the table's own `SeatMark` — the same composable, so it cannot come to
 * disagree with the felt — and the felt's ink came with it. A seat plate is dark in both
 * schemes; the help sheet is the rail, which is paper on a light phone. So the bot and away
 * marks, near-white at 45% for a dark plate, were near-white on cream at 1.01:1, and the crown,
 * the nod and the shed mark were the same mistake at 2.14, 1.61 and 2.7. Found by the
 * large-text review of 2026-10-05.
 *
 * Neither contrast test could see it. [ContrastTest] measures declared pairs, and every token
 * involved passes against the ground it was chosen for; [ScreenContrastTest] reads text, and a
 * mark is a drawing. So each mark is photographed where the legend draws it, in both schemes,
 * and its ink — the pixel furthest from the ground inside the mark's own box, which for a mark
 * drawn at less than full alpha is the blend a player actually sees — is held to SC 1.4.11's
 * 3:1 against that ground.
 *
 * Drawn at three pixels a point, as a phone draws it: at one, a stroke a tenth of an 18 dp mark
 * wide is under two pixels, no pixel of it is wholly ink, and the measurement would be of the
 * antialiasing rather than of the colour.
 */
@OptIn(ExperimentalTestApi::class)
class LegendMarksContrastTest {

    @Test
    fun everySeatMarkInTheLegendCanBeSeenInBothSchemes() {
        val found = unseen(dark = false) + unseen(dark = true)
        assertTrue(
            found.isEmpty(),
            "seat marks in the help sheet that cannot be seen:\n" + found.joinToString("\n"),
        )
    }

    /** Every seat mark in the badges tab that does not clear 3:1, said with its ratio. */
    private fun unseen(dark: Boolean): List<String> {
        val scheme = if (dark) "dark" else "light"
        val found = mutableListOf<String>()
        runDesktopComposeUiTest(PHONE_W * SCALE, PHONE_H * SCALE) {
            val said = HashMap<SeatBadge, String>()
            setContent {
                SeatBadge.entries.forEach { said[it] = stringResource(it.spoken()) }
                CompositionLocalProvider(LocalDensity provides Density(SCALE.toFloat())) {
                    VintoTheme(dark = dark) {
                        Box(modifier = Modifier.size(PHONE_W.dp, PHONE_H.dp)) {
                            HelpSheet(open = true, now = null, left = DECK_LEFT, onDismiss = {})
                        }
                    }
                }
            }
            waitForIdle()
            onNodeWithText("BADGES").performClick()
            waitForIdle()

            val screen = onRoot(useUnmergedTree = true).captureToImage().toPixelMap()
            SeatBadge.entries.forEach { badge ->
                val mark = onNode(markSaying(said.getValue(badge)), useUnmergedTree = true)
                    .fetchSemanticsNode("the legend draws no ${badge.name} mark")
                verdict("$scheme: the ${badge.name} mark", screen.inkAndGround(mark.boundsInRoot))
                    ?.let { found += it }
            }
        }
        return found
    }

    /** Why a mark cannot be seen, or null for one that can. */
    private fun verdict(what: String, seen: Pair<Color, Color>?): String? {
        val (ink, ground) = seen ?: return "  $what is off the screen"
        val got = Wcag.contrast(ink, ground)
        if (got >= Wcag.UI) return null
        return "  $what is ${ink.hex()} on ${ground.hex()} at %.2f:1, and has to be %.1f:1"
            .format(got, Wcag.UI)
    }

    /**
     * The mark whose description *ends* in [spoken]: the away mark says "a bot" first and its
     * own words second, so matching on any of its words would find it for the bot's as well.
     */
    private fun markSaying(spoken: String) = SemanticsMatcher("the mark that says \"$spoken\"") {
        it.config.getOrNull(SemanticsProperties.ContentDescription)?.lastOrNull() == spoken
    }

    /**
     * The mark's ink and the ground it stands on, from the pixels inside its box.
     *
     * The ground is the commonest colour there, since a mark is strokes on a field; the ink is
     * whichever pixel stands furthest from that ground. Antialiasing only ever blends the two,
     * so no edge pixel can stand further off than the ink itself does.
     */
    private fun PixelMap.inkAndGround(box: Rect): Pair<Color, Color>? {
        val left = box.left.toInt().coerceAtLeast(0)
        val top = box.top.toInt().coerceAtLeast(0)
        val right = box.right.toInt().coerceAtMost(width)
        val bottom = box.bottom.toInt().coerceAtMost(height)
        if (right <= left || bottom <= top) return null

        val pixels = buildList {
            for (y in top until bottom) {
                for (x in left until right) add(this@inkAndGround[x, y])
            }
        }
        val ground = pixels.groupingBy { it }.eachCount().maxBy { it.value }.key
        val ink = pixels.maxBy { Wcag.contrast(it, ground) }
        return ink to ground
    }

    private fun Color.hex(): String = "#%06X".format(HEX_MASK and toArgb())

    private companion object {
        const val PHONE_W = 411
        const val PHONE_H = 740

        /** Pixels a point: a phone's, so a stroke has pixels that are wholly ink. */
        const val SCALE = 3
        const val DECK_LEFT = 33
        const val HEX_MASK = 0xFFFFFF
    }
}

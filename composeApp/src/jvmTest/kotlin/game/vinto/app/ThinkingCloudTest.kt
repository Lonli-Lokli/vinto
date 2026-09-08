package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.art.Res
import game.vinto.app.art.seat_badge_waiting
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.Rail
import game.vinto.app.theme.VintoTheme
import game.vinto.client.Question
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The thought cloud over a seat the table is waiting on.
 *
 * It is the one mark on the felt that is about *right now* — a bot deciding, or a player yet
 * to peek — and it was drawn as a still life: four dots that look exactly the same whether the
 * seat is thinking or the round has hung. A player watching a table that has stopped cannot
 * tell those two apart, which is the whole complaint. So the cloud has to move, and this
 * measures that it does: the same mark, photographed twice a third of a second apart.
 *
 * And under reduced motion it has to keep saying the same thing while standing still — the
 * rule the spinner already follows (`VintoSpinner`), so a stilled mark is the whole cloud
 * rather than one frozen frame of a wave.
 */
@OptIn(ExperimentalTestApi::class)
class ThinkingCloudTest {

    @Test
    fun theCloudIsThinkingRatherThanSittingThere() {
        val moved = changesOverTime(reduced = false)

        assertTrue(
            moved,
            "the thought cloud draws the same pixels a third of a second apart — a seat that " +
                "is thinking and a round that has hung look identical",
        )
    }

    @Test
    fun theCloudHoldsStillWhenMotionIsOff() {
        val moved = changesOverTime(reduced = true)

        assertTrue(!moved, "the thought cloud still moves with motion turned off")
    }

    /** Whether the waiting mark draws something different a third of a second later. */
    private fun changesOverTime(reduced: Boolean): Boolean {
        var moved = false
        runComposeUiTest {
            val said = mutableStateOf("")
            mainClock.autoAdvance = false
            setContent {
                said.value = stringResource(Res.string.seat_badge_waiting)
                CompositionLocalProvider(LocalReducedMotion provides reduced) {
                    VintoTheme(dark = false) {
                        Surface(color = Rail.fill) {
                            Box(modifier = Modifier.size(PHONE_W, PHONE_H)) { Setup() }
                        }
                    }
                }
            }
            mainClock.advanceTimeByFrame()

            val first = cloud(said.value)
            mainClock.advanceTimeBy(LATER_MS)
            val second = cloud(said.value)
            moved = first != second
        }
        return moved
    }

    /** The table as a round opens: nobody has peeked, so every seat is being waited on. */
    @Composable
    private fun Setup() {
        val view = teachingSession().view.value
        TableScreen(
            state = TableState(view, tableFor(view, Question.None), null, emptyList(), 1),
            layout = TableLayout.forScreen(PHONE_H),
            onMove = {},
            onHelp = {},
            onSettings = {},
        )
    }

    /** Every pixel of the first waiting mark on the felt. */
    private fun ComposeUiTest.cloud(said: String): List<Int> {
        val marks = onAllNodesWithContentDescription(said, useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue(marks.isNotEmpty(), "no seat is showing the waiting mark")
        return onAllNodesWithContentDescription(said, useUnmergedTree = true)[0]
            .captureToImage()
            .toPixelMap()
            .pixels()
    }

    private fun PixelMap.pixels(): List<Int> = buildList {
        for (y in 0 until height) {
            for (x in 0 until width) add(this@pixels[x, y].hashCode())
        }
    }

    private companion object {
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp

        /** Long enough that any honest thinking animation has moved on. */
        const val LATER_MS = 330L
    }
}

package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import game.vinto.app.game.TossClock
import game.vinto.app.theme.Rail
import game.vinto.app.theme.VintoTheme
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import game.vinto.shapes.ActiveTossIn
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.Rank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The toss-in clock takes the same room whether it is counting or not.
 *
 * It was composed only while a window was open, in a column whose table has `weight(1f)` — so
 * the felt jumped up the moment a toss-in opened and dropped back when it closed. Reported as
 * exactly that: a text block with the seconds left, and the table moving under it. And it
 * lands at the worst possible moment, because a window opens on a card that is still flying
 * to the pile the jump has just moved.
 *
 * So the row is always there, and what sits in it is right-aligned under the controls: on a
 * desktop the sentence and the ask, on a phone one tappable circle with the seconds in it.
 * A number in a circle is the smallest thing that can carry a countdown, and it is where a
 * phone has the least room to spend on one.
 */
@OptIn(ExperimentalTestApi::class)
class TossClockTest {

    @Test
    fun theRowIsTheSameHeightOpenOrShut() {
        val counting = heightOf(windowOpen(), wide = false)
        val quiet = heightOf(noWindow(), wide = false)

        assertEquals(
            quiet,
            counting,
            "the clock is $counting dp while it counts and $quiet dp when it does not, so the " +
                "table above it moves every time a toss-in opens and closes",
        )
    }

    /** And the same on a desktop, where the sentence is longer than the circle. */
    @Test
    fun theRowIsTheSameHeightOnADesktopToo() {
        val counting = heightOf(windowOpen(), wide = true)
        val quiet = heightOf(noWindow(), wide = true)

        assertEquals(quiet, counting, "a desktop table moves when the clock appears")
    }

    /** A phone gets the circle: the seconds, and nothing else taking width from the rail. */
    @Test
    fun aPhoneGetsTheSecondsInACircle() {
        var badge = 0
        var sentence = 0
        runComposeUiTest {
            setContent { Framed { TossClock(windowOpen(), wide = false, onMoreTime = {}) } }
            waitForIdle()
            badge = onAllNodesWithText("$SECONDS").fetchSemanticsNodes().size
            sentence = onAllNodesWithText("The table moves on in $SECONDS s").fetchSemanticsNodes().size
        }

        assertTrue(badge > 0, "the phone's clock does not show the seconds")
        assertEquals(0, sentence, "the phone is given the desktop's sentence, which costs the rail width")
    }

    /** A desktop gets the sentence and the ask, on the right, under the controls. */
    @Test
    fun aDesktopGetsTheSentenceAndTheAsk() {
        var sentence = 0
        var ask = 0
        runComposeUiTest {
            setContent { Framed { TossClock(windowOpen(), wide = true, onMoreTime = {}) } }
            waitForIdle()
            sentence = onAllNodesWithText("The table moves on in $SECONDS s").fetchSemanticsNodes().size
            ask = onAllNodesWithContentDescription("More time").fetchSemanticsNodes().size
        }

        assertTrue(sentence > 0, "the desktop clock says nothing")
        assertTrue(ask > 0, "there is no way to ask for more time")
    }

    // ------------------------------------------------------------------ fixtures

    private fun heightOf(view: PlayerView, wide: Boolean): Int {
        var height = -1
        runComposeUiTest {
            setContent {
                Framed {
                    Box(modifier = Modifier.onGloballyPositioned { height = it.size.height }) {
                        TossClock(view, wide = wide, onMoreTime = {})
                    }
                }
            }
            waitForIdle()
        }
        return height
    }

    @Composable
    private fun Framed(content: @Composable () -> Unit) {
        VintoTheme(dark = false) {
            Surface(color = Rail.fill) {
                Box(modifier = Modifier.size(WIDTH, HEIGHT)) { content() }
            }
        }
    }

    /**
     * The viewer's own seat, with a window open and waiting on them.
     *
     * The sub-phase is what makes it open, not the record's `waitingForInput` — see
     * `tossInIsOpen`, and the table that stopped because the two disagreed.
     */
    private fun windowOpen(): PlayerView {
        val view = teachingSession().view.value
        return view.copy(
            subPhase = GameSubPhase.TOSS_QUEUE_ACTIVE,
            activeTossIn = ActiveTossIn(
                ranks = listOf(Rank.EIGHT),
                initiatorId = view.players.first { it.id != view.viewerId }.id,
                originalPlayerIndex = 1,
                participants = emptyList(),
                queuedActions = emptyList(),
                waitingForInput = true,
                playersReadyForNextTurn = emptyList(),
            ),
            tossInMsRemaining = SECONDS * 1_000L,
        )
    }

    private fun noWindow(): PlayerView =
        teachingSession().view.value.copy(activeTossIn = null, tossInMsRemaining = null)

    private companion object {
        val WIDTH: Dp = 411.dp
        val HEIGHT: Dp = 200.dp
        const val SECONDS = 12
    }
}

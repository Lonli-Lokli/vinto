package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.CardStage
import game.vinto.app.game.TableLayout
import game.vinto.app.theme.VintoTheme
import game.vinto.client.Attention
import game.vinto.client.Beat
import game.vinto.client.Frame
import game.vinto.client.Say
import game.vinto.client.Speaker
import game.vinto.client.teachingSession
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The rail says what a move was as the move is drawn — not when the engine finished it.
 *
 * Reported from a phone: three bots' lines were in the box under the felt before a single
 * one of their cards had moved, so the log was a spoiler for the animation it was meant to
 * caption. The engine finishes every bot's turn in one request and the session narrates each
 * as it goes; the screen read that log live while the felt stepped through the frames.
 *
 * Each frame now carries its own lines, and the stage tells them as it steps to the frame.
 * This drives the clock by hand: with two bot moves in one batch, the first line must be on
 * the rail *alone* for a while before the second joins it — and the second must not appear
 * until its own frame is picked up.
 */
@OptIn(ExperimentalTestApi::class)
class LogStepsWithTheTableTest {

    @Test
    fun aLineAppearsWithItsFrameAndNotBefore() = runComposeUiTest {
        val view = teachingSession().view.value
        val first = Say.Drew(Speaker.Named("Ember"))
        val second = Say.Drew(Speaker.Named("Sky"))
        val frames = MutableSharedFlow<List<Frame>>(replay = 1)
        var told: List<Say> = emptyList()

        mainClock.autoAdvance = false
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    CardStage(
                        frames = frames,
                        live = view,
                        sizes = TableLayout.forScreen(PHONE_H).sizes,
                        pace = 1f,
                        // The session's log as the screen would see it: already holding
                        // both lines, because the engine narrated both before either flew.
                        recent = listOf(first, second),
                    ) { _, lines -> told = lines }
                }
            }
        }

        frames.tryEmit(
            listOf(
                frame("bot-1", first),
                frame("bot-2", second),
            ),
        )

        // Walk the clock and note the rail at the moment each line first shows up.
        var whenFirst: List<Say>? = null
        var whenSecond: List<Say>? = null
        repeat(STEPS) {
            mainClock.advanceTimeBy(STEP_MS)
            if (whenFirst == null && first in told) whenFirst = told.toList()
            if (whenSecond == null && second in told) whenSecond = told.toList()
        }

        assertEquals(listOf(first), whenFirst, "the first move's line arrived with company")
        assertEquals(listOf(first, second), whenSecond, "the second line arrived out of step")
        assertTrue(whenFirst != whenSecond, "both lines landed in the same instant")
    }

    /** One bot move with something to animate, so the stage has to step to it. */
    private fun frame(actor: String, line: Say) = Frame(
        action = GameAction.DrawCard(PlayerIdPayload(actor)),
        scenes = listOf(listOf(Beat.Attend(actor, Attention.TURN))),
        view = teachingSession().view.value,
        said = listOf(line),
    )

    private companion object {
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
        const val STEP_MS = 100L
        const val STEPS = 80
    }
}

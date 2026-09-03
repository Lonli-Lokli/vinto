package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.CardStage
import game.vinto.app.game.Coaching
import game.vinto.app.game.TableLayout
import game.vinto.app.theme.VintoTheme
import game.vinto.client.Attention
import game.vinto.client.Beat
import game.vinto.client.Frame
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.PlayerIdPayload
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A coach is asked to hold on the move it is about to explain, never on the end of the round.
 *
 * The session publishes the view its whole dispatch arrived at *before* it emits the frames
 * for the moves that got there — the game really has moved on, and the frames are how the
 * screen catches up. So between the two there is an instant where the screen is showing the
 * end of the batch, and the stage used to ask the coach whether to hold in exactly that
 * instant, before stepping to the first move.
 *
 * At the end of a lesson that is fatal rather than untidy. Calling Vinto submits the call and
 * the coalition's whole final round as one batch; the view published first is the *scored*
 * one; the coach read it, decided the lesson was over, and held the table on an end card whose
 * only button leaves. The learner saw the score, pressed the one button there was, and the
 * round they had played the whole hand for never ran (product owner, twice — once diagnosed
 * as the animation queue dropping the batch, which it was not).
 */
@OptIn(ExperimentalTestApi::class)
class StageStepsBeforeTheCoachTest {

    @Test
    fun theTableStepsToTheMoveBeforeTheCoachIsAskedToHold() = runComposeUiTest {
        val playing = teachingSession().view.value
        val scored = playing.copy(phase = GamePhase.SCORING)
        val frames = MutableSharedFlow<List<Frame>>(replay = 1)
        var shown: PlayerView? = null

        mainClock.autoAdvance = false
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    CardStage(
                        frames = frames,
                        // What the session published the moment the dispatch returned: the
                        // round is over. The frames below are how the table gets there.
                        live = scored,
                        sizes = TableLayout.forScreen(PHONE_H).sizes,
                        pace = 1f,
                        // A coach with something to say, as it has after a Vinto call.
                        coaching = Coaching(hold = { true }),
                        recent = emptyList(),
                    ) { view, _ -> shown = view }
                }
            }
        }

        frames.tryEmit(listOf(moveStillBeingPlayed(playing)))
        repeat(STEPS) { mainClock.advanceTimeBy(STEP_MS) }

        assertEquals(
            GamePhase.PLAYING,
            shown?.phase,
            "the coach was held over the finished round instead of the move it explains",
        )
    }

    /** One move of the batch, leaving a table the round is still being played on. */
    private fun moveStillBeingPlayed(view: PlayerView) = Frame(
        action = GameAction.DrawCard(PlayerIdPayload("bot-1")),
        scenes = listOf(listOf(Beat.Attend("bot-1", Attention.TURN))),
        view = view,
    )

    private companion object {
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
        const val STEPS = 40
        const val STEP_MS = 50L
    }
}

package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A round dealt while the last one is still being drawn shows the new deal.
 *
 * Found tracing `STAGE_STALLED in ONLINE` (Sentry, Android 1.1, build 656) — one of two ways to
 * that report, and not the one those two events took: they were mid-lap, and this one reports
 * at the top of a deal. The other is a phone put in a pocket mid-batch, which
 * `FailureCountingTest.aStageOutOfSightIsNotCounted` holds.
 *
 * Online is a new session per round, and the screen is not rebuilt for it — so the deal swaps
 * the frames flow under a stage that may still be drawing the round before, and the loop
 * drawing it is cancelled halfway. Everything it would have put back on the way out stayed as
 * it was: the stage still said it was draining, which is what the watchdog counted, and the
 * felt went on showing the old round's move in place of the new deal until the new round's
 * first batch had been drawn to the end — on a deal that waits for this player, never.
 *
 * Driven by hand on the clock: the old round's move is caught on screen, the deal lands, and
 * the felt must be on the new round once there has been time for anything to settle.
 */
@OptIn(ExperimentalTestApi::class)
class DealtMidDrawTest {

    @Test
    fun aRoundDealtWhileTheLastIsStillBeingDrawnShowsTheNewDeal() = runComposeUiTest {
        val base = teachingSession().view.value
        val lastMove = base.copy(roundNumber = 1, turnNumber = LAST_TURN)
        val roundOver = base.copy(roundNumber = 1, turnNumber = LAST_TURN + 1)
        val dealt = base.copy(roundNumber = 2, turnNumber = 0)

        val oldRound = MutableSharedFlow<List<Frame>>(replay = 1)
        // The new session's flow, with nothing on it: a deal that waits for this player to look
        // at their cards sends no frame until they have.
        val newRound = MutableSharedFlow<List<Frame>>(replay = 1)

        var frames: Flow<List<Frame>> by mutableStateOf(oldRound)
        var live: PlayerView by mutableStateOf(roundOver)
        var shown: PlayerView? = null

        mainClock.autoAdvance = false
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    CardStage(
                        frames = frames,
                        live = live,
                        sizes = TableLayout.forScreen(PHONE_H).sizes,
                        pace = 1f,
                    ) { view, _ -> shown = view }
                }
            }
        }

        oldRound.tryEmit(listOf(longMove(lastMove)))

        // Until the old round's move is on the felt — it has to be mid-draw when the deal lands.
        var steps = 0
        while (shown != lastMove && steps++ < STEPS) mainClock.advanceTimeBy(STEP_MS)
        assertEquals(lastMove, shown, "the old round's move was never drawn, so nothing was interrupted")

        // The deal: a new session, so a new frames flow and a new live view, in the same instant.
        runOnUiThread {
            frames = newRound
            live = dealt
        }
        repeat(STEPS) { mainClock.advanceTimeBy(STEP_MS) }

        assertEquals(dealt, shown, "the felt is still showing the round before the deal")
    }

    /** A bot's move long enough to be interrupted: several scenes, each holding the table. */
    private fun longMove(view: PlayerView) = Frame(
        action = GameAction.DrawCard(PlayerIdPayload("bot-1")),
        scenes = List(SCENES) { listOf(Beat.Attend("bot-1", Attention.TURN)) },
        view = view,
    )

    private companion object {
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
        const val STEP_MS = 100L
        const val STEPS = 100
        const val SCENES = 12
        const val LAST_TURN = 40
    }
}

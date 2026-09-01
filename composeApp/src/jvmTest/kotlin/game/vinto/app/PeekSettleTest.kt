package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.CardStage
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.VintoTheme
import game.vinto.client.Anchor
import game.vinto.client.Beat
import game.vinto.client.Frame
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.shapes.Card
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.Rank
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A card that was looked at is put back, rather than taken away.
 *
 * The lift used to be one-way: the card rose out of the hand, held, and then disappeared from
 * one frame to the next when its scene ended — reappearing face-down in the hand it had never
 * visibly returned to. That reads as the table blinking, and it is the one moment in the game
 * where a player is asked to memorise something, so the moment they look away from is exactly
 * the wrong one to skip.
 *
 * The signature of the descent is a state that could not previously exist: the face no longer
 * showing, and the hand still holding the card's place open. Before, those two happened in the
 * same frame — the card went out and the gap closed together.
 */
@OptIn(ExperimentalTestApi::class)
class PeekSettleTest {

    @Test
    fun aPeekedCardIsPutBackRatherThanTakenAway() = runComposeUiTest {
        mainClock.autoAdvance = false

        val view = teachingSession().view.value
        val me = view.viewerId
        val layout = TableLayout.forScreen(PHONE_H)
        val frames = MutableStateFlow(emptyList<Frame>())

        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    CardStage(frames = frames, live = view, sizes = layout.sizes, pace = 1f) { shown, _ ->
                        TableScreen(
                            state = TableState(shown, tableFor(shown), null, emptyList(), 1),
                            layout = layout,
                            onMove = {},
                            onHelp = {},
                            onSettings = {},
                            onReport = {},
                            onDeck = {},
                        )
                    }
                }
            }
        }
        // The table has to have laid out and said where its seats are before a beat aimed at
        // one of them means anything.
        settle()

        frames.value = listOf(
            Frame(
                action = GameAction.ConfirmPeek(PlayerIdPayload(me)),
                scenes = listOf(listOf(Beat.Peek(Anchor.Seat(me, POSITION), PEEKED))),
                view = view,
            ),
        )

        var sawTheFace = false
        var sawItComingBack = false
        repeat(STEPS) {
            mainClock.advanceTimeBy(STEP_MS)
            val showingTheFace = nodes(FACE) > 0
            val holdingThePlace = nodes(SLOT) == 0
            if (showingTheFace) sawTheFace = true
            if (sawTheFace && !showingTheFace && holdingThePlace) sawItComingBack = true
        }

        assertTrue(sawTheFace, "the peek never showed the card")
        assertTrue(
            sawItComingBack,
            "the card was taken away rather than put back: the face went out and the hand " +
                "closed up in the same frame",
        )

        settle()
        assertTrue(nodes(SLOT) > 0, "the hand never got its card back")
        assertTrue(nodes(FACE) == 0, "the peeked card is still face-up after the peek ended")
    }

    private fun ComposeUiTest.nodes(description: String): Int =
        onAllNodesWithContentDescription(description).fetchSemanticsNodes().size

    private fun ComposeUiTest.settle() {
        mainClock.advanceTimeBy(SETTLE_MS)
        waitForIdle()
    }

    private companion object {
        const val POSITION = 1

        /** The hand's own label for the slot being peeked; absent while the card is out of it. */
        const val SLOT = "You, card 2"

        /** What the raised card reads as, which nothing else on this table does. */
        const val FACE = "9, worth 9"

        val PEEKED = Card(id = "9_0", rank = Rank.NINE, value = 9, actionText = null, played = false)

        const val STEP_MS = 40L
        const val STEPS = 80
        const val SETTLE_MS = 4_000L
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
    }
}

package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.art.Res
import game.vinto.app.art.card_position
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.Rail
import game.vinto.app.theme.VintoTheme
import game.vinto.client.Move
import game.vinto.client.Question
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.PlayerView
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * While a move of yours is in flight, the table stops offering to take another.
 *
 * **Reported from a real game: "I pressed a card and nothing happened."** `GameHolder.act` has
 * always refused a second press while the first is unanswered, which is right — online the room
 * applies whatever arrives, `ClientMessage.Action` carries nothing to dedupe by, and a repeat at
 * the same index names a *different* card once the hand has slid. The bug was that the refusal
 * was invisible: the card kept its breathing ring, the rail kept its live buttons, so the player
 * pressed again, nothing happened again, and a working guard read as a broken app.
 *
 * A dead tap is the worst thing a game can do, because it cannot be told apart from a crash —
 * the same reasoning as `DisabledButtonTest`, one layer in. So the affordance goes away at the
 * thing they touched: no ring, no click handler at all (so no ripple and no haptic promising
 * something happened), and the rail's buttons spin instead of pretending.
 */
@OptIn(ExperimentalTestApi::class)
class ActingTapsTest {

    @Test
    fun aCardOffersNoTapWhileAMoveOfYoursIsInFlight() {
        val view = tossWindow()
        var moves = 0

        runComposeUiTest {
            val label = mutableStateOf("")
            setContent {
                label.value = cardLabel(view)
                Felt { Table(view, busy = true, onMove = { moves += 1 }) }
            }
            waitForIdle()

            val cards = onAllNodesWithContentDescription(label.value)
            assertTrue(
                cards.fetchSemanticsNodes().isNotEmpty(),
                "the card is not on the felt at all, so this asserts nothing",
            )
            // No handler rather than one that returns: `CardFace` attaches no `clickable`
            // without one, which is what takes the ripple and the haptic away with it.
            cards[0].assertHasNoClickAction()
        }

        assertEquals(0, moves, "a card dispatched a move while one was already in flight")
    }

    /**
     * And it is a card again the moment the move is answered.
     *
     * The half that matters as much: a table still dead after its answer arrived is worse than
     * one that was never guarded, because now the player has reason to think the game is stuck
     * rather than that they were early.
     */
    @Test
    fun andTakesTapsAgainAsSoonAsNothingIsInFlight() {
        val view = tossWindow()
        var moves = 0

        runComposeUiTest {
            val label = mutableStateOf("")
            setContent {
                label.value = cardLabel(view)
                Felt { Table(view, busy = false, onMove = { moves += 1 }) }
            }
            waitForIdle()

            val cards = onAllNodesWithContentDescription(label.value)
            assertTrue(cards.fetchSemanticsNodes().isNotEmpty(), "the card is not on the felt")
            cards[0].assertHasClickAction()
            cards[0].performClick()
            waitForIdle()
        }

        assertEquals(1, moves, "a tappable card did not dispatch its move")
    }

    /**
     * The rail goes with it: strictly fewer live controls while a move is in flight.
     *
     * Counted rather than named because the rail's buttons are labelled from the model's own
     * `Label`, and what is being asserted is not which button is where — it is that the whole
     * set of things that will answer a press shrinks. `GameButton` already draws a spinner in
     * the label's place and swallows its taps (`DisabledButtonTest`); the risk this covers is
     * that the flag never reaches it.
     */
    @Test
    fun theRailsControlsGoQuietTooRatherThanStayingLive() {
        val view = tossWindow()
        var idle = 0
        var acting = 0

        runComposeUiTest {
            setContent { Felt { Table(view, busy = false, onMove = {}) } }
            waitForIdle()
            idle = onAllNodes(hasClickAction() and isEnabled()).fetchSemanticsNodes().size
        }
        runComposeUiTest {
            setContent { Felt { Table(view, busy = true, onMove = {}) } }
            waitForIdle()
            acting = onAllNodes(hasClickAction() and isEnabled()).fetchSemanticsNodes().size
        }

        assertTrue(idle > 0, "nothing on this table was pressable to begin with")
        assertTrue(
            acting < idle,
            "the same number of controls answer a press while a move is in flight ($acting of $idle)",
        )
    }

    // ------------------------------------------------------------------ fixtures

    /** What one of the player's own cards is called to a screen reader, which is how it is found. */
    @Composable
    private fun cardLabel(view: PlayerView): String {
        val me = view.players.first { it.id == view.viewerId }
        return stringResource(Res.string.card_position, me.nickname, 1)
    }

    /** A toss-in window: the moment the report was about, where the player's cards are tappable. */
    private fun tossWindow(): PlayerView {
        lateinit var view: PlayerView
        runTest {
            val session = teachingSession()
            val me = session.playerId
            session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
            session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
            session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
            session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
            session.dispatch(GameAction.DiscardCard(PlayerIdPayload(me)))
            view = session.view.value
        }
        return view
    }

    @Composable
    private fun Felt(content: @Composable () -> Unit) {
        VintoTheme(dark = false) {
            Surface(color = Rail.fill) {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) { content() }
            }
        }
    }

    @Composable
    private fun Table(view: PlayerView, busy: Boolean, onMove: (Move) -> Unit) {
        TableScreen(
            state = TableState(
                view = view,
                table = tableFor(view, Question.None),
                refusal = null,
                recent = emptyList(),
                round = 1,
                busy = busy,
            ),
            layout = TableLayout.forScreen(PHONE_H),
            onMove = onMove,
            onHelp = {},
            onSettings = {},
        )
    }

    private companion object {
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
    }
}

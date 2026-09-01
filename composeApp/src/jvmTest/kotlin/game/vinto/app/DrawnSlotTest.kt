package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.VintoTheme
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.CardView
import game.vinto.engine.PendingActionView
import game.vinto.engine.PlayerView
import game.vinto.shapes.ActionPhase
import game.vinto.shapes.Card
import game.vinto.shapes.PendingCardOrigin
import game.vinto.shapes.Rank
import game.vinto.shapes.TargetType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A drawn card being *played* is on the pile, and nowhere else.
 *
 * Reported from a phone: a bot drew an 8 and played it, and for the length of its action the
 * 8 sat in the drawn slot under the deck and on the discard at once. The pile was right —
 * `cardInPlay` puts a drawn card on the pile the moment its action is engaged — and the drawn
 * slot had never been told: it drew any pending card that came off the deck, whatever phase
 * its action was in.
 */
@OptIn(ExperimentalTestApi::class)
class DrawnSlotTest {

    @Test
    fun aDrawnCardBeingDecidedAboutIsInTheDrawnSlot() = runComposeUiTest {
        show(holding(ActionPhase.CHOOSING_ACTION))

        assertEquals(1, nodes("the card in your hand"), "the drawn slot is empty while deciding")
        assertEquals(0, nodes("discarded 8"), "the pile shows a card nobody has played yet")
    }

    @Test
    fun aDrawnCardBeingPlayedIsOnThePileAndNotInTheDrawnSlot() = runComposeUiTest {
        show(holding(ActionPhase.SELECTING_TARGET))

        assertEquals(1, nodes("discarded 8"), "the card being played is not on the pile")
        assertEquals(0, nodes("the card in your hand"), "the same card is also in the drawn slot")
    }

    /** The table with an 8 off the deck in the viewer's hand, at the given point of its action. */
    private fun holding(phase: ActionPhase): PlayerView {
        val view = teachingSession().view.value
        return view.copy(
            pendingAction = PendingActionView(
                playerId = view.viewerId,
                actionPhase = phase,
                from = PendingCardOrigin.DRAWING,
                targetType = TargetType.OWN_CARD,
                card = CardView.Visible(Card(id = "eight", rank = Rank.EIGHT, value = 8, played = false)),
                targets = emptyList(),
            ),
        )
    }

    private fun ComposeUiTest.show(view: PlayerView) {
        setContent {
            VintoTheme {
                Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                    TableScreen(
                        state = TableState(view, tableFor(view), null, emptyList(), 1),
                        layout = TableLayout.forScreen(PHONE_H),
                        onMove = {},
                        onHelp = {},
                        onSettings = {},
                        onReport = {},
                        onDeck = {},
                    )
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.nodes(description: String) =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .fetchSemanticsNodes()
            .count {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() ==
                    description
            }

    private companion object {
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
    }
}

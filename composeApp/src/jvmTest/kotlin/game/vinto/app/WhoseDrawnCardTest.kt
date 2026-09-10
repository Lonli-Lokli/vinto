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
import kotlin.test.assertTrue

/**
 * The drawn card says **whose** it is.
 *
 * The slot under the deck holds whatever card has just been drawn, by whoever drew it: the
 * rules reveal a drawn card publicly, and watching what an opponent took is most of the
 * information in this game. What it *said* it was holding never moved with it — every card in
 * that slot was announced as "the card in your hand", so a player using a screen reader was
 * told a bot's 8 was theirs, three turns out of four.
 *
 * Someone else's card is named for the seat that drew it, in the same words a thrown card
 * uses. Your own stays yours, because "You drew 8" is a sentence about a stranger.
 */
@OptIn(ExperimentalTestApi::class)
class WhoseDrawnCardTest {

    @Test
    fun anotherSeatsDrawnCardIsNamedForThatSeat() = runComposeUiTest {
        val table = teachingSession().view.value
        val bot = table.players.first { it.id != table.viewerId }

        show(table.drawing(bot.id))

        assertTrue(
            described("${bot.nickname} drew 8"),
            "the drawn slot does not say whose card it is holding: ${descriptions()}",
        )
        assertTrue(
            !described("the card in your hand"),
            "a bot's card is announced as the viewer's own",
        )
    }

    @Test
    fun yourOwnDrawnCardIsStillYours() = runComposeUiTest {
        val table = teachingSession().view.value

        show(table.drawing(table.viewerId))

        assertTrue(
            described("the card in your hand"),
            "your own drawn card stopped being yours: ${descriptions()}",
        )
    }

    /** The table with an 8 off the deck in front of [who], still being decided about. */
    private fun PlayerView.drawing(who: String): PlayerView = copy(
        pendingAction = PendingActionView(
            playerId = who,
            actionPhase = ActionPhase.CHOOSING_ACTION,
            from = PendingCardOrigin.DRAWING,
            targetType = TargetType.OWN_CARD,
            card = CardView.Visible(Card(id = "eight", rank = Rank.EIGHT, value = 8, played = false)),
            targets = emptyList(),
        ),
    )

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
                    )
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.described(description: String) = descriptions().any { it == description }

    private fun ComposeUiTest.descriptions() =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() }

    private companion object {
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
    }
}

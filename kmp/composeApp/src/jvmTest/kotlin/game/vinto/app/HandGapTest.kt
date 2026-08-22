package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.LocalStage
import game.vinto.app.game.Stage
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.VintoTheme
import game.vinto.client.Anchor
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.CardView
import game.vinto.engine.PlayerView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A hand does not shuffle along while one of its cards is in the air.
 *
 * The table steps to the position a move produced *before* the cards fly, so that a card
 * landing has somewhere to land — which means a hand that just lost a card has already closed
 * up by the time the card starts moving. Left alone, the other four slide sideways underneath
 * a card that is still travelling, and a card thrown from the last slot sets off from a place
 * that no longer exists.
 *
 * The web app draws a transparent card in the space. This holds the space itself: the hand
 * keeps a gap wherever a flight has left, until it lands.
 */
@OptIn(ExperimentalTestApi::class)
class HandGapTest {

    @Test
    fun aHandKeepsItsShapeWhileACardIsLeavingIt() = runComposeUiTest {
        val whole = teachingSession().view.value
        val me = whole.viewerId

        // The same hand a card short, as the table would show it the instant a toss-in lands.
        val short = whole.copy(
            players = whole.players.map { seat ->
                if (seat.id == me) seat.copy(cards = seat.cards.filterIndexed { i, _ -> i != GONE })
                else seat
            },
        )

        val settled = handWidth(whole, Stage())
        val midFlight = handWidth(short, leaving(me, GONE))
        val closedUp = handWidth(short, Stage())

        assertEquals(
            settled,
            midFlight,
            "the hand holds its shape while the card is in the air",
        )
        assertTrue(closedUp < settled, "and closes up once it has landed: $closedUp vs $settled")
    }

    /** A stage with one card in the air, having left [position] of [playerId]'s hand. */
    private fun leaving(playerId: String, position: Int) = Stage().apply {
        flying += Stage.Flight(
            id = 1,
            card = CardView.Hidden,
            from = Offset.Zero,
            to = Offset.Zero,
            landingAt = Anchor.Discard,
            leftFrom = Anchor.Seat(playerId, position),
            fromSpan = 0f,
            toSpan = 0f,
        )
    }

    /** How wide the player's own hand is drawn, from the first card's left to the last's right. */
    private fun handWidth(view: PlayerView, stage: Stage): Int {
        var width = 0
        runComposeUiTest {
            setContent {
                VintoTheme {
                    CompositionLocalProvider(LocalStage provides stage) {
                        Box(modifier = Modifier.size(PHONE_W, PHONE_H)) {
                            TableScreen(
                                state = TableState(view, tableFor(view), null, emptyList(), 1),
                                layout = TableLayout.forScreen(PHONE_H),
                                onMove = {},
                                onHelp = {},
                                onReport = {},
                                onDeck = {},
                            )
                        }
                    }
                }
            }
            waitForIdle()

            val mine = cards().filter { (label, _) -> label.startsWith(ME) }.map { it.second }
            width = if (mine.isEmpty()) 0 else (mine.maxOf { it.right } - mine.minOf { it.left }).toInt()
        }
        return width
    }

    private fun ComposeUiTest.cards(): List<Pair<String, Rect>> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .fetchSemanticsNodes()
            .mapNotNull { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription)
                    ?.firstOrNull()
                    ?.takeIf { it.contains(", card ") }
                    ?.let { it to node.boundsInRoot }
            }

    private companion object {
        const val ME = "You,"
        const val GONE = 2
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
    }
}

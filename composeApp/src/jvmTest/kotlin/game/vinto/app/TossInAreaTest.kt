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
import game.vinto.engine.PlayerView
import game.vinto.shapes.ActiveTossIn
import game.vinto.shapes.Rank
import game.vinto.shapes.TossInAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A toss-in window shows what has been thrown into it, and by whom.
 *
 * This is the web app's toss-in area, and the half of it that was missing here: under the
 * ranks the window is asking for, the cards that have actually gone in. Without it the only
 * account of a throw was a line in the *drawn* slot reading "Raph — ?" whenever the seat was
 * not entitled to the face — a question mark standing where a fact belongs, in the one place
 * on the table reserved for the card you drew yourself.
 *
 * The second case is the one that keeps it honest on a phone: the corner grows a row of cards
 * while a card is flying into it, and a corner that grows moves the place the card is landing.
 */
@OptIn(ExperimentalTestApi::class)
class TossInAreaTest {

    @Test
    fun theThrownCardsAreNamedUnderTheWindow() = runComposeUiTest {
        val view = tossing(teachingSession().view.value)
        val thrower = view.players.first { it.id != view.viewerId }

        show(view)

        assertEquals(1, nodes("${thrower.nickname} threw 8"), "the thrown 8 is not on the table")
        assertEquals(0, nodes("—"), "something is still being drawn as an unknown")
        assertTrue(
            texts().any { it.startsWith("Tossed (") },
            "the window does not say how many cards have gone in: ${texts()}",
        )
    }

    /**
     * The corner as one spoken sentence.
     *
     * Visually the window is a heading, a rank chip and a count, each its own node; read one
     * at a time they are "Toss-in", "8", "Tossed (1)" — three fragments a screen reader user
     * has to assemble into a rule. The column carries a single description naming the thing
     * that matters — which ranks the table will accept right now — so the corner announces
     * itself the way a dealer would.
     */
    @Test
    fun theWindowSaysWhatItIsWaitingFor() = runComposeUiTest {
        show(tossing(teachingSession().view.value))

        assertEquals(
            1,
            nodes("Toss-in window, matching 8"),
            "the toss-in corner does not describe itself as one sentence",
        )
    }

    @Test
    fun theWindowIsTheSameHeightBeforeAndAfterACardGoesIn() = runComposeUiTest {
        val whole = teachingSession().view.value
        val empty = deckTop(tossing(whole, thrown = emptyList()))
        val filled = deckTop(tossing(whole))

        assertEquals(
            empty,
            filled,
            "the toss-in corner grew when a card went into it, moving the deck — and with " +
                "it the place the next card is landing",
        )
    }

    /** The same table with a toss-in window open, and a card or two already in it. */
    private fun tossing(
        view: PlayerView,
        thrown: List<TossInAction> = listOf(
            TossInAction(view.players.first { it.id != view.viewerId }.id, Rank.EIGHT, 0),
        ),
    ) = view.copy(
        activeTossIn = ActiveTossIn(
            ranks = listOf(Rank.EIGHT),
            initiatorId = view.viewerId,
            originalPlayerIndex = 0,
            participants = emptyList(),
            queuedActions = thrown,
            waitingForInput = true,
            playersReadyForNextTurn = emptyList(),
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
                        onReport = {},
                        onDeck = {},
                    )
                }
            }
        }
        waitForIdle()
    }

    /**
     * Where the deck is drawn, which is what a growing toss-in corner moves.
     *
     * The felt fills the screen whatever happens inside it, so measuring the felt proves
     * nothing — the first version of this case measured exactly that and passed against a
     * corner that grew by 25px. The piles are a column: the toss-in cell is its last row, so
     * the cell growing pushes the deck up, and the deck's own top is the thing to watch.
     */
    private fun deckTop(view: PlayerView): Int {
        var tall = 0
        runComposeUiTest {
            show(view)
            // The deck's description carries its count — "40 cards left in the deck" — so
            // this matches the sentence's tail rather than pinning a number the session's
            // deal would have to keep producing.
            val deck = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
                .fetchSemanticsNodes()
                .first { n -> n.config.getOrNull(SemanticsProperties.ContentDescription)
                    ?.firstOrNull()?.endsWith("cards left in the deck") == true }
            tall = deck.boundsInRoot.top.toInt()
        }
        return tall
    }

    private fun ComposeUiTest.nodes(description: String) =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .fetchSemanticsNodes()
            .count {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() ==
                    description
            }

    private fun ComposeUiTest.texts() =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text }

    private companion object {
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
    }
}

package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.game.portraitOrNull
import game.vinto.app.theme.VintoTheme
import game.vinto.client.LocalGameSession
import game.vinto.client.tableFor
import game.vinto.engine.PlayerView
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.RankPayload
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * When the table asks which *player*, the players are on the rail, not only on the felt.
 *
 * Two moves name a person rather than a card — an Ace, which makes somebody draw, and the
 * coalition choosing who plays its hand — and for both, the only way to answer was to tap
 * that seat's plate out on the felt. Nothing said so. The rail asked "Who draws a card?" and
 * then offered one button, "Put it down", so the screen read as a question with a single
 * answer and that answer was to give up; the plate did take a gold ring, but gold is also
 * what a plate wears when it is simply somebody's turn.
 *
 * Every other question the table asks is answered on the rail — a rank from the King's grid,
 * a card from the felt with the prompt naming it, a choice from the row of buttons — so this
 * one was the odd one out, and the report was the plain one: *now I have to understand that
 * I need to click on a person icon*.
 *
 * The felt still answers; the plates are still tappable, and tapping one is the better gesture
 * once you know it exists. What is new is that the rail says the choice is there.
 *
 * The Ace is the case here because it is the one that was reported and the one a player meets
 * every few turns. The coalition's question renders through the same `SeatGrid` from the same
 * `Table.seats`, and that it is *asked* — every non-caller seat on offer, the caller not among
 * them — is held next door by `HumanCoalitionLeaderTest`, which can reach a final round in a
 * line where this suite would have to build one.
 */
@OptIn(ExperimentalTestApi::class)
class SeatChoiceTest {

    @Test
    fun anAceOffersEveryOpponentOnTheRail() = runComposeUiTest {
        val view = runBlocking { aceInHand() }
        val opponents = view.players.filter { it.id != view.viewerId }.map { it.nickname }

        show(view)

        val pressable = pressableNames()
        opponents.forEach { name ->
            assertTrue(name in pressable, "$name cannot be pressed on the rail: $pressable")
        }
    }

    /**
     * Each seat brings its portrait, where it has one.
     *
     * The four the offline game deals do; a person who typed their name into a room does not,
     * and will not until seats can carry one over the wire. So the mapping has to be able to
     * say *no* — the felt's `portraitFor` cannot, it falls back to Leonardo, and a stranger
     * wearing Leonardo's face is worse than a stranger wearing none.
     */
    @Test
    fun theSeatsTheGameDealsHavePortraitsAndATypedNicknameDoesNot() {
        listOf("You", "Raph", "Mikey", "Don").forEach { name ->
            assertNotNull(portraitOrNull(name), "$name is a seat this game deals; it has a face")
        }
        assertNull(portraitOrNull("Volha"), "somebody who typed their name has no portrait yet")
        assertNull(portraitOrNull(""), "and neither has a seat with no name at all")
    }

    /**
     * And the Ace itself is not shown again while it asks.
     *
     * The player drew it, read it, and chose to play it — three screens with the card on them
     * — and the rail then spent its one card of room on it for a fourth. What is undecided at
     * this point is who draws, and the answer to that is three faces, not the Ace again.
     */
    @Test
    fun theAceIsNotRepeatedWhileItAsksWhoDraws() = runComposeUiTest {
        val view = runBlocking { aceInHand() }

        show(view)

        assertEquals(
            0,
            onAllNodesWithContentDescription("in play: A").fetchSemanticsNodes().size,
            "the rail is showing the Ace a fourth time instead of who it can name",
        )
    }

    /**
     * But the words still say what card is asking.
     *
     * Taking the picture away takes the identification with it unless the sentence picks it
     * up: "Force an opponent to draw a penalty card" is what the action does and never says
     * which card is doing it, or what playing it costs. The line reads the same whether the
     * card is drawn beside it or not.
     */
    @Test
    fun theAceIsStillNamedInWordsNowThatItsPictureIsGone() = runComposeUiTest {
        val view = runBlocking { aceInHand() }

        show(view)

        onNodeWithText(ACE_LINE).assertIsDisplayed()
    }

    /**
     * And the felt answers with a whole hand, not with a chip beside one.
     *
     * An Ace lands on a hand — the victim draws a card nobody chooses — so the hand is what it
     * is aimed at, and every card in it names its owner. That also lights the hand up, because
     * a card that can be touched wears the ring that says so, which is the part a player sees
     * before they read anything.
     */
    @Test
    fun everyCardOfEveryOpponentIsAWayOfNamingThem() = runComposeUiTest {
        val view = runBlocking { aceInHand() }
        val opponents = view.players.filter { it.id != view.viewerId }

        show(view)

        opponents.forEach { seat ->
            seat.cards.indices.forEach { position ->
                onNodeWithContentDescription("${seat.nickname}, card ${position + 1}")
                    .assertHasClickAction()
            }
        }
        view.players.first { it.id == view.viewerId }.cards.indices.forEach { position ->
            onNodeWithContentDescription("You, card ${position + 1}")
                .assertHasNoClickAction()
        }
    }

    /** And the way out is still there, so an Ace with nobody to name is not a dead end. */
    @Test
    fun theAceCanStillBePutDown() = runComposeUiTest {
        val view = runBlocking { aceInHand() }

        show(view)

        assertTrue("Put it down" in pressableNames(), "no way to abandon the Ace")
    }

    private fun ComposeUiTest.pressableNames(): Set<String> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
            .fetchSemanticsNodes()
            .mapNotNull(SemanticsNode::describedAs)
            .toSet()

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

    /** An Ace played and waiting to be pointed at somebody. */
    private suspend fun aceInHand(): PlayerView {
        val session = dealt()
        val me = session.playerId
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.ACE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
        session.dispatch(GameAction.UseCardAction(PlayerIdPayload(me)))
        return session.view.value
    }

    private suspend fun dealt(): LocalGameSession {
        val session = LocalGameSession(seed = SEED, difficulty = Difficulty.EASY)
        val me = session.playerId
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
        return session
    }

    private companion object {
        const val SEED = 12L
        const val ACE_LINE = "Ace, worth 1: Force an opponent to draw a penalty card"
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
    }
}

private fun SemanticsNode.describedAs(): String? =
    config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
        ?: config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text

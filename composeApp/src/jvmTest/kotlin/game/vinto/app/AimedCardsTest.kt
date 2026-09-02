package game.vinto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.game.TableLayout
import game.vinto.app.game.TableScreen
import game.vinto.app.game.TableState
import game.vinto.app.theme.VintoTheme
import game.vinto.client.LocalGameSession
import game.vinto.client.tableFor
import game.vinto.engine.CardView
import game.vinto.engine.PlayerView
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.RankPayload
import game.vinto.shapes.SelectActionTargetPayload
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * While a Jack or a Queen is being aimed, the rail shows what it is aimed *at*.
 *
 * The rail has room for one card beside its words, and for the whole of a two-card action it
 * spent that room on the card doing the aiming — a Queen the player had just chosen to play,
 * lying on the discard where the felt already draws it — while the two cards being swapped,
 * which is the decision, were one line of small text: "Chosen: You, card 3 and Don, card 5".
 * The web table this replaced drew the pair instead, each under its owner's name, and a
 * player who had used both said so: you could see more about the cards being changed.
 *
 * So the column holds the aim now: two slots, filled as they are chosen, showing the face for
 * a Queen — which looks before it swaps — and a back for a Jack, which does not. Both cases
 * are here because the difference between them is the whole of what the two cards are worth.
 */
@OptIn(ExperimentalTestApi::class)
class AimedCardsTest {

    @Test
    fun aQueenShowsTheFaceOfEveryCardItHasLookedAt() = runComposeUiTest {
        val (view, aimed) = aimedAtOneOpponentCard(Rank.QUEEN)
        val face = assertIs<CardView.Visible>(aimed, "a Queen looks, so its target carries a face")

        show(view)

        onNodeWithContentDescription(
            "aiming at ${face.card.rank.serialName}, worth ${face.card.value}",
        ).assertIsDisplayed()
        onNodeWithContentDescription(STILL_TO_CHOOSE).assertIsDisplayed()
    }

    @Test
    fun aJackShowsABackForTheCardItIsAimedAtBecauseItNeverLooked() = runComposeUiTest {
        val (view, aimed) = aimedAtOneOpponentCard(Rank.JACK)
        assertEquals(CardView.Hidden, aimed, "a Jack swaps blind: the target carries no face")

        show(view)

        onNodeWithContentDescription(AIMING_AT_A_BACK).assertIsDisplayed()
        onNodeWithContentDescription(STILL_TO_CHOOSE).assertIsDisplayed()
    }

    /** Each slot says whose hand it is in and which card along it, as the web table did. */
    @Test
    fun eachAimedCardIsNamedUnderTheCardItself() = runComposeUiTest {
        val (view, _) = aimedAtOneOpponentCard(Rank.QUEEN)
        val target = view.pendingAction!!.targets.single()
        val owner = view.players.first { it.id == target.playerId }

        show(view)

        onNodeWithText("${owner.nickname}, card ${target.position + 1}").assertIsDisplayed()
    }

    /**
     * And the card doing the aiming gives its column up.
     *
     * It is the one thing on the rail the player already knows — they chose to play it, and
     * it is lying on the discard where the felt draws it. Its own line of words stays.
     */
    @Test
    fun theQueenItselfNoLongerTakesTheColumn() = runComposeUiTest {
        val (view, _) = aimedAtOneOpponentCard(Rank.QUEEN)

        show(view)

        assertEquals(
            0,
            onAllNodesWithContentDescription("in play: Q").fetchSemanticsNodes().size,
            "the rail is showing the Queen instead of what it is pointed at",
        )
    }

    /**
     * And the words still name the Queen, which no longer has a picture in the rail.
     *
     * The rule line is what the card's own picture used to say by being there. Read on its
     * own — "Peek at any two cards from two different players" — it never says which card is
     * asking or what it costs to have played it.
     */
    @Test
    fun theQueenIsStillNamedInWordsNowThatItsPictureIsGone() = runComposeUiTest {
        val (view, _) = aimedAtOneOpponentCard(Rank.QUEEN)

        show(view)

        onNodeWithText(QUEEN_LINE).assertIsDisplayed()
    }

    /** Before either card is chosen, both slots are drawn empty: the action wants two. */
    @Test
    fun bothSlotsWaitToBeFilledBeforeAnythingIsChosen() = runComposeUiTest {
        val view = runBlocking { aiming(Rank.JACK).view.value }

        show(view)

        assertEquals(
            2,
            onAllNodesWithContentDescription(STILL_TO_CHOOSE).fetchSemanticsNodes().size,
            "a Jack asks for two cards and says so",
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

    /** A [rank] played and pointed at one opponent's first card: the view, and what it holds. */
    private fun aimedAtOneOpponentCard(rank: Rank): Pair<PlayerView, CardView> = runBlocking {
        val session = aiming(rank)
        val me = session.playerId
        val opponent = session.view.value.players.first { it.id != me }
        session.dispatch(
            GameAction.SelectActionTarget(SelectActionTargetPayload.Positional(me, opponent.id, 0)),
        )
        val view = session.view.value
        view to view.pendingAction!!.targets.single().card
    }

    private suspend fun aiming(rank: Rank): LocalGameSession {
        val session = LocalGameSession(seed = SEED, difficulty = Difficulty.EASY)
        val me = session.playerId
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(rank)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
        session.dispatch(GameAction.UseCardAction(PlayerIdPayload(me)))
        return session
    }

    private companion object {
        const val SEED = 77L
        const val QUEEN_LINE = "Queen, worth 10: Peek at any two cards from two different " +
            "players, then optionally swap them"
        const val STILL_TO_CHOOSE = "a card still to choose"
        const val AIMING_AT_A_BACK = "aiming at a face-down card"
        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
    }
}

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
import game.vinto.app.game.pileIsOnOffer
import game.vinto.app.theme.VintoTheme
import game.vinto.client.LocalGameSession
import game.vinto.client.tableFor
import game.vinto.engine.PlayerView
import game.vinto.engine.cardInPlay
import game.vinto.shapes.Card
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.RankPayload
import game.vinto.shapes.isActionable
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A card whose action is being played is on the pile, but it is not on **offer**.
 *
 * Reported from a phone: "I discard a 6, the bot draws an 8 after me, and I see another card
 * on the discard pile — it looks like another pending card." The card was the 8, and its being
 * there is right: a played card lies on the discard for the whole of its action, which is what
 * makes the toss-in window open for its rank legible (`cardInPlay`).
 *
 * What was wrong is *how* it lay there. The engine marks a card `played` when it records the
 * discard, which is when the action **finishes** — so for the whole of one the pile drew the
 * card ringed and called it "action unused", which is the one treatment that means "this is
 * yours to take under Option B". A ringed card that is not the one you discarded reads as
 * somebody's card in play, which is exactly what the report said it looked like.
 *
 * The rule, stated once: a card being played has spent its action, whoever is playing it.
 */
@OptIn(ExperimentalTestApi::class)
class PlayedCardIsNotOnOfferTest {

    @Test
    fun theCardBeingPlayedIsNotOfferedOnThePile() = runComposeUiTest {
        val view = playingAnEight()
        val eight = assertNotNull(view.cardInPlay, "the 8 is on the pile for the whole of its action")
        assertEquals(Rank.EIGHT, eight.rank)

        show(view)

        assertTrue(
            described("discarded 8"),
            "the pile does not say what is lying on it: ${descriptions()}",
        )
        assertTrue(
            !described("discarded 8, action unused"),
            "the pile offers a card whose action is being spent right now",
        )
    }

    /** And a card merely put down keeps the offer, which is the whole point of the ring. */
    @Test
    fun aCardPutDownUnplayedIsStillOnOffer() = runComposeUiTest {
        val view = discardingAnEight()
        assertEquals(Rank.EIGHT, view.discardTop?.rank, "the 8 was thrown away, not played")

        show(view)

        assertTrue(
            described("discarded 8, action unused"),
            "the next player cannot see that the 8 is theirs to take: ${descriptions()}",
        )
    }

    /**
     * The rule on its own, without a table around it.
     *
     * The third case is the one that keeps the ring honest while something is moving: the pile
     * draws its own top while a played card is in the air towards it (`pileFace`), and that
     * top may well be an unplayed action card the next player could still take.
     */
    @Test
    fun theRuleIsAboutTheCardBeingPlayed() {
        val eight = card(Rank.EIGHT, "e1", played = false)
        val queen = card(Rank.QUEEN, "q1", played = false)

        assertTrue(pileIsOnOffer(eight, inPlay = null), "a card put down unplayed is takeable")
        assertTrue(!pileIsOnOffer(eight, inPlay = eight), "the card being played is not")
        assertTrue(pileIsOnOffer(queen, inPlay = eight), "and the one underneath it still is")
        assertTrue(
            !pileIsOnOffer(card(Rank.EIGHT, "e2", played = true), inPlay = null),
            "a card whose action was spent stays spent",
        )
        assertTrue(
            !pileIsOnOffer(card(Rank.SIX, "s1", played = false), inPlay = null),
            "and a card with no action was never on offer",
        )
    }

    private fun card(rank: Rank, id: String, played: Boolean) = Card(
        id = id,
        rank = rank,
        value = 10,
        actionText = if (rank.isActionable()) "does something" else null,
        played = played,
    )

    /** A drawn 8, played for its action: the state the report was made in. */
    private fun playingAnEight(): PlayerView = runBlocking {
        val session = dealt()
        val me = session.playerId
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.EIGHT)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
        session.dispatch(GameAction.UseCardAction(PlayerIdPayload(me)))
        session.view.value
    }

    /** The same 8, put down without using it — still there for the taking. */
    private fun discardingAnEight(): PlayerView = runBlocking {
        val session = dealt()
        val me = session.playerId
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.EIGHT)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
        session.dispatch(GameAction.DiscardCard(PlayerIdPayload(me)))
        session.view.value
    }

    private suspend fun dealt(): LocalGameSession {
        val session = LocalGameSession(seed = SEED, difficulty = Difficulty.EASY)
        val me = session.playerId
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
        return session
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
            .filter { it.startsWith("discarded") }

    private companion object {
        /** The seed `CardInPlayTest` already walks to its first draw. */
        const val SEED = 8L

        val PHONE_W = 411.dp
        val PHONE_H = 740.dp
    }
}

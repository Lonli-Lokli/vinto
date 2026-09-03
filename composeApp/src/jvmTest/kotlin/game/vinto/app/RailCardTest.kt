package game.vinto.app

import game.vinto.app.game.railCard
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.CardView
import game.vinto.shapes.Card
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The card the rail holds up at the start of your turn is one you could take, or nothing.
 *
 * With nothing in play the rail falls back to the top of the pile. Reported from a phone: a
 * played 8, held up large beside "Your turn" with "Peek at one of your own cards" under it,
 * read as a card on offer — and it was not, its action was spent. The pile itself keeps its
 * face, because what went down is public; the rail is about your choice, and a card you
 * cannot choose is shown as a back.
 */
class RailCardTest {

    @Test
    fun aDiscardYouCannotTakeIsShownAsABack() = runTest {
        val view = atMyTurn().copy(discardTop = eight(played = true), discardCount = 1)
        assertEquals(CardView.Hidden, railCard(view, tableFor(view)))
    }

    @Test
    fun aDiscardYouCouldTakeIsShownFaceUp() = runTest {
        val live = eight(played = false)
        val view = atMyTurn().copy(discardTop = live, discardCount = 1)
        assertEquals(CardView.Visible(live), railCard(view, tableFor(view)))
    }

    @Test
    fun aPlainDiscardIsNeverOnOffer() = runTest {
        val four = Card(id = "4_0", rank = Rank.FOUR, value = 4, actionText = null, played = false)
        val view = atMyTurn().copy(discardTop = four, discardCount = 1)
        assertEquals(CardView.Hidden, railCard(view, tableFor(view)))
    }

    private suspend fun atMyTurn() = teachingSession().let { session ->
        val me = session.playerId
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
        session.view.value
    }

    private fun eight(played: Boolean) =
        Card(id = "8_0", rank = Rank.EIGHT, value = 8, actionText = "Peek at one of your own cards", played = played)
}

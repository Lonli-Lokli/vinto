package game.vinto.app

import game.vinto.app.game.railCard
import game.vinto.client.LocalGameSession
import game.vinto.client.Move
import game.vinto.client.tableFor
import game.vinto.client.teachingSession
import game.vinto.engine.CardView
import game.vinto.shapes.Card
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.RankPayload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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

    /**
     * Reported from a phone: a 7 played on your own hand turns a Joker over on the felt, and
     * the rail goes on holding up the 7 with "Seven, worth 7: Peek at one of your own cards"
     * under it. The look is the news — the 7 is what bought it, and the player has already
     * read that line twice by the time it pays out.
     */
    @Test
    fun aPeekOfYourOwnHoldsUpTheCardItTurnedOver() = runTest {
        val session = peeking(Rank.SEVEN)
        val view = session.view.value
        val looked = session.lookedAt()

        val held = assertIs<CardView.Visible>(railCard(view, tableFor(view)))
        assertEquals(looked, held.card, "the rail holds up what the peek turned over")
    }

    /** The same rule when the card belongs to somebody else: a 9 looks, and the look is the news. */
    @Test
    fun aPeekOfAnOpponentHoldsUpTheCardItTurnedOver() = runTest {
        val session = peeking(Rank.NINE)
        val view = session.view.value
        val looked = session.lookedAt()

        val held = assertIs<CardView.Visible>(railCard(view, tableFor(view)))
        assertEquals(looked, held.card, "the rail holds up what the peek turned over")
    }

    /** Deals [rank] to the player, plays its action, and aims it at the first card offered. */
    private suspend fun peeking(rank: Rank): LocalGameSession {
        val session = LocalGameSession(seed = 77L, difficulty = Difficulty.EASY)
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(session.playerId, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(session.playerId, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(rank)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.UseCardAction(PlayerIdPayload(session.playerId)))

        val aim = tableFor(session.view.value).taps.values.first()
        session.dispatch((aim as Move.Send).action)
        return session
    }

    /** The card the peek in progress turned over, read off the hand it is in. */
    private fun LocalGameSession.lookedAt(): Card {
        val view = view.value
        val target = view.pendingAction!!.targets.single()
        val seat = view.players.first { it.id == target.playerId }
        return assertIs<CardView.Visible>(seat.cards[target.position]).card
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

package game.vinto.client

import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.RankPayload
import game.vinto.shapes.actionIsLive
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Playing a card and putting it down are two different moves, and the table has to say which.
 *
 * They look identical the moment after: the same card, face up, on the same pile. But one of
 * them left its action on the table — the next player may take that card and play it instead
 * of drawing — and the other spent it. Somebody watching the turn from across the table has to
 * be able to tell, because it changes what they can do next.
 *
 * In the moment, the difference is the flight: a played card travels lit. Afterwards it is the
 * ring the pile draws round a card whose action nobody has used, and this is the fact that
 * ring is drawn from.
 */
class LiveActionTest {

    @Test
    fun aCardPutDownUnusedKeepsItsAction() = runTest {
        val session = dealt()
        val me = session.playerId

        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.NINE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
        session.dispatch(GameAction.DiscardCard(PlayerIdPayload(me)))

        val top = assertNotNull(session.view.value.discardTop)
        assertEquals(Rank.NINE, top.rank)
        assertTrue(top.actionIsLive(), "nobody used it, so the next player may")
    }

    @Test
    fun aCardPlayedForItsActionIsSpent() = runTest {
        val session = dealt()
        val me = session.playerId

        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.NINE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
        session.dispatch(GameAction.UseCardAction(PlayerIdPayload(me)))
        val opponent = session.view.value.players.first { it.id != me }.id
        session.dispatch(
            GameAction.SelectActionTarget(
                game.vinto.shapes.SelectActionTargetPayload.Positional(me, opponent, 0),
            ),
        )
        session.dispatch(GameAction.ConfirmPeek(PlayerIdPayload(me)))

        val top = assertNotNull(session.view.value.discardTop)
        assertEquals(Rank.NINE, top.rank, "the same card, on the same pile")
        assertFalse(top.actionIsLive(), "but its action has been used")
    }

    /** A number card has no action to leave behind, so it never carries the mark. */
    @Test
    fun aPlainCardIsNeverLive() = runTest {
        val session = dealt()
        val me = session.playerId

        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.FIVE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
        session.dispatch(GameAction.DiscardCard(PlayerIdPayload(me)))

        assertFalse(assertNotNull(session.view.value.discardTop).actionIsLive())
    }

    private suspend fun dealt(): LocalGameSession {
        val session = LocalGameSession(seed = 5L, difficulty = Difficulty.EASY)
        val me = session.playerId
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
        return session
    }
}

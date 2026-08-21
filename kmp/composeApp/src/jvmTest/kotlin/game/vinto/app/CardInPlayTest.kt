package game.vinto.app

import game.vinto.app.game.pileFace
import game.vinto.engine.cardInPlay
import game.vinto.engine.discardTop
import game.vinto.engine.discardUnder
import game.vinto.shapes.DeclareKingActionPayload
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.RankPayload
import game.vinto.client.LocalGameSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * If the table is asking for a rank to be tossed in, that rank is lying on the pile.
 *
 * It was not. Playing a King opens the toss-in window for Kings at the moment the action
 * starts, and the engine keeps the card in `pendingAction` until the action finishes — so for
 * the whole of a King's declaration the table showed "Toss-in K" over an empty discard, and
 * the card itself had been lifted to the middle for half a second and then vanished. The
 * player's own account of it was that the King animated to the discard and disappeared.
 *
 * A card whose action is being played is *on* the pile. That is where it is, and it is why
 * the window is open.
 */
class CardInPlayTest {

    @Test
    fun aWindowIsNeverOpenOverAnEmptyPile() = runTest {
        val session = dealt()
        val me = session.playerId

        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.KING)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
        session.dispatch(GameAction.UseCardAction(PlayerIdPayload(me)))
        session.dispatch(GameAction.DeclareKingAction(DeclareKingActionPayload(me, Rank.SEVEN)))

        val view = session.view.value
        val open = assertNotNull(view.activeTossIn, "the window is open for the King")

        val showing = assertNotNull(
            pileFace(view.discardTop, view.discardUnder, view.cardInPlay, landing = false),
            "and the pile is showing the card it is open for",
        )
        assertEquals(open.ranks.single(), showing.rank, "which is the King")
    }

    /** A card merely *drawn* is not on the pile: it is in front of you, being decided about. */
    @Test
    fun aDrawnCardIsNotOnThePileUntilItIsPlayed() = runTest {
        val session = dealt()
        val me = session.playerId

        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.SEVEN)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))

        val drawn = session.view.value
        assertNull(drawn.cardInPlay, "a card being chosen about is not in play")
        assertNull(
            pileFace(drawn.discardTop, drawn.discardUnder, drawn.cardInPlay, landing = false),
            "so the pile is still empty",
        )

        session.dispatch(GameAction.UseCardAction(PlayerIdPayload(me)))

        val played = session.view.value
        assertEquals(Rank.SEVEN, assertNotNull(played.cardInPlay).rank, "playing it puts it down")
    }

    private suspend fun dealt(): LocalGameSession {
        val session = LocalGameSession(seed = 8L, difficulty = Difficulty.EASY)
        val me = session.playerId
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
        return session
    }
}

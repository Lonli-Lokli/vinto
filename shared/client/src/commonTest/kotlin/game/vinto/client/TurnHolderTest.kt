package game.vinto.client

import game.vinto.engine.turnHolderId
import game.vinto.shapes.ActiveTossIn
import game.vinto.shapes.Rank
import game.vinto.engine.projectView
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * A toss-in happens inside somebody's turn, and the table should go on saying whose.
 *
 * The engine points `currentPlayerIndex` at whoever threw a card in while that card's action
 * resolves, because it is their action to aim. Read literally by the screen, that moved the
 * turn: a player put a King down, the seat two along threw one in, and the ring jumped to
 * them — from the other side, "why is it Mikey's turn after me?" It was not; it was still
 * theirs, with a window open on it.
 */
class TurnHolderTest {

    @Test
    fun theTurnStaysWithThePlayerWhoseWindowItIs() = runTest {
        val session = LocalGameSession(seed = 8L, difficulty = Difficulty.EASY)
        val me = session.playerId
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))

        val view = projectView(session.state, me)
        val third = view.players[2].id

        // A window open on this player's turn, with the seat two along resolving what they
        // threw into it — which is exactly the position the report came from.
        val during = view.copy(
            currentPlayerIndex = 2,
            activeTossIn = ActiveTossIn(
                ranks = listOf(Rank.KING),
                initiatorId = me,
                originalPlayerIndex = 0,
                participants = listOf(third),
                queuedActions = emptyList(),
                waitingForInput = true,
                playersReadyForNextTurn = emptyList(),
            ),
        )

        assertEquals(me, during.turnHolderId, "it is still the turn it was")
        assertEquals(
            third,
            during.players[during.currentPlayerIndex].id,
            "even though the seat two along is the one acting",
        )
    }

    /** With no window open, the turn is simply whose it is. */
    @Test
    fun outsideAWindowTheTurnIsTheCurrentPlayer() = runTest {
        val session = LocalGameSession(seed = 8L, difficulty = Difficulty.EASY)
        val view = projectView(session.state, session.playerId)

        assertEquals(
            assertNotNull(view.players.getOrNull(view.currentPlayerIndex)).id,
            view.turnHolderId,
        )
    }
}

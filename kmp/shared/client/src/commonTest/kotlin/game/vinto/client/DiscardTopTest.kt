package game.vinto.client

import game.vinto.engine.discardTop
import game.vinto.engine.discardUnder
import game.vinto.engine.projectView
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The card the table shows on the discard pile is the card that is on the discard pile.
 *
 * Stated that plainly it reads as a tautology, and it was false for the whole of the port:
 * the pile is kept top-first and the screen read the *last* element, so from the second
 * discard of a round onwards the table showed the card the round started with and went on
 * showing it. It survived this long because the first discard of a round is both the first
 * and the last element, so every fresh deal looks right for exactly one turn.
 *
 * The invariant below is the one that catches it without knowing anything about which card
 * should be where: when the engine opens a toss-in window it names the rank from the real
 * top of the pile, so the window and the pile have to agree. They did not — a Queen went
 * down, the window opened for Queens, and the pile displayed a two.
 */
class DiscardTopTest {

    @Test
    fun theTossInWindowAndThePileAgreeAboutWhatWentDown() = runTest {
        val session = dealt()
        var windows = 0

        repeat(TURNS) {
            if (session.isOver) return@repeat
            val view = projectView(session.state, session.playerId)

            view.activeTossIn?.let { window ->
                windows++
                assertEquals(
                    window.ranks.toSet(),
                    setOfNotNull(view.discardTop?.rank),
                    "the window is open for what is showing on the pile, and the pile has " +
                        "${view.discardPile.size} cards on it",
                )
            }
            session.play()
        }

        assertTrue(windows > 1, "the game got past its first discard: $windows windows")
    }

    /** And the two ends of a pile are not the same end, which is the whole of the bug. */
    @Test
    fun theTopAndTheBottomOfThePileAreDifferentCards() = runTest {
        val session = dealt()
        repeat(TURNS) { if (!session.isOver) session.play() }

        val view = projectView(session.state, session.playerId)
        assertTrue(view.discardPile.size > 1, "a pile worth reading: ${view.discardPile.size}")

        val top = assertNotNull(view.discardTop)
        assertEquals(view.discardPile.first().id, top.id, "the top is the first element")
        assertEquals(view.discardPile[1].id, assertNotNull(view.discardUnder).id)
        assertTrue(top.id != view.discardPile.last().id, "and not the last one")
    }

    private suspend fun dealt(): LocalGameSession = teachingSession().also { session ->
        val me = session.playerId
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
    }

    /** One move, whatever the table is asking for: draw, put it down, and let the window go. */
    private suspend fun LocalGameSession.play() {
        val me = playerId
        dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
        dispatch(GameAction.DiscardCard(PlayerIdPayload(me)))
        dispatch(GameAction.PlayerTossInFinished(PlayerIdPayload(me)))
    }

    private companion object {
        const val TURNS = 10
    }
}

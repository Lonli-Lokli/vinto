package game.vinto.client

import game.vinto.engine.projectView
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
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
                val top = view.discardTop?.rank
                assertTrue(
                    top != null && top in window.ranks,
                    "the window is open for what is showing on the pile ($top of ${window.ranks}), " +
                        "and the pile has ${view.discardCount} cards on it",
                )
                // A King that names a card right opens the window for both: the King goes
                // down, the named card goes down on top of it, and the pile can only show
                // one of them. Nothing else opens a window for two ranks at once.
                if (window.ranks.size > 1) {
                    assertTrue(Rank.KING in window.ranks, "two ranks in a window not opened by a King: $window")
                }
            }
            session.play()
        }

        assertTrue(windows > 1, "the game got past its first discard: $windows windows")
    }

    /**
     * And the pile a client is sent is one card deep, whatever is under it.
     *
     * The whole pile used to arrive, which is a perfect record of a round in a game about
     * remembering one. What is public about a discard is its top card and its thickness.
     */
    @Test
    fun onlyTheTopOfThePileIsSent() = runTest {
        val session = dealt()
        repeat(TURNS) { if (!session.isOver) session.play() }

        val view = projectView(session.state, session.playerId)
        assertTrue(view.discardCount > 1, "a pile worth covering up: ${view.discardCount}")

        val top = assertNotNull(view.discardTop)
        assertEquals(session.state.discardPile.peekTop()?.id, top.id, "and it is the top one")
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

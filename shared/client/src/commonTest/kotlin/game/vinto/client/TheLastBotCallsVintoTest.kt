package game.vinto.client

import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rig that makes the last bot call Vinto, whatever it holds.
 *
 * The coalition's final round is the part of this game hardest to get at deliberately: it needs a
 * bot to *decide* to call, which needs a good hand and a search that agrees — so reaching it
 * means playing rounds and hoping, and the round you get by playing is usually the one you called
 * yourself, which is the other side of the table entirely.
 *
 * With the rig on, the **last** bot calls the moment its turn comes. That seat is the one before
 * the person's turn comes round again, so the person is first in the coalition — which is the
 * position worth testing. Asked for in as many words: *"3rd bot (so I will be next after him)
 * declares vinto regardless of his cards"*.
 *
 * It is off by default, local by construction — this is `LocalGameSession`, and a room deals its
 * own bots — and switched on only from a `src/debug` source set, so a release build cannot reach
 * it however the flag is threaded.
 */
class TheLastBotCallsVintoTest {

    @Test
    fun theLastBotCallsAsSoonAsItsTurnComes() = runTest(timeout = WHOLE_GAME) {
        val session = LocalGameSession(
            seed = SEED,
            difficulty = Difficulty.EASY,
            theLastBotCallsVinto = true,
        )
        val me = session.playerId
        openTheRound(session, me)

        // One turn of the person's, spent, and the bots take it from there.
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
        session.dispatch(GameAction.DiscardCard(PlayerIdPayload(me)))
        closeAnyWindow(session, me)

        val view = session.view.value
        val lastBot = view.players.last { it.isBot }.id
        assertEquals(lastBot, view.vintoCallerId, "the last bot did not call on its turn")
        assertEquals(GamePhase.FINAL, view.phase, "the call did not start the final round")

        // Which puts the person first in the coalition — the whole point of rigging *that* seat
        // rather than any other. The claim is about the order of play, not about where the index
        // happens to rest the instant the call lands: the seat after the last is the first, and
        // the first is the person's.
        val seats = view.players.map { it.id }
        assertEquals(seats.last(), lastBot, "the last bot is not the last seat")
        assertEquals(me, seats.first(), "the person does not follow the seat that called")
        assertTrue(me != lastBot, "the person called it themselves")
    }

    @Test
    fun withoutTheRigNobodyCallsJustBecauseItIsTheirTurn() = runTest(timeout = WHOLE_GAME) {
        // Off by default, and the default is what ships. The same deal, the same moves: whether
        // anybody calls is the search's business again.
        val session = LocalGameSession(seed = SEED, difficulty = Difficulty.EASY)
        val me = session.playerId
        openTheRound(session, me)

        session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
        session.dispatch(GameAction.DiscardCard(PlayerIdPayload(me)))
        closeAnyWindow(session, me)

        assertNull(session.view.value.vintoCallerId, "somebody called Vinto with the rig off")
    }

    /** The two peeks every seat is dealt, spent, which is what starts the round. */
    private suspend fun openTheRound(session: LocalGameSession, me: String) {
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
    }

    /** The bots stop at the window a discard opens; it is the person's to close. */
    private suspend fun closeAnyWindow(session: LocalGameSession, me: String) {
        var guard = 0
        while (session.view.value.subPhase == GameSubPhase.TOSS_QUEUE_ACTIVE && guard++ < WINDOWS) {
            session.dispatch(GameAction.PlayerTossInFinished(PlayerIdPayload(me)))
        }
    }

    private companion object {
        const val SEED = 20_260_915L

        /** A window per seat between the person's turn and the last bot's, and no more. */
        const val WINDOWS = 6
    }
}

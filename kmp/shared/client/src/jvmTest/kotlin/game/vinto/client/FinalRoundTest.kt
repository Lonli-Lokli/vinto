package game.vinto.client

import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.RankPayload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Calling Vinto has to end the round.
 *
 * The rules put the call at the end of your own turn, which is the toss-in window — so that is
 * where the table offers it, and it is the path a player actually takes. It is also the path
 * that was never tested: the earlier tests called Vinto at the start of a turn, which the
 * engine treats differently (you still owe the turn you just declared the end of).
 *
 * JVM only, for the same reason as `FinishesTest`: a sweep over sixty deals is fast here and
 * slow on a simulator, and what it proves is about the rules rather than about the platform.
 */
class FinalRoundTest {

    private suspend fun callVintoAt(seed: Long): LocalGameSession {
        val session = LocalGameSession(seed = seed, difficulty = Difficulty.EASY)
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(session.playerId, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(session.playerId, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(session.playerId)))

        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.NINE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.DiscardCard(PlayerIdPayload(session.playerId)))

        val window = tableFor(session.view.value)
        window.choices.firstOrNull { it.label == "Call Vinto" }?.let {
            session.dispatch((it.move as Move.Send).action)
        }
        return session
    }

    @Test
    fun callingVintoInTheTossInWindowPlaysTheRoundOut() = runTest {
        val stalled = mutableListOf<String>()

        for (seed in 1L..60L) {
            val session = callVintoAt(seed)
            if (session.isOver) continue

            val view = session.view.value
            stalled += "seed $seed: phase=${view.phase} sub=${view.subPhase} " +
                "current=${view.players[view.currentPlayerIndex].nickname} " +
                "leader=${view.coalitionLeaderId} pending=${view.pendingAction?.playerId}"
        }

        assertTrue(stalled.isEmpty(), "rounds that never finished:\n${stalled.joinToString("\n")}")
    }
}

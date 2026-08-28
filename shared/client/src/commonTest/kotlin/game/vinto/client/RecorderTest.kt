package game.vinto.client

import game.vinto.engine.GameEngine
import game.vinto.engine.ReduceResult
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.Rank
import game.vinto.shapes.RankPayload
import game.vinto.shapes.actorId
import game.vinto.shapes.hashGameState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A bug report has to be reproducible or it is a paragraph.
 *
 * The test that matters is not "it produced some JSON" — it is that the JSON *replays*: run
 * the recorded actions through the engine from the recorded start and you land on the recorded
 * hash, action for action. That is the same check `CorpusReplayTest` runs against the fifty
 * TypeScript recordings, which is the point: a report from a player's phone is the same kind
 * of object, and drops into the same harness.
 */
class RecorderTest {

    private suspend fun played(): LocalGameSession {
        val session = LocalGameSession(seed = 4242L, difficulty = Difficulty.EASY)
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(session.playerId, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(session.playerId, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.SetNextDrawCard(RankPayload(Rank.NINE)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        session.dispatch(GameAction.DiscardCard(PlayerIdPayload(session.playerId)))
        return session
    }

    @Test
    fun aReportReplaysActionForAction() = runTest {
        val report = played().report(at = "2026-08-20T00:00:00Z", label = "test")

        assertTrue(report.actions.size > FEW, "it recorded the game: ${report.actions.size}")

        var state = report.initialState
        report.actions.forEachIndexed { index, recorded ->
            state = when (val result = GameEngine.reduce(state, recorded.action)) {
                is ReduceResult.Success -> result.state
                is ReduceResult.Failure -> error("action $index was refused: ${result.reason}")
            }
            assertEquals(
                recorded.stateHash,
                hashGameState(state),
                "replay diverged at action $index (${recorded.action.type})",
            )
        }

        assertEquals(report.finalStateHash, hashGameState(state), "and ends where it ended")
    }

    /** The bots' moves are in it too, or the report replays a game nobody played. */
    @Test
    fun theBotsMovesAreRecordedAsWell() = runTest {
        val session = played()
        val report = session.report(at = "2026-08-20T00:00:00Z", label = "test")

        val mine = report.actions.count { it.action.actorId == session.playerId }
        assertTrue(mine < report.actions.size, "somebody else moved too: $mine of ${report.actions.size}")
    }

    @Test
    fun aReportCarriesWhatItNeedsToBeDealtAgain() = runTest {
        val report = played().report(at = "2026-08-20T00:00:00Z", label = "stuck on turn 3")

        assertEquals(4242L, report.settings.seed)
        assertEquals(Difficulty.EASY, report.settings.difficulty)
        assertEquals("stuck on turn 3", report.meta.label)
        assertEquals(Recording.FORMAT, report.formatVersion, "the format the harness reads")
        assertTrue(report.toJson().contains("\"initialState\""), "and it serialises")
    }

    private companion object {
        const val FEW = 5
    }
}

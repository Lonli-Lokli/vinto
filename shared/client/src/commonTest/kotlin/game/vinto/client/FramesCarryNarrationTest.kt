package game.vinto.client

import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every line the session writes to its log also rides on the frame of the move it is about.
 *
 * The screen used to read the log live and the felt stepped through frames, so the words ran
 * ahead of the pictures. The frame is the unit the screen steps by; carrying its narration
 * on it is what lets the rail keep pace with the table — and this holds the two apart from
 * drifting: the log and the frames' lines, in order, are one and the same.
 */
class FramesCarryNarrationTest {

    @Test
    fun theLogAndTheFramesSayTheSameThingsInTheSameOrder() = runTest {
        val session = LocalGameSession(seed = 21L, difficulty = Difficulty.EASY)
        val me = session.playerId

        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))

        val onFrames = session.frames.replayCache.flatten().flatMap { it.said }
        assertTrue(onFrames.isNotEmpty(), "a batch of moves carried no narration at all")
        assertEquals(
            session.log.value.takeLast(onFrames.size),
            onFrames,
            "the frames' lines are not the log's tail, in order",
        )
    }
}

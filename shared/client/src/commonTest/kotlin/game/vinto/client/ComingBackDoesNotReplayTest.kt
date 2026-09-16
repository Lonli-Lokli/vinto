package game.vinto.client

import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A screen that leaves the table and comes back does not watch the last move a second time.
 *
 * Reported 2026-09-16: *"after bot turn I opened settings to report, return back to game and
 * saw again move from bot."* Settings is a different branch of the navigation `when`, so the
 * table leaves the composition entirely and everything the stage remembers goes with it. When
 * the player comes back, a new stage subscribes to the same session — and the frames a bot's
 * turn produced were handed to it all over again, so the move played out on screen a second
 * time, minutes after it had happened.
 *
 * Frames are **events, not a log**. The narration beside them is a log and is replayed on
 * purpose, generously — a strip that arrives mid-conversation should still show it — but a
 * frame is a thing that happens once, and handing it to a second subscriber is showing the
 * past as though it were the present. So the stream buffers a batch until somebody takes it
 * and never gives the same batch out twice, which is the one shape that also keeps what the
 * replay was there for: a stage that subscribes a moment late still gets everything it missed,
 * and now more than one batch of it.
 */
class ComingBackDoesNotReplayTest {

    private suspend fun dealt(session: LocalGameSession) {
        val me = session.playerId
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))
    }

    @Test
    fun theSameMoveIsNotHandedToASecondStage() = runTest {
        val session = LocalGameSession(seed = 7L, difficulty = Difficulty.EASY)
        dealt(session)

        // The stage that was on screen when the move happened.
        val watched = mutableListOf<List<Frame>>()
        val stage: Job = backgroundScope.launch { session.frames.collect { watched += it } }
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        runCurrent()
        assertTrue(watched.isNotEmpty(), "the first stage watched nothing, so this proves nothing")

        // Opening the settings takes the table out of the composition with it.
        stage.cancel()
        runCurrent()

        // Coming back builds a new one against the same session.
        val cameBack = mutableListOf<List<Frame>>()
        backgroundScope.launch { session.frames.collect { cameBack += it } }
        runCurrent()

        assertEquals(
            emptyList(),
            cameBack,
            "the move was played to the screen again on the way back from the settings",
        )
    }

    @Test
    fun aStageThatSubscribesLateStillGetsWhatItMissed() = runTest {
        val session = LocalGameSession(seed = 7L, difficulty = Difficulty.EASY)
        dealt(session)

        // Nobody is watching yet — the reason the stream buffers at all.
        session.dispatch(GameAction.DrawCard(PlayerIdPayload(session.playerId)))
        runCurrent()

        val late = mutableListOf<Frame>()
        backgroundScope.launch { session.frames.collect { late += it } }
        runCurrent()

        assertTrue(late.isNotEmpty(), "a stage that arrived late was shown nothing at all")
    }
}

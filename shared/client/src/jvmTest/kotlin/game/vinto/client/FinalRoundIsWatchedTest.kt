package game.vinto.client

import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Calling Vinto is watched, not skipped.
 *
 * It was skipped. `AnimationQueue` drops a whole batch that costs more than its budget — the
 * right rule for a client that fell behind, and the wrong one here, because a Vinto call
 * submits the call **and the three bots' entire last turns** as one batch. Eight is about two
 * turns' worth; a final round is three. So the batch went over, the queue cleared it, and the
 * player went from tapping "Call Vinto" to reading "Round over" with nothing in between —
 * having never seen the endgame their whole hand had been played for. Reported from a phone.
 *
 * This is the shape of that failure, checked where it can be checked: the batch a Vinto call
 * produces is bigger than two turns, and the queue must still play it.
 *
 * The queue's own case for a genuine backlog is next door in `AnimationQueueTest` and is
 * unchanged — dropping a reconnect's worth of history is still right. What changed is that
 * "one dispatch" and "how far behind the client is" stopped being the same number.
 */
class FinalRoundIsWatchedTest {

    @Test
    fun theWholeFinalRoundIsHandedToTheTable() = runTest(timeout = WHOLE_GAME) {
        val session = LocalGameSession(seed = 20260819L, difficulty = Difficulty.EASY)
        val me = session.playerId

        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(me, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(me)))

        // Turns until the call is legal, which is at the end of one of the player's own —
        // the validator puts it in the toss-in window the player's own discard opened.
        var called = false
        repeat(TURNS) {
            if (called || session.isOver) return@repeat
            session.dispatch(GameAction.DrawCard(PlayerIdPayload(me)))
            session.dispatch(GameAction.DiscardCard(PlayerIdPayload(me)))
            if (session.dispatch(GameAction.CallVinto(PlayerIdPayload(me))) == null) {
                called = true
            } else {
                session.dispatch(GameAction.PlayerTossInFinished(PlayerIdPayload(me)))
            }
        }
        assertTrue(called, "never managed to call Vinto, so this case tested nothing")

        // `frames` replays its last batch, which is the one the call produced.
        val batch = session.frames.first()

        assertTrue(session.isOver, "the call did not finish the round: ${session.state.phase}")
        assertTrue(batch.isNotEmpty(), "the call produced no frames at all")

        val moves = batch.count { it.hasSomethingToSee }
        assertTrue(
            moves > TWO_TURNS,
            "a final round is only $moves moves; this case is no longer about a long batch",
        )

        // The queue the table actually uses, asked the same question the table asks it.
        val queue = AnimationQueue<Frame>(takesTime = { it.hasSomethingToSee })
        queue.submit(batch)
        assertTrue(
            queue.pending > 0,
            "the final round was dropped whole: the player taps Call Vinto and reads the score",
        )
        assertTrue(
            queue.pending == batch.size,
            "part of the final round was dropped — half an endgame is worse than none",
        )
    }

    private companion object {
        const val TURNS = 12

        /** What the old budget was, and what a final round has to be allowed to exceed. */
        const val TWO_TURNS = 8
    }
}

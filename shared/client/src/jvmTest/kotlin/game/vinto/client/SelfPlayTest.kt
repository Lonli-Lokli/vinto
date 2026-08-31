package game.vinto.client

import game.vinto.shapes.Difficulty
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * The public drive does what the internal one does.
 *
 * `FinishesTest` proves games terminate when driven over the internal state; this proves the
 * helper the UI test leans on — the one built from public surface — reaches the same end,
 * and emits the frames the screen will be walking while it does.
 */
class SelfPlayTest {

    @Test
    fun aSessionPlaysItselfToScoring() = runTest(timeout = LONG) {
        val session = LocalGameSession(seed = SEED, difficulty = Difficulty.EASY)

        assertTrue(session.playItselfOut(seed = SEED), "the game never reached its scoring")
        assertTrue(session.isOver, "over means over")

        // The replay cache rather than a collector: the drive is synchronous on this
        // dispatcher, so a launched collector would not get a turn until it was over anyway.
        assertTrue(
            session.frames.replayCache.flatten().isNotEmpty(),
            "a played game emitted no frames for the screen",
        )
    }

    private companion object {
        const val SEED = 20260826L
        val LONG = 2.minutes
    }
}

package game.vinto.client

import game.vinto.engine.calculateFinalScores
import game.vinto.engine.calculateRoundPoints
import game.vinto.shapes.Difficulty
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * The client-side pay computation against the engine's own, over whole played games.
 *
 * [roundPoints] deliberately duplicates a rule — a remote client has only public facts at
 * the moment a round ends — and a duplicated rule needs a leash. Real games rather than
 * hand-picked tables, so the caller-wins, tie and coalition-wins arms are all exercised by
 * whatever the seeds actually produce, and the test says which arm it saw.
 */
class OnlineScoreTest {

    @Test
    fun theDerivedPayMatchesTheEngines() = runTest(timeout = LONG) {
        var called = 0
        for (seed in 1L..SEEDS) {
            val session = LocalGameSession(seed = seed, difficulty = Difficulty.EASY)
            assertTrue(session.playItselfOut(seed), "seed $seed never finished")

            val state = session.state
            val scores = calculateFinalScores(state.players, state.vintoCallerId)
            if (state.vintoCallerId != null) called++

            assertEquals(
                calculateRoundPoints(state.players, state.vintoCallerId),
                roundPoints(scores, state.vintoCallerId),
                "seed $seed: the duplicate drifted from the engine",
            )
        }
        assertTrue(called > 0, "no seed produced a Vinto call — the interesting arms went untested")
    }

    private companion object {
        const val SEEDS = 12L
        val LONG = 5.minutes
    }
}

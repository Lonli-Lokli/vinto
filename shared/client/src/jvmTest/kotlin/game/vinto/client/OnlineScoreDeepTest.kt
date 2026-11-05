package game.vinto.client

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * `OnlineScoreTest` over twelve whole games rather than four.
 *
 * Its own class so the deep suite (`-Pgames.suite=deep`, gradle/tests.gradle.kts) runs it;
 * the everyday suite keeps the same assertions on the first four seeds.
 */
class OnlineScoreDeepTest {

    @Test
    fun theDerivedPayMatchesTheEngines() = runTest(timeout = OnlineScoreTest.LONG) {
        OnlineScoreTest().theDerivedPayMatchesTheEnginesOver(1L..SEEDS)
    }

    private companion object {
        const val SEEDS = 12L
    }
}

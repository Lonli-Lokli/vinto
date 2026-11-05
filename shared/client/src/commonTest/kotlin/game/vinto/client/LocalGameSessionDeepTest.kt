package game.vinto.client

import kotlin.test.Test

/**
 * `LocalGameSessionTest`'s slowest tests, so they run in the deep suite (`-Pgames.suite=deep`,
 * gradle/tests.gradle.kts): the same seed, 19 s on Kotlin/JS and 11 s on Wasm, and the solo round, over the
 * 10 s guard twice on the JVM on a loaded Mac. Each body stays in `LocalGameSessionTest`, unchanged, under the
 * same name with `Run` added.
 */
class LocalGameSessionDeepTest {

    @Test
    fun aSoloGameRunsFromTheDealToScoring() = LocalGameSessionTest().aSoloGameRunsFromTheDealToScoringRun()

    @Test
    fun theSameSeedPlaysTheSameGame() = LocalGameSessionTest().theSameSeedPlaysTheSameGameRun()
}

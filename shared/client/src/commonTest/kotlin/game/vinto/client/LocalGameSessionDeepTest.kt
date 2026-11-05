package game.vinto.client

import kotlin.test.Test

/**
 * `LocalGameSessionTest`'s slowest test, 19 s on Kotlin/JS and 11 s on Wasm, so it runs in the deep suite
 * (`-Pgames.suite=deep`, gradle/tests.gradle.kts). Its body stays in `LocalGameSessionTest`, unchanged, under
 * the same name with `Run` added.
 */
class LocalGameSessionDeepTest {

    @Test
    fun theSameSeedPlaysTheSameGame() = LocalGameSessionTest().theSameSeedPlaysTheSameGameRun()
}

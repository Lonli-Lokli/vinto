package game.vinto.client

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * `LocalGameSessionTest`'s same-seed games, played whole.
 *
 * Two whole games are 19 s on Kotlin/JS, 11 s on Wasm and 78 s on the iOS simulator, so they run
 * in the deep suite (`-Pgames.suite=deep`, gradle/tests.gradle.kts). The everyday suite compares
 * the games' first moves.
 */
class LocalGameSessionDeepTest {

    @Test
    fun theSameSeedPlaysTheSameGame() = runTest(timeout = LocalGameSessionTest.A_WHOLE_ROUND) {
        with(LocalGameSessionTest()) { theSameSeedPlaysTheSameGameOver() }
    }
}

package game.vinto.client

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * `BotDispatcherTest` over a whole game, which it also asserts finished.
 *
 * A whole game is 10 to 13 s on Kotlin/JS and Wasm and 40 to 79 s on the iOS simulator, so it
 * runs in the deep suite (`-Pgames.suite=deep`, gradle/tests.gradle.kts). The everyday suite
 * checks the dispatcher on the game's first moves.
 */
class BotDispatcherDeepTest {

    @Test
    fun theBotsThinkOnTheDispatcherTheyWereGiven() = runTest(timeout = WHOLE_GAME) {
        BotDispatcherTest().theBotsThinkOnTheDispatcherTheyWereGivenOver(moveLimit = null)
    }

    @Test
    fun withoutOneTheBotsThinkWhereTheyAreCalled() = runTest(timeout = WHOLE_GAME) {
        BotDispatcherTest().withoutOneTheBotsThinkWhereTheyAreCalledOver(moveLimit = null)
    }
}

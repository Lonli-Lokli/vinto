package game.vinto.client

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * `FinalRoundTest`'s call in the toss-in window, swept over sixty deals.
 *
 * Most of two minutes on the JVM, so it runs in the deep suite (`-Pgames.suite=deep`,
 * gradle/tests.gradle.kts); the everyday suite makes the same call on three deals.
 */
class FinalRoundDeepTest {

    @Test
    fun callingVintoInTheTossInWindowPlaysTheRoundOut() = runTest(timeout = WHOLE_GAME) {
        FinalRoundTest().callingVintoInTheTossInWindowPlaysTheRoundOutOver(1L..DEALS)
    }

    private companion object {
        const val DEALS = 60L
    }
}

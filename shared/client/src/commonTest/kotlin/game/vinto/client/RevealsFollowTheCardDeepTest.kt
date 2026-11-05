package game.vinto.client

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * `RevealsFollowTheCardTest`'s whole games over all three seeds.
 *
 * Three whole games are 12 s on the JVM, 17 s on Kotlin/JS and 102 s on the iOS simulator, so
 * they run in the deep suite (`-Pgames.suite=deep`, gradle/tests.gradle.kts). The everyday suite
 * plays the first seed with every assertion.
 */
class RevealsFollowTheCardDeepTest {

    @Test
    fun theStandingRevealsAreExactlyTheRevealedCardsStillInAHand() = runTest(timeout = WHOLE_GAME) {
        RevealsFollowTheCardTest().theStandingRevealsAreExactlyTheRevealedCardsStillInAHandOver(
            RevealsFollowTheCardTest.SEEDS,
        )
    }
}

package game.vinto.client

import kotlin.test.Test

/**
 * `RevealsFollowTheCardTest`'s slowest test, 17 s on Kotlin/JS, 15 s on Wasm and 12 s on the JVM, so it runs
 * in the deep suite (`-Pgames.suite=deep`, gradle/tests.gradle.kts). Its body stays in
 * `RevealsFollowTheCardTest`, unchanged, under the same name with `Run` added.
 */
class RevealsFollowTheCardDeepTest {

    @Test
    fun theStandingRevealsAreExactlyTheRevealedCardsStillInAHand() =
        RevealsFollowTheCardTest().theStandingRevealsAreExactlyTheRevealedCardsStillInAHandRun()
}

package game.vinto.client

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * `FinishesTest` over a hundred and twenty seeds: every game finishes.
 *
 * Seven minutes of whole games on the JVM, so it runs in the deep suite (`-Pgames.suite=deep`,
 * gradle/tests.gradle.kts), before a release and in a step that changes the rules or the bot.
 * The everyday suite plays the same check on two seeds.
 */
class FinishesDeepTest {

    @Test
    fun aWholeGamePlaysItselfOutFromAnySeed() = runTest(timeout = FinishesTest.LONG) {
        FinishesTest().aWholeGamePlaysItselfOutOver(1L..SEEDS)
    }

    internal companion object {
        const val SEEDS = 120L
    }
}

package game.vinto.bot

import kotlin.test.Test

/**
 * `ReportedGamesTest`'s reported Jack, searched at `hard` on all five seeds.
 *
 * Five searches are 15 s, so they run in the deep suite (`-Pgames.suite=deep`,
 * gradle/tests.gradle.kts). The everyday suite searches the first seed with every assertion.
 */
class ReportedGamesDeepTest {

    @Test
    fun aJackLeftOnThePileIsTakenForAKnownJoker() {
        ReportedGamesTest().aJackLeftOnThePileIsTakenForAKnownJokerOver(ReportedGamesTest.JACK_SEEDS)
    }
}

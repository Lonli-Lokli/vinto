package game.vinto.bot

import kotlin.test.Test

/**
 * `ReportedGamesTest`'s slowest test, 15 s on the JVM, so it runs in the deep suite (`-Pgames.suite=deep`,
 * gradle/tests.gradle.kts). Its body stays in `ReportedGamesTest`, unchanged, under the same name with `Run`
 * added.
 */
class ReportedGamesDeepTest {

    @Test
    fun aJackLeftOnThePileIsTakenForAKnownJoker() = ReportedGamesTest().aJackLeftOnThePileIsTakenForAKnownJokerRun()
}

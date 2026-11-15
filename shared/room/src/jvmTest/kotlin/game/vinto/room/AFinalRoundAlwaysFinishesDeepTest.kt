package game.vinto.room

import kotlin.test.Test

/**
 * `AFinalRoundAlwaysFinishesTest`'s two tests, so they run in the deep suite (`-Pgames.suite=deep`,
 * gradle/tests.gradle.kts): the person-called final round, 11 s on the JVM, and the person who stops answering,
 * 10.1 to 12.7 s on the JVM on CI. Each body stays in `AFinalRoundAlwaysFinishesTest`, unchanged, under the same
 * name with `Run` added.
 */
class AFinalRoundAlwaysFinishesDeepTest {

    @Test
    fun aRoundAPersonCallsVintoInReachesScoring() =
        AFinalRoundAlwaysFinishesTest().aRoundAPersonCallsVintoInReachesScoringRun()

    @Test
    fun aPersonWhoStopsAnsweringDoesNotStopTheTable() =
        AFinalRoundAlwaysFinishesTest().aPersonWhoStopsAnsweringDoesNotStopTheTableRun()
}

package game.vinto.room

import kotlin.test.Test

/**
 * `AFinalRoundAlwaysFinishesTest`'s slowest test, 11 s on the JVM, so it runs in the deep suite
 * (`-Pgames.suite=deep`, gradle/tests.gradle.kts). Its body stays in `AFinalRoundAlwaysFinishesTest`,
 * unchanged, under the same name with `Run` added.
 */
class AFinalRoundAlwaysFinishesDeepTest {

    @Test
    fun aRoundAPersonCallsVintoInReachesScoring() =
        AFinalRoundAlwaysFinishesTest().aRoundAPersonCallsVintoInReachesScoringRun()
}

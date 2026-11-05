package game.vinto.room

import kotlin.test.Test

/**
 * `AFinalRoundAlwaysFinishesTest`'s person-called final round over all six seeds.
 *
 * Six rounds are 11 s, so they run in the deep suite (`-Pgames.suite=deep`,
 * gradle/tests.gradle.kts). The everyday suite plays the first two.
 */
class AFinalRoundAlwaysFinishesDeepTest {

    @Test
    fun aRoundAPersonCallsVintoInReachesScoring() {
        AFinalRoundAlwaysFinishesTest().aRoundAPersonCallsVintoInReachesScoringOver(AFinalRoundAlwaysFinishesTest.SEEDS)
    }
}

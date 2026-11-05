package game.vinto.client

import game.vinto.client.CoalitionFinalRoundTest.Companion.SCORED_SWEEP
import game.vinto.client.CoalitionFinalRoundTest.Companion.SWEEP
import game.vinto.client.CoalitionFinalRoundTest.Companion.TARGETED_SWEEP
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * `CoalitionFinalRoundTest` over every seed of its sweeps, against its measured floors.
 *
 * Two and a half minutes of final rounds on the JVM, so it runs in the deep suite
 * (`-Pgames.suite=deep`, gradle/tests.gradle.kts). The everyday suite keeps every assertion on
 * the first two rounds each sweep reaches.
 */
class CoalitionFinalRoundDeepTest {

    private val rounds = CoalitionFinalRoundTest()

    @Test
    fun theCoalitionPlaysItsLastTurnsAndTheRoundEnds() = runTest(timeout = WHOLE_GAME) {
        rounds.theCoalitionPlaysItsLastTurnsAndTheRoundEndsOver(1L..SWEEP, enough = rounds.enoughSeeds, stopAt = ALL)
    }

    @Test
    fun theCallerIsNeverTargetedByTheCoalitionInTheFinalRound() = runTest(timeout = WHOLE_GAME) {
        rounds.theCallerIsNeverTargetedByTheCoalitionInTheFinalRoundOver(1L..TARGETED_SWEEP, stopAt = ALL)
    }

    @Test
    fun theRoundIsScoredOnTheCoalitionsBestHandNotItsLast() = runTest(timeout = WHOLE_GAME) {
        rounds.theRoundIsScoredOnTheCoalitionsBestHandNotItsLastOver(1L..SCORED_SWEEP, stopAt = ALL)
    }

    @Test
    fun theCallerIsGivenThePlanToRead() = runTest(timeout = WHOLE_GAME) {
        rounds.theCallerIsGivenThePlanToReadOver(
            1L..SWEEP,
            enoughCalls = rounds.enoughSeeds,
            enoughRead = rounds.enoughPlans,
            stopAt = ALL,
        )
    }

    private companion object {
        const val ALL = Int.MAX_VALUE
    }
}

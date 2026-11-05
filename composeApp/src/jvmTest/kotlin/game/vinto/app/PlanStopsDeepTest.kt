package game.vinto.app

import kotlin.test.Test

/**
 * `PlanStopsTest`'s slowest test, 14 s on the JVM under load, so it runs in the deep suite
 * (`-Pgames.suite=deep`, gradle/tests.gradle.kts). Its body stays in `PlanStopsTest`, unchanged, under the
 * same name with `Run` added.
 */
class PlanStopsDeepTest {

    @Test
    fun theStopBeingReadIsAlwaysInView() = PlanStopsTest().theStopBeingReadIsAlwaysInViewRun()
}

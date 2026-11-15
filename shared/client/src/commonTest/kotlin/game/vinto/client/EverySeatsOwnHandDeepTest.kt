package game.vinto.client

import kotlin.test.Test

/**
 * `EverySeatsOwnHandTest`'s slowest test, 10.5 to 11.1 s on the JVM on CI, so it runs in the deep suite
 * (`-Pgames.suite=deep`, gradle/tests.gradle.kts). Its body stays in `EverySeatsOwnHandTest`, unchanged, under
 * the same name with `Run` added.
 */
class EverySeatsOwnHandDeepTest {

    @Test
    fun aCoalitionMemberIsShownItsOwnTotalAndNotTheCoalitionsBest() =
        EverySeatsOwnHandTest().aCoalitionMemberIsShownItsOwnTotalAndNotTheCoalitionsBestRun()
}

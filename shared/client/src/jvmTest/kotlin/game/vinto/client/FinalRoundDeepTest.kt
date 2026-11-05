package game.vinto.client

import kotlin.test.Test

/**
 * `FinalRoundTest`'s slowest test, 80 s on the JVM, so it runs in the deep suite (`-Pgames.suite=deep`,
 * gradle/tests.gradle.kts). Its body stays in `FinalRoundTest`, unchanged, under the same name with `Run`
 * added.
 */
class FinalRoundDeepTest {

    @Test
    fun callingVintoInTheTossInWindowPlaysTheRoundOut() =
        FinalRoundTest().callingVintoInTheTossInWindowPlaysTheRoundOutRun()
}

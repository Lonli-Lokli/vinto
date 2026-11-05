package game.vinto.bot

import kotlin.test.Test

/**
 * `MctsDiscriminationTest`'s slowest test, 12 s on the JVM, so it runs in the deep suite
 * (`-Pgames.suite=deep`, gradle/tests.gradle.kts). Its body stays in `MctsDiscriminationTest`, unchanged,
 * under the same name with `Run` added.
 */
class MctsDiscriminationDeepTest {

    @Test
    fun aJackGivesATenForAKnownJokerRatherThanForAKnownNine() =
        MctsDiscriminationTest().aJackGivesATenForAKnownJokerRatherThanForAKnownNineRun()
}

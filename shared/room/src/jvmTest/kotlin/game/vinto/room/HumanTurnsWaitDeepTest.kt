package game.vinto.room

import kotlin.test.Test

/**
 * `HumanTurnsWaitTest`'s slowest test, 47 s on the JVM, so it runs in the deep suite (`-Pgames.suite=deep`,
 * gradle/tests.gradle.kts). Its body stays in `HumanTurnsWaitTest`, unchanged, under the same name with `Run`
 * added.
 */
class HumanTurnsWaitDeepTest {

    @Test
    fun theRoomOnlyEverFinishesAPersonsTossIn() = HumanTurnsWaitTest().theRoomOnlyEverFinishesAPersonsTossInRun()
}

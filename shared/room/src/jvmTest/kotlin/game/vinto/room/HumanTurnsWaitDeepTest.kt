package game.vinto.room

import kotlin.test.Test

/**
 * `HumanTurnsWaitTest`'s invariant over two rounds of all three seeds.
 *
 * The rounds are 47 s of the bots' thinking, so they run in the deep suite
 * (`-Pgames.suite=deep`, gradle/tests.gradle.kts). The everyday suite plays the first seed.
 */
class HumanTurnsWaitDeepTest {

    @Test
    fun theRoomOnlyEverFinishesAPersonsTossIn() {
        HumanTurnsWaitTest().theRoomOnlyEverFinishesAPersonsTossInOver(HumanTurnsWaitTest.SEEDS)
    }
}

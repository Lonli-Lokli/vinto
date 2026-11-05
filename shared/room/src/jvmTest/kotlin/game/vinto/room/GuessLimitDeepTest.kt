package game.vinto.room

import kotlin.test.Test

/** `GuessLimitTest`'s bound on the counter table, which takes 2,500 guesses to prove. */
class GuessLimitDeepTest {

    @Test
    fun theCounterTableCannotBeGrownWithoutLimit() = GuessLimitTest().theCounterTableCannotBeGrownWithoutLimitRun()
}

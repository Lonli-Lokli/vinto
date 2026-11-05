package game.vinto.app

import kotlin.test.Test

/**
 * The deep half of [TheDuoHalfOpenTest]: the glides from flat into a laptop (14 s under load) and into a book
 * (11 s under load). Both bodies stay in `TheDuoHalfOpenTest`, unchanged, under the same names with `Run` added.
 * Both still postures stay everyday.
 */
class TheDuoHalfOpenDeepTest {

    /**
     * Folded from flat into a laptop, your own hand moves down to the lower half, and starts from where
     * it lay on the flat table.
     */
    @Test
    fun theTableGlidesFromFlatIntoALaptop() = TheDuoHalfOpenTest().theTableGlidesFromFlatIntoALaptopRun()

    @Test
    fun theTableGlidesFromFlatIntoABook() = TheDuoHalfOpenTest().theTableGlidesFromFlatIntoABookRun()
}

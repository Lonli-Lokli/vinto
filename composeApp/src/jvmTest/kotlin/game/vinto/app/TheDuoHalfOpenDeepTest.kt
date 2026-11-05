package game.vinto.app

import kotlin.test.Test

/**
 * The deep half of [TheDuoHalfOpenTest]: the glide from flat into a laptop, 14 s under load. The book's
 * glide and both still postures stay everyday.
 */
class TheDuoHalfOpenDeepTest {

    /**
     * Folded from flat into a laptop, your own hand moves down to the lower half, and starts from where
     * it lay on the flat table.
     */
    @Test
    fun theTableGlidesFromFlatIntoALaptop() = TheDuoHalfOpenTest().theTableGlidesFromFlatIntoALaptopRun()
}

package game.vinto.app

import kotlin.test.Test

/**
 * The deep half of [TheTableGlidesTest]: the glide that waits for the names to settle, over 10 s
 * under load. The other glides stay everyday.
 */
class TheTableGlidesDeepTest {

    /** "Our names slowly moving into new position": the cards are home while the plates still travel. */
    @Test
    fun theNamesArriveAfterTheirCards() = TheTableGlidesTest().theNamesArriveAfterTheirCardsRun()
}

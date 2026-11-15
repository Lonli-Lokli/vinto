package game.vinto.app

import kotlin.test.Test

/**
 * The deep half of [TheTableGlidesTest]: every test that plays a glide out to where it settles, each 10 to 24 s on
 * CI, since each one renders four seconds of a 1200 by 900 table a frame at a time. Their bodies stay in
 * `TheTableGlidesTest`, unchanged, under the same names with `Run` added. The first frame of opening, where the
 * table starts on the felt, stays everyday.
 */
class TheTableGlidesDeepTest {

    @Test
    fun openingTheFoldGlidesTheTableToItsNewShape() =
        TheTableGlidesTest().openingTheFoldGlidesTheTableToItsNewShapeRun()

    /** "Our names slowly moving into new position": the cards are home while the plates still travel. */
    @Test
    fun theNamesArriveAfterTheirCards() = TheTableGlidesTest().theNamesArriveAfterTheirCardsRun()

    @Test
    fun underReducedMotionTheTableIsSimplyThere() =
        TheTableGlidesTest().underReducedMotionTheTableIsSimplyThereRun()

    @Test
    fun theHingeCarriesTheTableOpen() = TheTableGlidesTest().theHingeCarriesTheTableOpenRun()

    @Test
    fun aWindowResizedByAFewPointsIsTheSameTable() =
        TheTableGlidesTest().aWindowResizedByAFewPointsIsTheSameTableRun()
}

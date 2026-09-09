package game.vinto.app

import game.vinto.app.game.headerStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The header is one of three known shapes, and which one is a FACT rather than a measurement.
 *
 * It used to be arithmetic: every word the header might say, measured against the room it had,
 * with the wordmark and the round counter dropped when the sum said they would not fit. The sum
 * was wrong twice — it reserved a tap target for a deck chip that had moved to the felt, and it
 * asked whether the labels fit without the width floor the drawing applied — and each time the
 * symptom was the same, a phone showing a blank left half with the controls floating on the right.
 * Tuning it twice did not stop it, because a sum that has to be right about text it has not drawn
 * yet, in a font the web loads after the first frames, has more ways to be wrong than anyone can
 * enumerate.
 *
 * So these are the rules, asserted where they are decided rather than through pixels. A test that
 * renders a header can only ever say what happened at the widths it thought to try.
 */
class HeaderStyleTest {

    @Test
    fun aPhoneInPortraitDrawsMarksAndKeepsItsWordmark() {
        val style = headerStyle(Host.PHONE, landscape = false)
        assertFalse(style.labelled, "a portrait phone has no width for words on its controls")
        assertFalse(style.cup, "a phone's answer to the same want is an in-app purchase, not a link out")
        assertFalse(style.counter, "the wordmark wins the space a portrait phone has")
    }

    /** Rotate the same phone and it has the width, so it says what the controls do. */
    @Test
    fun aPhoneInLandscapeSaysWhatItsControlsDo() {
        val style = headerStyle(Host.PHONE, landscape = true)
        assertTrue(style.labelled)
        assertTrue(style.counter)
        assertFalse(style.cup, "still a phone, still a store's rules")
    }

    /** A desktop window is roomy whichever way round it is. */
    @Test
    fun aDesktopIsAlwaysLabelled() {
        assertTrue(headerStyle(Host.DESKTOP, landscape = false).labelled)
        assertTrue(headerStyle(Host.DESKTOP, landscape = true).labelled)
    }

    /**
     * And the cup is the web's alone.
     *
     * The one place a page can take a payment. On a phone the same offer is an in-app purchase in
     * the settings — App Store 3.1.1 bars a link out of a game screen to buy something — and on
     * the desktop there is no store to sell it and no reason to put a payment in a table's header.
     */
    @Test
    fun onlyTheWebOffersTheCup() {
        assertEquals(
            listOf(false, false, true),
            listOf(Host.PHONE, Host.DESKTOP, Host.WEB).map { headerStyle(it, landscape = false).cup },
        )
    }
}

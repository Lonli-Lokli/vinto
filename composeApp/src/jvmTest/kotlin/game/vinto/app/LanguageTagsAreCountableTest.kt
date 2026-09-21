package game.vinto.app

import game.vinto.protocol.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The app's languages and the store's vocabulary are the same twenty.
 *
 * The other half of `EveryLanguageIsCountableTest`. A language added to [Language] and not to
 * `Locale` is a language whose players are silently not counted — the room drops a tag it does
 * not know, which is the correct refusal and an invisible one.
 */
class LanguageTagsAreCountableTest {

    @Test
    fun everyLanguageTheAppOffersCanBeCounted() {
        assertEquals(
            Language.entries.map { it.tag }.toSet(),
            Locale.entries.map { it.tag }.toSet(),
            "a language the app ships cannot be counted, or a countable one is not shipped",
        )
    }

    /** And the normaliser turns what a platform answers into one of them. */
    @Test
    fun thePlatformsThreeShapesAllReduceToATag() {
        assertEquals("en", countable("en_GB"), "Android and the desktop")
        assertEquals("en", countable("en-GB"), "iOS")
        assertEquals("be", countable("be"), "a browser")
        assertEquals(null, countable("kl"), "a language the app does not ship is not invented")
    }
}

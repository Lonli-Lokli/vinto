package game.vinto.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every language the app ships is a language the store will accept.
 *
 * [Locale] is a closed enum because what reaches the dataset must be unrepresentable as free
 * text, and the room drops a tag it does not recognise. That is the right refusal and a silent
 * one: add a twenty-first language to the app and its players are simply not counted, with no
 * error anywhere and a panel that quietly under-reports.
 *
 * So the list is checked against the app's own. `Language.kt` cannot be read from here —
 * `shared/protocol` knows nothing about `composeApp` and must not — so this holds the tags and
 * `LanguageTagsAreCountableTest` in the app holds the same set against the enum. Two tests, one
 * fact, and the fact is written down in both places rather than in neither.
 */
class EveryLanguageIsCountableTest {

    @Test
    fun theListIsTheTwentyTheAppShips() {
        assertEquals(
            setOf(
                "ar", "be", "bn", "de", "en", "es", "fr", "he", "hi", "id",
                "it", "ja", "ko", "pl", "pt", "ru", "tr", "uk", "ur", "zh",
            ),
            Locale.entries.map { it.tag }.toSet(),
            "the countable languages are not the ones the app ships",
        )
    }

    /** And they are base tags, because that is what a client's locale is cut down to. */
    @Test
    fun theyAreBaseTagsWithNoRegion() {
        val odd = Locale.entries.map { it.tag }.filterNot { it.length == 2 && it.all(Char::isLetter) }
        assertEquals(emptyList(), odd, "a region or a script would never match a cut-down tag")
    }
}

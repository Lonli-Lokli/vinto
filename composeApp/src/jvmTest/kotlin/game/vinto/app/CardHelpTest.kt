package game.vinto.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.client.Explains
import game.vinto.shapes.Rank
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The "?" says what the card is, what it costs, and what it does.
 *
 * The other half of `TableModelTest.everyStateExplainsItself`. That test used to assert on the
 * assembled paragraph — "starts with Queen", "contains swap", "contains 10" — which was the
 * right claim in the wrong module: after WORDS.md §6h the model says only *which* explanation is
 * wanted, and the words are here.
 *
 * Splitting it kept the claim rather than dropping it, which is the part worth being careful
 * about when a refactor moves a responsibility: it is very easy to convert the assertion into
 * something weaker and call the tests green.
 */
@OptIn(ExperimentalTestApi::class)
class CardHelpTest {

    @Test
    fun theCardInPlayIsNamedPricedAndExplained() = runComposeUiTest {
        val read = mutableStateOf("")
        setContent { read.value = explained(Explains.TheCardInPlay(Rank.QUEEN)) }
        waitForIdle()

        val help by read
        assertTrue(help.startsWith("Queen"), "the card is not named first: $help")
        assertTrue("swap" in help, "it does not say what a Queen does: $help")
        assertTrue("10" in help, "it does not say what holding one costs: $help")
    }

    @Test
    fun aPlainCardSaysItHasNoAction() = runComposeUiTest {
        // The other branch, which the old assertion never reached.
        val read = mutableStateOf("")
        setContent { read.value = explained(Explains.TheCardInPlay(Rank.FIVE)) }
        waitForIdle()

        val help by read
        assertTrue(help.startsWith("Five"), help)
        assertTrue("no action" in help, "a 5 should say it cannot do anything: $help")
        assertTrue("5" in help, "and what it is worth: $help")
    }
}

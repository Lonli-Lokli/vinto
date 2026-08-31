package game.vinto.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.app.theme.CodeField
import game.vinto.app.theme.VintoTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/*
 * A note on the fixture below, because it is a trap worth naming: the value has to be real
 * Compose state. A plain captured `var` accepts the first `performTextInput` and then never
 * recomposes, so the field still holds "" and the *second* input replaces rather than appends
 * — which reads exactly like the control dropping characters. The bug was in the test.
 */

/**
 * The one string in this app somebody reads aloud down a telephone.
 *
 * A room code arrives two ways and a single text box serves neither: read out, character by
 * character, where the reader needs to see how many are left; or pasted whole out of an
 * invitation, where what lands on the clipboard is a URL and not six characters.
 *
 * These are the rules the six cells are for. They are asserted against the control rather than
 * against the screen using it, so the lobby can be rearranged without taking the code entry's
 * behaviour with it.
 */
@OptIn(ExperimentalTestApi::class)
class CodeFieldTest {

    @Test
    fun sixCharactersIsTheWholeCodeAndTheSeventhIsIgnored() = runComposeUiTest {
        val code = mutableStateOf("")
        setContent { VintoTheme { Field(code.value) { code.value = it } } }

        field().performTextInput("ABCDEF")
        waitForIdle()
        assertEquals("ABCDEF", code.value)

        field().performTextInput("G")
        waitForIdle()
        assertEquals("ABCDEF", code.value, "a seventh character was accepted")
    }

    /**
     * Typed lower case, stored upper.
     *
     * The registry issues upper case and `looksLikeRoomCode` expects it, so a code typed in
     * lower case on a phone that decided to be helpful must still reach the room.
     */
    @Test
    fun aCodeTypedInLowerCaseIsStillTheSameCode() = runComposeUiTest {
        val code = mutableStateOf("")
        setContent { VintoTheme { Field(code.value) { code.value = it } } }

        field().performTextInput("abc23d")
        waitForIdle()
        assertEquals("ABC23D", code.value)
    }

    /**
     * The characters that are not in the alphabet are the ones that look like the ones that are.
     *
     * `CODE_ALPHABET` leaves out `O`, `I`, `L`, `0` and `1` precisely because a code gets read
     * aloud, and an O for a 0 is the mistake that costs somebody their invitation. Refusing them
     * at the keyboard is better than accepting them and answering "no such room" — the person
     * has the right code and is being told they do not.
     */
    @Test
    fun theLookalikeCharactersCannotBeTyped() = runComposeUiTest {
        val code = mutableStateOf("")
        setContent { VintoTheme { Field(code.value) { code.value = it } } }

        field().performTextInput("O0I1LA")
        waitForIdle()
        assertEquals("A", code.value, "a character the registry could never issue was accepted")
    }

    /**
     * Pasting the whole invitation works.
     *
     * Somebody sent a link taps it or copies it; what reaches the clipboard is a URL. Asking
     * them to find the six characters inside it and retype them is asking them to do the
     * app's job. The filter drops everything that is not a code character, which for
     * `https://vinto.kupalinka.app/r/ABC23D` leaves the code and the letters of the host —
     * so the *caller* runs `roomCodeFrom` first; this test pins what the field does with the
     * six characters that survive, which is the half the control is responsible for.
     */
    @Test
    fun aPastedCodeFillsTheCells() = runComposeUiTest {
        val code = mutableStateOf("")
        setContent { VintoTheme { Field(code.value) { code.value = it } } }

        field().performTextReplacement("ABC23D")
        waitForIdle()
        assertEquals("ABC23D", code.value)
    }

    /** Deleting works backwards through the cells, which is what a backspace has to do. */
    @Test
    fun aCodeCanBeCorrected() = runComposeUiTest {
        val code = mutableStateOf("")
        setContent { VintoTheme { Field(code.value) { code.value = it } } }

        field().performTextInput("ABC23D")
        waitForIdle()
        field().performTextReplacement("ABC23")
        waitForIdle()
        assertEquals("ABC23", code.value)
    }

    @androidx.compose.runtime.Composable
    private fun Field(value: String, onChange: (String) -> Unit) {
        CodeField(value = value, onValueChange = onChange, label = LABEL)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.field() =
        onNodeWithContentDescription(LABEL)

    private companion object {
        const val LABEL = "Room code"
    }
}

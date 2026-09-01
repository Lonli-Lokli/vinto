package game.vinto.app

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.app.theme.VintoTheme
import game.vinto.client.MemoryVault
import kotlin.test.Test

/**
 * A new room is listed, unless the host says otherwise.
 *
 * **This test asserted the opposite yesterday, and the reversal is the point of keeping it.**
 * The default was private, on the reasoning that a listing cannot be taken back once a
 * stranger has read it, so the safe answer is the one already chosen. That reasoning was
 * sound as far as it went and left out the thing that decides it: a room has to be *found*.
 * With nobody listed by default the public browser is an empty screen, and an online mode
 * that depends on two strangers meeting gives them nowhere to meet. Reversed on the product
 * owner's decision.
 *
 * What the test is *for* has not changed, which is why it is a rewrite rather than a deletion:
 * this is the one control on the way into a room whose value nobody re-reads before tapping
 * Open, and it is carried entirely by which end of a groove a thumb is sitting on. Shading is
 * exactly the thing that inverts in a refactor without a line of the logic changing — so
 * whichever way the default points, something has to say so out loud.
 */
@OptIn(ExperimentalTestApi::class)
class RoomVisibilityDefaultTest {

    @Test
    fun aNewRoomIsListedUntilSomebodySaysOtherwise() = runComposeUiTest {
        setContent { VintoTheme { App(seeds = { SEED }, vault = MemoryVault()) } }
        waitForIdle()

        press("Play online")
        waitForIdle()
        // A name is required before the three ways in are live, so type one. That gate is the
        // point of `OnlineNameRequiredTest`; here it is only the door to everything past it.
        onNodeWithContentDescription("Your name at the table").performTextInput("Ada")
        waitForIdle()

        // The choice lives on the path that creates a room now, and nowhere else. It used to
        // sit above a Join button it had nothing to do with, which is how the one control here
        // whose wrong answer cannot be undone got tapped on the way past.
        press("Open a room")
        waitForIdle()

        onNodeWithContentDescription("Listed publicly").assertIsSelected()
        onNodeWithContentDescription("By code only").assertIsNotSelected()
    }

    /** Scrolls to it first: this control is below the fold on a test-sized window. */
    private fun ComposeUiTest.press(label: String) {
        val button = onNodeWithContentDescription(label)
        if (!button.isDisplayed()) button.performScrollTo()
        button.performClick()
    }
    private companion object {
        const val SEED = 20_260_819L
    }
}

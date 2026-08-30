package game.vinto.app

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.app.theme.VintoTheme
import game.vinto.client.MemoryVault
import kotlin.test.Test

/**
 * A new room is private until somebody says otherwise.
 *
 * The one control on this screen whose wrong answer cannot be taken back. A listing is read by
 * strangers the moment it exists, so a room that is public *by default* publishes a game
 * somebody meant to play with two friends — and unlike every other mistake in this app, undoing
 * it does not undo the consequence.
 *
 * `OnlineScreen`'s state has always defaulted to private and its KDoc has always said so. What
 * did not exist was anything checking that the *screen* agrees with the state: the choice is
 * drawn as a raised thumb sliding along a groove, so which side is chosen is carried entirely
 * by shading, and shading is exactly the kind of thing that inverts in a refactor without one
 * line of the logic changing. Reported as a possible bug from a photograph of a phone, which is
 * the only way anybody would ever have found it.
 */
@OptIn(ExperimentalTestApi::class)
class RoomVisibilityDefaultTest {

    @Test
    fun aNewRoomIsPrivateUntilSomebodySaysOtherwise() = runComposeUiTest {
        setContent { VintoTheme { App(seeds = { SEED }, vault = MemoryVault()) } }
        waitForIdle()

        press("Play online")
        waitForIdle()
        // The choice lives on the path that creates a room now, and nowhere else. It used to
        // sit above a Join button it had nothing to do with, which is how the one control here
        // whose wrong answer cannot be undone got tapped on the way past.
        press("Open a room")
        waitForIdle()

        onNodeWithContentDescription("By code only").assertIsSelected()
        onNodeWithContentDescription("Listed publicly").assertIsNotSelected()
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

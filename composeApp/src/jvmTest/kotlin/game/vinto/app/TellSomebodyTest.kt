package game.vinto.app

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.app.theme.VintoTheme
import game.vinto.client.MemoryVault
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * "Tell somebody" is one button, and the code it sends travels *with the message*.
 *
 * The row used to draw the QR on the settings screen itself, under the sentence and above the
 * button. That is the code in the one place it cannot be used: nobody scans their own phone, and
 * the person who needs it is by definition not holding it. On the invite sheet a drawn code earns
 * its place — somebody is sitting opposite with a camera — but in About there is no second person
 * in the room, so the code has to leave the device to be worth anything.
 *
 * So the screen shows the button alone, and the picture goes into the share sheet beside the text.
 */
@OptIn(ExperimentalTestApi::class)
class TellSomebodyTest {

    @Test
    fun aboutOffersTheButtonAndNothingElse() = runComposeUiTest {
        settings()
        door("About")

        onNodeWithText("Tell somebody").performScrollTo().assertIsDisplayed()

        // The chip's only name is its label, so its absence is what says the code is not drawn
        // here. Matched on `settings_share_scan` — if that string is reused elsewhere on this
        // screen this test is the thing that should be edited, not the assertion loosened.
        assertEquals(
            0,
            onAllNodesWithContentDescription("Scan to open Vinto").fetchSemanticsNodes().size,
            "the settings screen is still drawing the QR at the person who cannot scan it",
        )
    }

    private fun ComposeUiTest.settings() {
        setContent { VintoTheme { App(seeds = { SEED }, vault = MemoryVault()) } }
        waitForIdle()
        press("Settings")
    }

    private fun ComposeUiTest.door(name: String) {
        onNodeWithText(name.uppercase()).performScrollTo().performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.press(label: String) {
        val node = onNodeWithContentDescription(label)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
        waitForIdle()
    }

    private companion object {
        const val SEED = 20_260_819L
    }
}

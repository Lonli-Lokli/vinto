package game.vinto.app

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
 * Each way in asks for what it needs, and nothing else.
 *
 * The front door used to be one form followed by three verbs that each wanted a different part
 * of it: join needs a name and a code, opening a room needs a name and a visibility, browsing
 * needs only a name. Everybody saw all of it. So a player who came to browse was asked for a
 * room code they do not have, and a player joining a friend was asked to decide the visibility
 * of a room they are not creating — which is also the control whose wrong answer publishes
 * somebody's private game and cannot be taken back.
 *
 * These cases pin the *separation* rather than the layout. A screen can be rearranged; what
 * must not come back is a control appearing on a path it has nothing to do with.
 */
@OptIn(ExperimentalTestApi::class)
class LobbyFlowTest {

    @Test
    fun theFrontDoorAsksWhatYouCameToDoBeforeItAsksForAnything() = runComposeUiTest {
        online()

        onNodeWithContentDescription("Open a room").assertIsDisplayed()
        onNodeWithContentDescription("Join with a code").assertIsDisplayed()
        onNodeWithContentDescription("Browse public rooms").assertIsDisplayed()

        // The two controls that belong to one path each are on neither.
        onNodeWithContentDescription("Room code").assertDoesNotExist()
        onNodeWithContentDescription("By code only").assertDoesNotExist()
    }

    /**
     * Joining asks for a code and does not ask who may find the room.
     *
     * You are not creating one. The visibility control appearing here is the exact confusion
     * the split exists to remove.
     */
    @Test
    fun joiningAsksForACodeAndNothingAboutVisibility() = runComposeUiTest {
        online()
        press("Join with a code")

        onNodeWithContentDescription("Room code").assertIsDisplayed()
        onNodeWithContentDescription("By code only").assertDoesNotExist()
        onNodeWithContentDescription("Listed publicly").assertDoesNotExist()
    }

    /** And opening a room asks about visibility and not for a code you could not have. */
    @Test
    fun openingARoomAsksAboutVisibilityAndNotForACode() = runComposeUiTest {
        online()
        press("Open a room")

        onNodeWithContentDescription("By code only").assertIsDisplayed()
        onNodeWithContentDescription("Room code").assertDoesNotExist()
    }

    /**
     * Join lights up on the sixth character, and is dead before it.
     *
     * The defect this replaces: the biggest, brightest control on the old screen was a green
     * Join whose `onClick` returned unless six characters had been typed. No disabled state, no
     * message, nothing — so a first-time player's most likely first tap was a silent no-op,
     * which is indistinguishable from the app being broken.
     */
    @Test
    fun joinIsDeadUntilThereIsAWholeCodeAndThenItIsNot() = runComposeUiTest {
        online()
        press("Join with a code")

        onNodeWithContentDescription("Join the room").assertIsNotEnabled()

        onNodeWithContentDescription("Room code").performTextInput("ABC23")
        waitForIdle()
        onNodeWithContentDescription("Join the room").assertIsNotEnabled()

        onNodeWithContentDescription("Room code").performTextInput("D")
        waitForIdle()
        onNodeWithContentDescription("Join the room").assertIsEnabled()
    }

    /**
     * Every destination leads back to the front door, not to wherever it was reached from.
     *
     * That is what lets the chevron in the corner of each mean one thing. It is also the half
     * of a restructure that is easiest to leave broken, because nothing about a wrong Back
     * looks wrong in a diff.
     */
    @Test
    fun everyWayInHasTheSameWayBack() = runComposeUiTest {
        online()

        listOf("Open a room", "Join with a code").forEach { tile ->
            press(tile)
            press("Back")
            waitForIdle()
            onNodeWithContentDescription(tile).assertIsDisplayed()
        }
    }

    /**
     * And the phone's own back button means the same thing as the one on the screen.
     *
     * It did not. `Discover` sent the system back all the way Home while its drawn chevron went
     * to the front door, so which of two gestures you used decided where you ended up — and
     * three ways in makes that three times as easy to hit. Asserted through `Screen.backedOutOf`
     * rather than by pressing a hardware key, because `SystemBack` is an `expect` that no-ops on
     * the JVM: what can be checked here is the routing, which is the half that was wrong.
     */
    @Test
    fun theWayInIsLeftTheSameWayWhicheverBackIsUsed() = runComposeUiTest {
        online()
        press("Join with a code")

        // The drawn chevron.
        press("Back")
        onNodeWithContentDescription("Join with a code").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ helpers

    private fun ComposeUiTest.online() {
        setContent { VintoTheme { App(seeds = { SEED }, vault = MemoryVault()) } }
        waitForIdle()
        press("Play online")
        // No name to supply any more: the screen mints one, so the three ways in are live the
        // moment it opens. `MintedNameTest` is what holds that.
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

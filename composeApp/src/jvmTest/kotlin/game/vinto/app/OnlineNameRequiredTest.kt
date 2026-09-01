package game.vinto.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
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
 * Online play will not start without a name, and pressing anyway is how you find that out.
 *
 * The field began empty and the three ways in were live, so the easiest thing to do on that
 * screen was walk past it. The room then fills a blank name in as "Player 1", and the player
 * is introduced to three strangers by a placeholder they never chose and cannot tell is
 * theirs — which is what a screenshot from a real game showed.
 *
 * The first fix disabled the tiles, and that was reported straight back: "buttons look
 * enabled and nothing is shown if I press". A control that swallows a press and says nothing
 * is the worst of the three available answers, because the player's next move is to press it
 * again. So the tiles still take the press, and the press is what marks the field.
 *
 * The mark is a semantics error and not only a red rim, because a warning a screen reader
 * cannot hear is a warning the one player who most needs it never gets.
 */
@OptIn(ExperimentalTestApi::class)
class OnlineNameRequiredTest {

    @Test
    fun pressingAWayInWithNoNameSaysSoInsteadOfGoing() = runComposeUiTest {
        online()
        onNodeWithContentDescription(NAME).assert(noComplaint())

        press("Open a room")

        // Still here, and the field is now the thing being complained about.
        onNodeWithContentDescription("Open a room").assertIsDisplayed()
        onNodeWithContentDescription(NAME).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
    }

    /** Spaces are not a name, however many of them there are. */
    @Test
    fun aNameOfNothingButSpacesIsNoName() = runComposeUiTest {
        online()

        onNodeWithContentDescription(NAME).performTextInput("   ")
        press("Open a room")

        onNodeWithContentDescription("Open a room").assertIsDisplayed()
        onNodeWithContentDescription(NAME).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
    }

    /** And typing takes the complaint back, rather than leaving it up until the next press. */
    @Test
    fun theComplaintGoesTheMomentThereIsSomethingToCallYou() = runComposeUiTest {
        online()
        press("Open a room")

        onNodeWithContentDescription(NAME).performTextInput("Ada")
        waitForIdle()

        onNodeWithContentDescription(NAME).assert(noComplaint())
    }

    private fun noComplaint() = SemanticsMatcher.keyNotDefined(SemanticsProperties.Error)

    private fun ComposeUiTest.press(label: String) {
        val node = onNodeWithContentDescription(label)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.online() {
        setContent { VintoTheme { App(seeds = { SEED }, vault = MemoryVault()) } }
        waitForIdle()
        press("Play online")
    }

    private companion object {
        const val SEED = 20_260_901L
        const val NAME = "Your name at the table"
    }
}

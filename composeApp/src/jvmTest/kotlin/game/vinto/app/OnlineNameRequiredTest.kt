package game.vinto.app

import androidx.compose.ui.test.ExperimentalTestApi
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
 * Online play will not start without a name, and what travels is the trimmed one.
 *
 * The field began empty and the three ways in were live anyway, so the easiest thing to do on
 * that screen was walk past it. The room then fills a blank name in as "Player 1", and the
 * player is introduced to three strangers by a placeholder they never chose and cannot tell is
 * theirs — which is exactly what a screenshot from a real game showed.
 *
 * Whitespace is the other half. A phone keyboard offers a space after every word, so " Ada "
 * is what a careful person types; untrimmed it reaches the room and is shown to the table with
 * the spaces in, and a name of nothing but spaces passes an `isEmpty` check.
 */
@OptIn(ExperimentalTestApi::class)
class OnlineNameRequiredTest {

    @Test
    fun thereIsNoWayInUntilYouHaveSaidWhatToCallYou() = runComposeUiTest {
        online()

        listOf("Open a room", "Join with a code", "Browse public rooms").forEach { way ->
            onNodeWithContentDescription(way).assertIsNotEnabled()
        }

        onNodeWithContentDescription(NAME).performTextInput("Ada")
        waitForIdle()

        listOf("Open a room", "Join with a code", "Browse public rooms").forEach { way ->
            onNodeWithContentDescription(way).assertIsEnabled()
        }
    }

    /** Spaces are not a name, however many of them there are. */
    @Test
    fun aNameOfNothingButSpacesIsNoName() = runComposeUiTest {
        online()

        onNodeWithContentDescription(NAME).performTextInput("   ")
        waitForIdle()

        onNodeWithContentDescription("Open a room").assertIsNotEnabled()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.online() {
        setContent { VintoTheme { App(seeds = { SEED }, vault = MemoryVault()) } }
        waitForIdle()
        val node = onNodeWithContentDescription("Play online")
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
        waitForIdle()
    }

    private companion object {
        const val SEED = 20_260_901L
        const val NAME = "Your name at the table"
    }
}

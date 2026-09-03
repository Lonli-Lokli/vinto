package game.vinto.app

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.app.theme.VintoTheme
import game.vinto.client.MemoryVault
import game.vinto.protocol.looksMinted
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The player always has a name, and never types one.
 *
 * This replaces `OnlineNameRequiredTest`, which guarded a state that can no longer happen. That
 * test's history is worth keeping, because it is the reason this screen is careful: the field
 * began empty and the three ways in were live, so the easiest thing to do was walk past it —
 * the room then filled the blank in as "Player 1", and a real screenshot showed a player
 * introduced to three strangers by a placeholder they never chose. Disabling the tiles was
 * tried and reported straight back ("buttons look enabled and nothing is shown if I press").
 *
 * The field is gone now, for a different reason: a typed name is user-generated content, and an
 * app that has it owes App Store Review Guideline 1.2 a filter, a report route and a block
 * (`shared/protocol`'s `Nickname.kt`). Minting the name removes the category — and it happens to
 * remove the empty-name bug too, structurally rather than by warning about it.
 *
 * So what is asserted here is the *new* guarantee: a name is present before anything is pressed,
 * it is one the room will accept, and the player can still change it.
 */
@OptIn(ExperimentalTestApi::class)
class MintedNameTest {

    @Test
    fun aNameIsAlreadyThereAndTheWaysInAreLiveAtOnce() = runComposeUiTest {
        online()

        assertTrue(looksMinted(shownName()), "the screen offered a name the room would refuse")

        // No press needed to earn them: the empty state that used to gate these is gone.
        listOf("Open a room", "Join with a code").forEach {
            onNodeWithContentDescription(it).performScrollTo().assertIsDisplayed()
        }
    }

    /** A fresh vault gets a name, rather than a blank waiting to be filled in. */
    @Test
    fun thereIsNoWayToReachThisScreenWithoutNaming(): Unit = runComposeUiTest {
        online()
        assertTrue(shownName().isNotBlank(), "the seat would be introduced by nothing at all")
    }

    /**
     * Pressing the row takes another name, which is the whole of the choice on offer.
     *
     * Two presses rather than one, because a generator is allowed to hand back the same name
     * twice and a test that demanded otherwise would be flaky roughly one time in a thousand.
     */
    @Test
    fun pressingTheNameTakesADifferentOne() = runComposeUiTest {
        online()
        val first = shownName()

        nameRow().performClick()
        waitForIdle()
        val second = shownName()
        if (second == first) {
            nameRow().performClick()
            waitForIdle()
        }

        val third = shownName()
        assertTrue(looksMinted(third), "pressing produced a name the room would refuse: $third")
        assertNotEquals(first, third, "the name never changed across two presses")
    }

    /** The name is the row's title, so reading it is reading the row. */
    private fun ComposeUiTest.shownName(): String {
        val detail = "What the other seats see. Tap for a different one. Not an account — there are none."
        // The tile is described to a screen reader as "<title>. <detail>", so the name is what
        // is left when the detail is taken off the end.
        return tileDescription(detail).removeSuffix(detail).trim().removeSuffix(".").trim()
    }

    private fun ComposeUiTest.tileDescription(detail: String): String {
        val node = onNodeWithText(detail, substring = true).performScrollTo()
        node.assertIsDisplayed()
        return node.fetchSemanticsNode().config
            .first { it.key.name == "ContentDescription" }
            .value
            .let { (it as List<*>).joinToString(" ") }
    }

    private fun ComposeUiTest.nameRow(): SemanticsNodeInteraction =
        onNodeWithText("What the other seats see", substring = true).performScrollTo()

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
    }
}

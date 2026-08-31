package game.vinto.app

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.app.theme.VintoTheme
import game.vinto.client.MemoryVault
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The language control is a drop-down, and the property that makes it one is testable.
 *
 * It was twenty-one buttons in a grid — "Follow the device" full width, then ten rows of two —
 * and nothing tested it, which is why nothing said that it was taller than a phone and that it
 * pushed Motion, Theme, Sound and Haptics below the fold behind nineteen languages nobody was
 * hunting for.
 *
 * So the first case is the one that matters and it is a *negative*: closed, the answers are not
 * on the screen at all. A picker that merely looked tidier while still composing twenty-one
 * rows would pass everything else here.
 */
@OptIn(ExperimentalTestApi::class)
class LanguagePickerTest {

    @Test
    fun theLanguagesAreNotOnTheScreenUntilYouAskForThem() = runComposeUiTest {
        settings()

        onNodeWithContentDescription(CLOSED_DEVICE).performScrollTo().assertIsDisplayed()

        // One from each script the list carries, so this cannot pass by the Latin ones
        // happening to be absent.
        listOf("Русский", "Українська", "日本語", "العربية", "Deutsch").forEach {
            assertEquals(
                0,
                onAllNodesWithText(it).fetchSemanticsNodes().size,
                "\"$it\" is on the screen before anybody opened the list",
            )
        }
    }

    @Test
    fun openingItOffersEveryLanguageAndTheDevice() = runComposeUiTest {
        settings()
        open()

        Language.entries.forEach { language ->
            assertTrue(
                onAllNodesWithText(language.endonym).fetchSemanticsNodes().isNotEmpty(),
                "${language.tag} is missing from the open list",
            )
        }
        // "Follow the device" is the twenty-second answer and a real one, not an absence —
        // storing `en` for somebody who never chose it pins an English app on a Ukrainian phone.
        assertTrue(onAllNodesWithText("Follow the device").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun choosingOneWritesItAndShutsTheList() = runComposeUiTest {
        settings()
        open()

        choose("Русский")

        // **In Russian**, and that is the assertion rather than an inconvenience: choosing a
        // language re-keys the whole tree through `InLanguage`, so the label beside the answer
        // is the first proof that the setting did something more than store a string. A test
        // expecting "Language: Русский" here would be testing a picker that does not work.
        onNodeWithContentDescription("Язык: Русский").performScrollTo().assertIsDisplayed()
        assertEquals(
            0,
            onAllNodesWithText("Українська").fetchSemanticsNodes().size,
            "the list is still open after a choice",
        )
    }

    /**
     * The row in use is marked with a coloured dot, and colour alone is not information — so it
     * is said in words too. This is the same failure the chapter dots had before §6h slice 7.
     */
    @Test
    fun theLanguageInUseSaysSoOutLoud() = runComposeUiTest {
        settings()
        open()

        choose("Русский")
        // Russian from here on, control and description both.
        open("Язык: Русский")

        // Scrolled to for the same reason as everything else here: twelfth of twenty-two.
        onNodeWithContentDescription("Русский — выбран").performScrollTo().assertIsDisplayed()
    }

    // ------------------------------------------------------------------ the harness

    /** The real app, walked to Settings — the same route `SettingsLinksTest` takes. */
    private fun ComposeUiTest.settings() {
        setContent { VintoTheme { App(seeds = { SEED }, vault = MemoryVault()) } }
        waitForIdle()
        press("Settings")
    }

    private fun ComposeUiTest.open(closed: String = CLOSED_DEVICE) {
        press(closed)
    }

    /**
     * Scroll the row into the sheet before pressing it — the same rule as [press] and the same
     * trap, one level in. Russian is twelfth of twenty-two and the list is bounded, so a bare
     * `performClick` lands on the window's origin, which is the scrim, which *dismisses the
     * sheet* — and reports success. The first version of this test did exactly that and the
     * failure it produced ("the closed field still says Follow the device") pointed at the
     * picker rather than at the click.
     */
    private fun ComposeUiTest.choose(name: String) {
        val row = onNodeWithText(name)
        if (!row.isDisplayed()) row.performScrollTo()
        row.performClick()
        waitForIdle()
    }

    /**
     * Scroll first, always. Compose clips `boundsInRoot` to what is on screen, so a control
     * below the fold measures as nothing and `performClick` lands on the window's corner —
     * and reports success, because a press on the corner is a perfectly good press. Settings
     * is the longest screen in the app, so everything here is below the fold.
     */
    private fun ComposeUiTest.press(label: String) {
        val node = onNodeWithContentDescription(label)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
        waitForIdle()
    }

    private companion object {
        const val SEED = 20_260_819L
        const val CLOSED_DEVICE = "Language: Follow the device"
    }
}

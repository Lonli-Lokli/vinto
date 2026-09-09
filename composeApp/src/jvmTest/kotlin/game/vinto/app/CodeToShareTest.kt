package game.vinto.app

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import game.vinto.app.share.CodeToShare
import game.vinto.app.theme.ButtonTone
import game.vinto.app.theme.GameButton
import game.vinto.app.theme.VintoTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The machinery both invitations share: a button, a picture drawn where nobody can see it, and a
 * fall through three steps that must never reach a fourth.
 *
 * All three of these are invisible by construction, which is the reason to assert them. The card
 * is drawn at zero alpha in a host that reports no size, so a version that took up room, or that
 * read itself aloud, or that dropped a tap on a platform with no share sheet would look identical
 * on screen to one that behaved. This is the only place any of that shows.
 */
@OptIn(ExperimentalTestApi::class)
class CodeToShareTest {

    /**
     * Off-screen means off-screen: the card contributes no height to the row it sits in.
     *
     * `OffscreenLayer` reports 0 x 0 to its parent while laying its content out at real pixel
     * size. Get that wrong — wrap the card in a `Box(Modifier.size(0.dp))`, the obvious
     * alternative — and the layer captures nothing; get it wrong the other way and a 300dp card
     * lands in the middle of a settings screen.
     */
    @Test
    fun theCardTakesNoRoomOnTheScreenItIsCapturedFrom() = runComposeUiTest {
        setContent {
            VintoTheme(dark = false) {
                Column {
                    CodeToShare(URL, CAPTION, SUBJECT, BODY, onNoSheet = {}) { send ->
                        GameButton(label = BUTTON, tone = ButtonTone.NEUTRAL, onClick = send)
                    }
                    Text(BELOW)
                }
            }
        }
        waitForIdle()

        val button = onNodeWithContentDescription(BUTTON).fetchSemanticsNode().boundsInRoot
        val below = onNodeWithText(BELOW).fetchSemanticsNode().boundsInRoot
        assertTrue(
            below.top - button.bottom < ROUNDING,
            "the off-screen card pushed the layout down by ${below.top - button.bottom}px",
        )
    }

    /** And is not read aloud either — it is a rendering source, not something on the screen. */
    @Test
    fun theCardIsNotReadAloudToSomebodyWhoCannotSeeIt() = runComposeUiTest {
        setContent {
            VintoTheme(dark = false) {
                CodeToShare(URL, CAPTION, SUBJECT, BODY, onNoSheet = {}) { send ->
                    GameButton(label = BUTTON, tone = ButtonTone.NEUTRAL, onClick = send)
                }
            }
        }
        waitForIdle()

        assertEquals(
            0,
            onAllNodesWithContentDescription(CAPTION).fetchSemanticsNodes().size,
            "the invisible card describes itself to a screen reader",
        )
    }

    /**
     * On a platform with no share sheet the tap reaches the clipboard rather than the floor.
     *
     * The desktop is exactly that platform — both actuals here return false — so this is the
     * real fall-through rather than a mocked one: picture, then text, then [CodeToShare]'s
     * `onNoSheet`. A share button that silently does nothing is the one outcome none of the
     * three may produce, and it is what this would have been before the last step existed.
     */
    @Test
    fun withNoShareSheetAtAllTheTapStillLandsSomewhere() = runComposeUiTest {
        var fellBack = false
        setContent {
            VintoTheme(dark = false) {
                CodeToShare(URL, CAPTION, SUBJECT, BODY, onNoSheet = { fellBack = true }) { send ->
                    GameButton(label = BUTTON, tone = ButtonTone.NEUTRAL, onClick = send)
                }
            }
        }
        waitForIdle()

        onNodeWithContentDescription(BUTTON).performClick()
        waitUntil(timeoutMillis = TIMEOUT) { fellBack }

        assertTrue(fellBack, "the tap was swallowed: no sheet, no clipboard, nothing said")
    }

    private companion object {
        const val URL = "https://vinto.kupalinka.app/r/7KQ2MP"
        const val CAPTION = "7KQ2MP"
        const val SUBJECT = "Join my Vinto game"
        const val BODY = "Join my Vinto game: $URL"
        const val BUTTON = "Share the code"
        const val BELOW = "underneath"
        const val TIMEOUT = 5_000L

        /** Layout lands on device pixels; a fraction of one is not the card taking up room. */
        val ROUNDING = 1.dp.value
    }
}

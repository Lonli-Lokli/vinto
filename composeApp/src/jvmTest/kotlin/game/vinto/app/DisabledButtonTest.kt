package game.vinto.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.app.theme.ButtonTone
import game.vinto.app.theme.GameButton
import game.vinto.app.theme.VintoTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A button with nothing to do says so.
 *
 * `GameButton` had `busy` and nothing else, so "you already asked" was expressible and "there
 * is nothing here to ask for yet" was not. The only way to write the second was a live-looking
 * button whose `onClick` quietly returned — which is what the online screen did: the biggest,
 * brightest control on it did nothing and said nothing for every player who had not been sent
 * a room code, which is every player opening the app for the first time.
 *
 * A dead tap is the worst thing a game's front door can do, because it is indistinguishable
 * from the app being broken. There is no partial credit here: either the control refuses to be
 * pressed and looks like it, or a person taps it twice and puts the phone down.
 */
@OptIn(ExperimentalTestApi::class)
class DisabledButtonTest {

    @Test
    fun aButtonWithNothingToDoRefusesToBePressed() = runComposeUiTest {
        var presses = 0
        setContent {
            VintoTheme {
                GameButton(
                    label = LABEL,
                    tone = ButtonTone.PLAY,
                    onClick = { presses += 1 },
                    enabled = false,
                )
            }
        }

        onNodeWithContentDescription(LABEL).assertIsNotEnabled()
        onNodeWithContentDescription(LABEL).performClick()
        waitForIdle()
        assertEquals(0, presses, "a disabled button ran its action")
    }

    /**
     * And is a real button again the moment its condition is met.
     *
     * The half that matters as much: a control that stays disabled after the thing it was
     * waiting for arrives is worse than one that was never disabled, because the player now has
     * a reason to believe the app is stuck rather than that they have missed a step.
     */
    @Test
    fun andComesBackToLifeWhenThereIsSomethingToDo() = runComposeUiTest {
        var presses = 0
        setContent {
            VintoTheme {
                GameButton(
                    label = LABEL,
                    tone = ButtonTone.PLAY,
                    onClick = { presses += 1 },
                    enabled = true,
                )
            }
        }

        onNodeWithContentDescription(LABEL).assertIsEnabled()
        onNodeWithContentDescription(LABEL).performClick()
        waitForIdle()
        assertEquals(1, presses)
    }

    /** Busy and disabled are different states, and both swallow the tap. */
    @Test
    fun aBusyButtonAlsoSwallowsThePress() = runComposeUiTest {
        var presses = 0
        setContent {
            VintoTheme {
                GameButton(
                    label = LABEL,
                    tone = ButtonTone.PLAY,
                    onClick = { presses += 1 },
                    busy = true,
                )
            }
        }

        onNodeWithContentDescription(LABEL).performClick()
        waitForIdle()
        assertEquals(0, presses, "a second press while the first was in flight got through")
    }

    private companion object {
        const val LABEL = "Join the room"
    }
}

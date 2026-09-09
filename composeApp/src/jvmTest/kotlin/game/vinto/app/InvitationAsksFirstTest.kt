package game.vinto.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.app.link.invitationsAskFirst
import game.vinto.app.theme.VintoTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An invitation asks before it takes a seat — on the client a link can reach unasked.
 *
 * Reported from a real room: a host opened a table, shared it, and read their own QR back. The
 * scan opened the invitation in a browser rather than in the app, and the web client — which is
 * the same Compose UI, so it is not obvious which one you are looking at — joined on load. One
 * person, two vaults, two of the room's four seats, the second under a name they had never
 * seen. A seat token cannot link the two: it is per client by design, and that is what makes
 * reconnecting safe.
 *
 * So the fix is not to recognise the person. It is to stop seating anybody who never asked to
 * sit: on the web a URL is opened by scanners' previews, in-app browsers, link previews and
 * curious taps, and every one of those used to cost a table one of its chairs for the ten
 * minutes a lobby lives. On a phone an App Link means somebody deliberately tapped an
 * invitation in an app that had to resolve it first, so that walk-in stays as it was.
 */
@OptIn(ExperimentalTestApi::class)
class InvitationAsksFirstTest {

    @Test
    fun theClientThatCanBeOpenedByALookAsksFirst() {
        // The JVM is the desktop window, which no link ever reaches; the flag says what each
        // client is, and the web's own actual is what this rule is about.
        assertFalse(invitationsAskFirst, "the desktop is not opened by links")
    }

    @Test
    fun theCardNamesTheRoomAndSaysWhatJoiningCosts() = runComposeUiTest {
        setContent { VintoTheme { InvitationScreen(code = "7KQ2MP", onJoin = {}, onBack = {}) } }

        // The code, because the person is about to act on somebody else's word for where they
        // are going, and it is the one thing they can check against the message they were sent.
        assertTrue(
            onAllNodesWithText("7KQ2MP", substring = true).fetchSemanticsNodes().isNotEmpty(),
            "the invitation does not say which room it is for",
        )
        onNodeWithContentDescription("Take a seat").assertIsDisplayed()
    }

    @Test
    fun andNothingHappensUntilItIsTapped() = runComposeUiTest {
        var joined = 0
        setContent { VintoTheme { InvitationScreen(code = "7KQ2MP", onJoin = { joined++ }, onBack = {}) } }

        assertEquals(0, joined, "the seat was taken by the screen appearing")
        onNodeWithContentDescription("Take a seat").performClick()
        assertEquals(1, joined)
    }
}

package game.vinto.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import game.vinto.app.link.INVITE_HOST
import game.vinto.app.link.Invitations
import game.vinto.app.link.invitationFrom
import game.vinto.app.link.offerOpenedLink
import game.vinto.app.link.openedLink
import game.vinto.app.link.takeOpenedLink
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * An invitation that arrives while the app is already open.
 *
 * Reported from a real evening: the host shared a room, read their own QR back, and the app
 * came to the front and did nothing at all. Both platform halves were there — Android's
 * `onNewIntent` (the manifest's `singleTop` exists for it) and iOS's `HandleOpenedLink` — and
 * both handed the link to a `var` whose only reader was the startup effect, which runs once
 * per composition. So the second invitation of an evening was filed, not read, and stayed
 * pending for whichever cold start came next.
 *
 * The other half of the same fix is the one case where doing nothing is right: a link for the
 * room you are *already sitting in*. That must not rebuild the screen, and must not leave the
 * link lying about either.
 */
@OptIn(ExperimentalTestApi::class)
class InvitationWhileRunningTest {

    // The hand-off is process-wide, as the platform entry points need it to be, so each case
    // starts from empty and leaves nothing behind for the next one.
    @BeforeTest fun clear() = drain()

    @AfterTest fun tidy() = drain()

    private fun drain() {
        takeOpenedLink()
    }

    @Test
    fun anInvitationThatArrivesWhileTheAppRunsIsActedOn() = runComposeUiTest {
        val taken = mutableListOf<String>()
        setContent { Invitations(ready = true, atRoom = { null }, onInvited = { taken += it }) }
        waitForIdle()

        offerOpenedLink("https://$INVITE_HOST/r/7KQ2MP")

        waitUntil(timeoutMillis = WAIT_MS) { taken.isNotEmpty() }
        assertEquals(listOf("7KQ2MP"), taken)
        assertNull(openedLink.value, "the link was left pending for some later launch")
    }

    @Test
    fun anInvitationToTheRoomYouAreSittingInIsAnsweredByStayingPut() = runComposeUiTest {
        val taken = mutableListOf<String>()
        setContent { Invitations(ready = true, atRoom = { "7KQ2MP" }, onInvited = { taken += it }) }
        waitForIdle()

        offerOpenedLink("https://$INVITE_HOST/r/7kq2mp")

        // Consumed — not honoured, and not left behind: a pending link outlives the moment it
        // was about, and would pull somebody into this room on a launch hours later.
        waitUntil(timeoutMillis = WAIT_MS) { openedLink.value == null }
        assertTrue(taken.isEmpty(), "walking into the room we are in rebuilds it for nothing")
    }

    @Test
    fun untilStartupHasHadItsTurnNothingIsTaken() = runComposeUiTest {
        val taken = mutableListOf<String>()
        setContent { Invitations(ready = false, atRoom = { null }, onInvited = { taken += it }) }
        waitForIdle()

        offerOpenedLink("https://$INVITE_HOST/r/7KQ2MP")
        waitForIdle()

        // The launch that a link *caused* is the startup effect's business. Both reading it
        // would open the room twice.
        assertTrue(taken.isEmpty())
        assertEquals("https://$INVITE_HOST/r/7KQ2MP", openedLink.value, "startup's link was eaten")
    }

    @Test
    fun whereTheAppIsDecidesWhatALinkMeans() {
        assertEquals("7KQ2MP", invitationFrom("https://$INVITE_HOST/r/7KQ2MP", atRoom = null))
        assertEquals("7KQ2MP", invitationFrom("https://$INVITE_HOST/r/7KQ2MP", atRoom = "ABCDEF"))
        assertNull(invitationFrom("https://$INVITE_HOST/r/7KQ2MP", atRoom = "7KQ2MP"))
        assertNull(invitationFrom("https://$INVITE_HOST/r/7kq2mp", atRoom = "7KQ2MP"), "case")
        assertNull(invitationFrom("https://example.com/r/7KQ2MP", atRoom = null), "not an invite")
        assertNull(invitationFrom(null, atRoom = null))
    }

    private companion object {
        const val WAIT_MS = 2_000L
    }
}

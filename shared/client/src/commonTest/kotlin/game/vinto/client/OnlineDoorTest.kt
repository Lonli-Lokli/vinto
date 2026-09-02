package game.vinto.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The front door of online play, decided by the pure model rather than by a composable.
 *
 * What happened: somebody opened the public-room browser in aeroplane mode and was shown a
 * failure, with a hostname in it, for a thing the phone knew could not work before the tap.
 * The platform can say "there is no network at all" reliably — that is what aeroplane mode
 * is — and when it does, the door is shut and says why, instead of opening onto a screen
 * whose only content is an apology.
 *
 * The other two answers matter as much. A platform that cannot tell (the desktop, a browser
 * that only knows it is not *definitely* offline) must not nag: a false "you are offline" on
 * a working connection is worse than the failure it replaces, so only certainty shuts the
 * door. And a captive portal or a dead Wi-Fi is not detectable this way, which is why the
 * screens behind the door keep their own failure handling — this is a door, not a promise.
 */
class OnlineDoorTest {

    @Test
    fun noNetworkAtAllShutsTheDoorAndSaysSo() {
        val door = onlineDoor(Reachability.OFFLINE)

        assertFalse(door.open, "a tap that cannot work was allowed through")
        assertEquals(OnlineWord.OFFLINE, door.word)
    }

    @Test
    fun aPlatformThatCannotTellDoesNotNag() {
        val door = onlineDoor(Reachability.UNKNOWN)

        assertTrue(door.open, "a desktop with no signal to read was told it is offline")
        assertEquals(OnlineWord.READY, door.word)
    }

    @Test
    fun aNetworkOpensTheDoor() {
        val door = onlineDoor(Reachability.ONLINE)

        assertTrue(door.open)
        assertEquals(OnlineWord.READY, door.word)
    }
}

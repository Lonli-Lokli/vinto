package game.vinto.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * What a status means, and what a player is told about it.
 *
 * All four connectors used to discard the HTTP status entirely: a 404, a 429 and a 503 were
 * handed to a JSON parser along with whatever the service had said in plain text, so a person
 * who mistyped a room code was shown "Unexpected JSON token at offset 0". That is not a small
 * cosmetic fault — it is the difference between a player retyping the code and a player
 * deciding the app is broken.
 *
 * These cases are in `commonTest`, so the mapping is identical on the JVM, in Node, in a
 * browser and on a phone. Four transports, one vocabulary, is the whole point of the type.
 */
class RoomTroubleTest {

    @Test
    fun aGoodAnswerIsHandedBackUntouched() {
        assertEquals("""{"rooms":[]}""", requireOk(200, """{"rooms":[]}"""))
        assertEquals("", requireOk(204, ""))
    }

    /** The four the room service actually answers with, each meaning something different. */
    @Test
    fun theServicesOwnStatusesAreRecognised() {
        assertEquals(RoomTrouble.NO_SUCH_ROOM, troubleFor(404))
        assertEquals(RoomTrouble.BUSY, troubleFor(429))
        assertEquals(RoomTrouble.CLOSED, troubleFor(503))
        assertEquals(RoomTrouble.BROKEN, troubleFor(500))
        assertEquals(RoomTrouble.REFUSED, troubleFor(400))
        assertEquals(null, troubleFor(200), "a good answer is not trouble")
    }

    /**
     * And the ones that cannot be waited out are marked as such.
     *
     * This is the property `RemoteRoom` reads, and it is the one that stops a mistyped code
     * from spinning for ever: a code nobody has issued will not start existing because the
     * client asked a fourth time.
     */
    @Test
    fun aCodeNobodyHasIsNotWorthWaitingFor() {
        assertTrue(RoomServiceException(RoomTrouble.NO_SUCH_ROOM, "no such room").permanent)
        assertTrue(RoomServiceException(RoomTrouble.CLOSED, "closed").permanent)
        assertTrue(RoomServiceException(RoomTrouble.REFUSED, "no").permanent)

        assertTrue(!RoomServiceException(RoomTrouble.OFFLINE, "no signal").permanent, "a tunnel ends")
        assertTrue(!RoomServiceException(RoomTrouble.BUSY, "full").permanent, "a seat frees up")
        assertTrue(!RoomServiceException(RoomTrouble.BROKEN, "500").permanent, "a deploy finishes")
    }

    /** The service's own words reach the player, because they are better than any invented here. */
    @Test
    fun whatTheServiceSaidIsWhatIsShown() {
        val plain = assertFailsWith<RoomServiceException> { requireOk(404, "no such room") }
        assertEquals("no such room", plain.message)

        val json = assertFailsWith<RoomServiceException> {
            requireOk(503, """{"error":"could not mint a code"}""")
        }
        assertEquals("could not mint a code", json.message)
    }

    /**
     * But a page of markup is not words, and a toast is not a place to put one.
     *
     * The body of a 502 from something in front of the service is an HTML error page. Showing
     * its first line to a player is worse than showing nothing, which is why there is a
     * fallback sentence with the status in it.
     */
    @Test
    fun anErrorPageIsNotShownToAnybody() {
        val html = assertFailsWith<RoomServiceException> {
            requireOk(502, "<!doctype html><html><head><title>502 Bad Gateway</title>")
        }
        assertEquals("the room service answered 502", html.message)
        assertEquals(RoomTrouble.BROKEN, html.trouble)

        val empty = assertFailsWith<RoomServiceException> { requireOk(429, "   ") }
        assertEquals("the room service answered 429", empty.message)
    }
}

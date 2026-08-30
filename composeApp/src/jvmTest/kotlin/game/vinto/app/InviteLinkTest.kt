package game.vinto.app

import game.vinto.app.link.INVITE_HOST
import game.vinto.app.link.inviteLink
import game.vinto.app.link.roomCodeFrom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What an invite link may name, and what it may not.
 *
 * The generous half and the strict half are both deliberate, and they are about different
 * people. Generous about *shape*, because everything it accepts is something a real person
 * will actually hand the app — a link they tapped, a link they pasted with a trailing space,
 * a bare code they copied out of a message, any of it in the wrong case. Refusing those
 * teaches nobody anything.
 *
 * Strict about *content*, because the code that comes out is used to open a socket. It must
 * be one the registry could have issued, judged by the same `looksLikeRoomCode` the Worker
 * applies before it wakes the Durable Object that knows every live room — which is why that
 * function moved into `shared/protocol` rather than being written twice.
 */
class InviteLinkTest {

    @Test
    fun aLinkNamesItsRoom() {
        assertEquals("7KQ2MP", roomCodeFrom("https://$INVITE_HOST/r/7KQ2MP"))
    }

    @Test
    fun theLinkAndTheParserAgree() {
        // The one property that matters most, and the cheapest to get wrong: what the lobby
        // shares must be what an opened link resolves to.
        for (code in listOf("7KQ2MP", "ABCDEF", "23456789ABCDEFGHJKMNPQRSTUVWXYZ".take(6))) {
            assertEquals(code, roomCodeFrom(inviteLink(code)), "round trip failed for $code")
        }
    }

    @Test
    fun everythingAPersonWouldActuallyPasteIsAccepted() {
        val cases = mapOf(
            "https://$INVITE_HOST/r/7KQ2MP" to "7KQ2MP",
            "http://$INVITE_HOST/r/7KQ2MP" to "7KQ2MP",
            "https://$INVITE_HOST/r/7kq2mp" to "7KQ2MP",
            "  https://$INVITE_HOST/r/7KQ2MP  " to "7KQ2MP",
            "https://$INVITE_HOST/r/7KQ2MP/" to "7KQ2MP",
            "https://$INVITE_HOST/r/7KQ2MP?utm_source=whatsapp" to "7KQ2MP",
            "https://$INVITE_HOST/r/7KQ2MP#play" to "7KQ2MP",
            "vinto://7KQ2MP" to "7KQ2MP",
            "vinto://r/7KQ2MP" to "7KQ2MP",
            "/r/7KQ2MP" to "7KQ2MP",
            "7KQ2MP" to "7KQ2MP",
            "7kq2mp" to "7KQ2MP",
        )
        for ((input, expected) in cases) {
            assertEquals(expected, roomCodeFrom(input), "rejected something a person would paste: '$input'")
        }
    }

    @Test
    fun nothingThatCouldNotHaveBeenIssuedGetsThrough() {
        val refused = listOf(
            null,
            "",
            "   ",
            "https://$INVITE_HOST/r/",
            "https://$INVITE_HOST/r/TOOLONG1",
            "https://$INVITE_HOST/r/SHORT",
            // 0, O, 1, I and L are not in the alphabet: they are the characters that get
            // misread when somebody reads a code aloud, which is the whole reason for it.
            "https://$INVITE_HOST/r/7KQ2M0",
            "https://$INVITE_HOST/r/OOOOOO",
            "https://$INVITE_HOST/r/IIIIII",
            "https://$INVITE_HOST/lobby/7KQ2MP",
            "https://$INVITE_HOST/7KQ2MP",
            "not a link at all",
        )
        for (input in refused) {
            assertNull(roomCodeFrom(input), "accepted something that could not have been issued: '$input'")
        }
    }

    @Test
    fun someoneElsesSiteIsNotAnInvitation() {
        // A link to another host that happens to end in six legal characters is not an
        // invite, and treating it as one would let any page anywhere put somebody in a room.
        assertNull(roomCodeFrom("https://example.com/r/7KQ2MP"))
        assertNull(roomCodeFrom("https://vinto.kupalinka.app.evil.test/r/7KQ2MP"))
        assertNull(roomCodeFrom("https://evil.test/vinto.kupalinka.app/r/7KQ2MP"))
    }
}

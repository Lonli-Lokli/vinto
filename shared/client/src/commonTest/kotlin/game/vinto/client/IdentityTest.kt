package game.vinto.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Who the device is, and what it holds: the id is minted once and then left alone, and a
 * seat token lives and dies with its room.
 */
class IdentityTest {

    @Test
    fun aGuestIdIsMintedOnceAndKept() {
        val vault = MemoryVault()
        var draws = 0
        val entropy = {
            draws++
            (draws * 7919L)
        }

        val first = vault.identity(entropy)
        val second = vault.identity(entropy)

        assertEquals(first.guestId, second.guestId, "the id survives asking twice")
        assertEquals(2, draws, "entropy was drawn only for the mint — two longs, once")
        assertTrue(first.guestId.startsWith("guest-"))
    }

    @Test
    fun aSeatTokenBelongsToItsRoomAlone() {
        val vault = MemoryVault()
        vault.saveSeatToken("ab12cd", "tok-1")

        assertEquals("tok-1", vault.seatToken("AB12CD"), "codes are case-insensitive")
        assertNull(vault.seatToken("XY99ZZ"), "another room's seat is another seat")

        vault.forgetSeatToken("ab12cd")
        assertNull(vault.seatToken("AB12CD"), "a credential for nothing is a liability")
    }

    @Test
    fun theNicknameIsRememberedAndBlankForgets() {
        val vault = MemoryVault()
        vault.rememberNickname("Ann")
        assertEquals("Ann", vault.identity { 1L }.nickname)

        vault.rememberNickname("  ")
        assertEquals("", vault.identity { 2L }.nickname)
    }
}

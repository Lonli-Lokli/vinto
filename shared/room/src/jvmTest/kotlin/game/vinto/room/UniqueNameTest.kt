package game.vinto.room

import game.vinto.protocol.looksMinted
import game.vinto.protocol.mintNickname
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * No two seats at one table are called the same thing.
 *
 * There are 1024 minted names and four seats, so a collision is rare and entirely possible. It
 * matters more than it reads: the felt draws a seat by **name**, so two seats sharing one would
 * share a face and a ground as well — and the whole point of letting somebody choose a face is
 * that it is theirs.
 */
class UniqueNameTest {

    @Test
    fun aSecondArrivalWithTheSameNameGetsADifferentOne() {
        val wanted = mintNickname(1)
        val given = uniqueNickname(wanted, seatIndex = 1, taken = setOf(wanted))

        assertTrue(given != wanted, "two seats were both called $wanted")
        assertTrue(looksMinted(given), "the substitute is not a name the room would accept: $given")
    }

    @Test
    fun aNameNobodyElseHasIsLeftAlone() {
        // The common case, and the one a player notices: choosing a name and keeping it.
        val wanted = mintNickname(7)
        assertEquals(wanted, uniqueNickname(wanted, seatIndex = 2, taken = setOf(mintNickname(8))))
    }

    @Test
    fun aWholeTableEndsUpWithFourDifferentNames() {
        // Everybody asking for the same name is the worst case and the one worth pinning: four
        // seats, one wanted name, and four distinct results.
        val wanted = mintNickname(3)
        val given = mutableSetOf<String>()
        repeat(4) { seat -> given += uniqueNickname(wanted, seat, given) }

        assertEquals(4, given.size, "a table of four shared a name: $given")
        assertTrue(given.all(::looksMinted), "a substitute was not a minted name: $given")
    }

    @Test
    fun somethingThatIsNotAMintedNameIsStillReplaced() {
        // The original job of `sanitiseNickname`, which this must not lose: the room's door is
        // open to anything that speaks the protocol, and free text on another player's screen is
        // the thing minting exists to prevent.
        val given = uniqueNickname("<script>alert(1)</script>", seatIndex = 0, taken = emptySet())
        assertTrue(looksMinted(given), "raw text survived the door: $given")
    }
}

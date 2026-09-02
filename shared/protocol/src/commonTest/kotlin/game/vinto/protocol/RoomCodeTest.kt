package game.vinto.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The shape of a room code, held where the three callers that agree on it can all run it.
 *
 * `InviteLinkTest` in the app already exercises this through `roomCodeFrom`, on the JVM. The
 * Worker asks the same question of every request before the registry is woken, and a phone
 * asks it before spending a round trip — so the rule is pinned here in `commonTest`, on every
 * target, by itself: a refusal at the stateless edge that disagreed with the registry would
 * be a code that works from one client and not another.
 */
class RoomCodeTest {

    @Test
    fun aCodeTheRegistryCouldHaveIssuedLooksLikeOne() {
        assertTrue(looksLikeRoomCode("7KQ2MP"))
        assertTrue(looksLikeRoomCode("7kq2mp"), "a code read down a phone is typed however it is typed")
        assertTrue(looksLikeRoomCode(CODE_ALPHABET.take(CODE_LENGTH)))
        assertTrue(looksLikeRoomCode(CODE_ALPHABET.takeLast(CODE_LENGTH)))
    }

    /** Every glyph that is read wrong out loud is out: no `0`/`O`, no `1`/`I`/`L`. */
    @Test
    fun theLookalikesAreNotInTheAlphabet() {
        "0OIL1oil".forEach { glyph ->
            assertFalse(glyph.uppercaseChar() in CODE_ALPHABET, "'$glyph' is in the alphabet")
            assertFalse(looksLikeRoomCode("7KQ2M$glyph"), "'$glyph' passed as a code character")
        }
    }

    @Test
    fun onlySixCharactersWillDo() {
        assertFalse(looksLikeRoomCode(""))
        assertFalse(looksLikeRoomCode("7KQ2M"))
        assertFalse(looksLikeRoomCode("7KQ2MPX"))
        assertFalse(looksLikeRoomCode(" 7KQ2MP"), "a shape check does not trim; the caller does")
        assertFalse(looksLikeRoomCode("7KQ-2MP"))
    }

    /**
     * The alphabet itself: 31 distinct upper-case symbols, so 31^6 codes — about 900 million,
     * which is the number `GuessLimitTest`'s arithmetic rests on.
     */
    @Test
    fun theAlphabetIsThirtyOneUnambiguousSymbols() {
        assertEquals(31, CODE_ALPHABET.length)
        assertEquals(CODE_ALPHABET.length, CODE_ALPHABET.toSet().size, "a repeated symbol shrinks the keyspace")
        assertEquals(CODE_ALPHABET, CODE_ALPHABET.uppercase(), "the registry issues upper case")
        assertEquals(6, CODE_LENGTH)
    }
}

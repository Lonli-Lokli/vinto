package game.vinto.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The vocabulary a name may come from, and the door that only opens for it.
 *
 * These are not tests about spelling. The whole reason names are minted rather than typed is
 * that a typed one is user-generated content — text one player authors and three strangers
 * read — which obliges the app to filter, report and block under App Store Review Guideline
 * 1.2. That argument only holds while **nothing outside this vocabulary can reach another
 * player's screen**, so the assertions below are the argument, written down.
 */
class NicknameTest {

    @Test
    fun everyNameThisCanMintIsOneItRecognises() {
        // The whole space, not a sample: 1024 pairings is small enough to check exhaustively,
        // and a single bad one is a name the client shows and the room then replaces.
        repeat(NICKNAME_COUNT) { seed ->
            val name = mintNickname(seed.toLong())
            assertTrue(looksMinted(name), "minted a name the room would refuse: $name")
        }
    }

    @Test
    fun theWholeVocabularyIsReachable() {
        val seen = (0 until NICKNAME_COUNT).map { mintNickname(it.toLong()) }.toSet()
        assertEquals(
            NICKNAME_COUNT,
            seen.size,
            "the seeds 0 until $NICKNAME_COUNT should give every name exactly once",
        )
    }

    /**
     * Total for every `Long`, including the two that break the obvious implementation.
     *
     * `Long.MIN_VALUE` has no positive counterpart, so `abs` returns it unchanged and `%` then
     * yields a negative index — an exception rather than a name, on one seed in 2^64 that a
     * platform's random number generator will eventually produce.
     */
    @Test
    fun anySeedAtAllGivesAName() {
        listOf(Long.MIN_VALUE, Long.MAX_VALUE, 0L, -1L, -7L, 12_345_678_901L).forEach { seed ->
            assertTrue(looksMinted(mintNickname(seed)), "seed $seed did not give a usable name")
        }
    }

    @Test
    fun theSameSeedAlwaysGivesTheSameName() {
        assertEquals(mintNickname(42L), mintNickname(42L))
    }

    /**
     * Consecutive seeds have to look different, which is a product requirement rather than a
     * mathematical one: "another name" is a button, and a player who presses it three times
     * wants three names rather than three adjectives in front of one noun.
     */
    @Test
    fun steppingTheSeedChangesTheNameVisibly() {
        val run = (100L..104L).map(::mintNickname)
        assertEquals(run.size, run.toSet().size, "consecutive seeds repeated a name: $run")
        assertEquals(
            run.size,
            run.map { it.substringAfter(' ') }.toSet().size,
            "consecutive seeds only changed the adjective: $run",
        )
    }

    /** Every name fits the 16 characters a seat is allowed, with the space counted. */
    @Test
    fun everyNameFitsASeatPlate() {
        val longest = (0 until NICKNAME_COUNT).map { mintNickname(it.toLong()) }.maxBy { it.length }
        assertTrue(longest.length <= SEAT_NAME_LIMIT, "'$longest' is ${longest.length} characters")
    }

    /**
     * What the door refuses, which is the half that matters.
     *
     * The room applies [looksMinted] to whatever arrives on the wire, because the app having no
     * text field says nothing about what a modified client can send. Each case below is a shape
     * somebody probing that door would actually try.
     */
    @Test
    fun nothingOutsideTheVocabularyIsAccepted() {
        listOf(
            "" to "empty",
            " " to "a space",
            "Quiet" to "one word",
            "Quiet Heron Two" to "three words",
            "Otter Quiet" to "the right words the wrong way round",
            "quiet heron" to "lowercase",
            "QUIET HERON" to "uppercase",
            "Quiet  Heron" to "two spaces",
            " Quiet Heron" to "a leading space",
            "Quiet Heron " to "a trailing space",
            "Quiet Wolf" to "a noun that is not in the list",
            "Angry Heron" to "an adjective that is not in the list",
            "<b>Quiet</b> Heron" to "markup",
        ).forEach { (name, what) ->
            assertFalse(looksMinted(name), "$what was accepted: '$name'")
        }
    }

    /** The two halves are disjoint, so a name can never be read as its own mirror image. */
    @Test
    fun noWordIsBothAnAdjectiveAndANoun() {
        val both = NICKNAME_ADJECTIVES.toSet() intersect NICKNAME_NOUNS.toSet()
        assertTrue(both.isEmpty(), "these words are in both lists: $both")
    }

    @Test
    fun neitherListRepeatsAWord() {
        assertEquals(NICKNAME_ADJECTIVES.size, NICKNAME_ADJECTIVES.toSet().size, "adjectives repeat")
        assertEquals(NICKNAME_NOUNS.size, NICKNAME_NOUNS.toSet().size, "nouns repeat")
    }

    /**
     * Plain ASCII letters, capitalised, and nothing else.
     *
     * The name crosses the wire, lands in a seat plate, and is read aloud by people playing in
     * two languages. A stray apostrophe or accent would be a rendering question in four places
     * for no gain, and a digit would let a minted name imitate the "Player 2" placeholders this
     * replaced.
     */
    @Test
    fun everyWordIsPlainCapitalisedAscii() {
        (NICKNAME_ADJECTIVES + NICKNAME_NOUNS).forEach { word ->
            assertTrue(word.isNotEmpty(), "an empty word is in the vocabulary")
            assertTrue(word.first().isUpperCase(), "'$word' is not capitalised")
            assertTrue(
                word.all { it in 'a'..'z' || it in 'A'..'Z' },
                "'$word' has something other than an ASCII letter in it",
            )
        }
    }

    private companion object {
        /** `MAX_NICKNAME_LENGTH` in `shared/room`, which cannot be imported from here. */
        const val SEAT_NAME_LIMIT = 16
    }
}

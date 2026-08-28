package game.vinto.shapes

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The first genuine cross-language parity check.
 *
 * The vectors are the contents of `fixtures/prng/vectors.json` — the very same committed
 * file the TypeScript test reads — embedded at build time by
 * `:shared:shapes:generatePrngVectorsSource` rather than transcribed into Kotlin. A
 * transcription could drift from the file TypeScript reads, and this test would then pass
 * while proving nothing.
 *
 * This lives in `commonTest` so it runs on every configured target, including iOS, where
 * there is no filesystem to read a fixture from. If the two implementations ever disagree,
 * it fails before any engine code is ported, which is the cheapest possible place to find
 * out.
 *
 * Test names are camelCase, not backticked prose: Kotlin only permits spaces in identifiers
 * on the JVM, and this must compile for JS and Native too.
 */
class PrngVectorsTest {

    @Serializable
    private data class Vectors(
        val algorithm: String,
        val increment: Long,
        val sequences: List<Sequence>,
        val boundedSequences: List<BoundedSequence>,
        val shuffles: List<ShuffleVector>,
    )

    @Serializable
    private data class Sequence(val seed: Long, val values: List<Long>, val finalState: Long)

    @Serializable
    private data class BoundedSequence(val seed: Long, val bound: Int, val values: List<Long>)

    @Serializable
    private data class ShuffleVector(
        val seed: Long,
        val deckSize: Int,
        val order: List<Int>,
        val finalState: Long,
    )

    private companion object {
        private val json = Json { ignoreUnknownKeys = true }
        val vectors: Vectors = json.decodeFromString(PrngVectorsFixture.JSON)
    }

    @Test
    fun readsTheSharedVectorFile() {
        assertEquals("mulberry32", vectors.algorithm)
        assertEquals(0x6D2B79F5L, vectors.increment)
        assertTrue(vectors.sequences.isNotEmpty())
    }

    @Test
    fun reproducesEveryPublishedSequence() {
        for (sequence in vectors.sequences) {
            var state = Prng.seed(sequence.seed)
            val produced = sequence.values.map {
                val result = Prng.next(state)
                state = result.state
                result.value
            }

            assertEquals(sequence.values, produced, "sequence mismatch for seed ${sequence.seed}")
            assertEquals(sequence.finalState, state, "final state mismatch for seed ${sequence.seed}")
        }
    }

    @Test
    fun reproducesEveryPublishedBoundedSequence() {
        for (bounded in vectors.boundedSequences) {
            var state = Prng.seed(bounded.seed)
            val produced = bounded.values.map {
                val result = Prng.nextInt(state, bounded.bound)
                state = result.state
                result.value
            }

            assertEquals(
                bounded.values,
                produced,
                "bounded mismatch for seed ${bounded.seed} bound ${bounded.bound}",
            )
        }
    }

    @Test
    fun reproducesEveryPublishedShuffle() {
        for (shuffleVector in vectors.shuffles) {
            val deck = (0 until shuffleVector.deckSize).toList()
            val result = Prng.shuffle(deck, Prng.seed(shuffleVector.seed))

            assertEquals(
                shuffleVector.order,
                result.items,
                "shuffle order mismatch for seed ${shuffleVector.seed}",
            )
            assertEquals(shuffleVector.finalState, result.state)
        }
    }

    @Test
    fun stateStaysWithinUint32AcrossManyDraws() {
        // The trap this guards: holding the state in a signed Int silently corrupts any
        // value at or above 2^31.
        var state = Prng.seed(0xFFFFFFFFL)
        repeat(1000) {
            val result = Prng.next(state)
            assertTrue(result.state in 0..0xFFFFFFFFL, "state escaped uint32: ${result.state}")
            assertTrue(result.value in 0..0xFFFFFFFFL, "value escaped uint32: ${result.value}")
            state = result.state
        }
    }

    @Test
    fun nextIntIsNeverNegative() {
        // Kotlin's % is negative for a negative left operand; JavaScript's would not be.
        var state = Prng.seed(7)
        repeat(500) {
            val result = Prng.nextInt(state, 54)
            assertTrue(result.value in 0..53, "nextInt out of range: ${result.value}")
            state = result.state
        }
    }

    // --- properties the vectors cannot state ------------------------------------------------

    @Test
    fun nextIsAPureFunctionOfTheState() {
        // Everything downstream — replay, parity, a seeded deal — rests on this one line.
        val state = Prng.seed(2026)
        assertEquals(Prng.next(state), Prng.next(state))
    }

    @Test
    fun nextIntStaysInsideItsBound() {
        var state = Prng.seed(7)
        repeat(500) {
            val result = Prng.nextInt(state, 54)
            assertTrue(result.value >= 0, "nextInt returned ${result.value}")
            assertTrue(result.value < 54, "nextInt returned ${result.value}")
            state = result.state
        }
    }

    @Test
    fun aBoundOfOneAlwaysGivesZero() {
        assertEquals(0, Prng.nextInt(Prng.seed(99), 1).value)
    }

    @Test
    fun anImpossibleBoundIsRefused() {
        for (bound in listOf(0, -1)) {
            assertFailsWith<IllegalArgumentException>("bound $bound was accepted") {
                Prng.nextInt(Prng.seed(1), bound)
            }
        }
    }

    @Test
    fun aShuffleIsAPermutationAndLeavesItsInputAlone() {
        val deck = (0 until 54).toList()
        val result = Prng.shuffle(deck, Prng.seed(31337))

        assertEquals((0 until 54).toList(), deck, "shuffle mutated the list it was given")
        assertEquals(deck, result.items.sorted(), "cards were lost or duplicated")
    }

    @Test
    fun differentSeedsGiveDifferentOrders() {
        val deck = (0 until 54).toList()

        assertTrue(
            Prng.shuffle(deck, Prng.seed(1)).items != Prng.shuffle(deck, Prng.seed(2)).items,
            "two seeds produced the same shuffle",
        )
    }

    @Test
    fun shufflingNothingOrOneThingIsNotASpecialCase() {
        assertEquals(emptyList(), Prng.shuffle(emptyList<Int>(), Prng.seed(1)).items)
        assertEquals(listOf(42), Prng.shuffle(listOf(42), Prng.seed(1)).items)
    }

    @Test
    fun seedingNormalisesToUnsignedThirtyTwoBits() {
        // TypeScript stores the state in a `number` and masks it; Kotlin carries it in a Long
        // because a signed Int would corrupt anything at or above 2^31. Both must land on the
        // same value or every later draw diverges.
        assertEquals(0L, Prng.seed(0))
        assertEquals(1L, Prng.seed(1))
        assertEquals(0xFFFFFFFFL, Prng.seed(-1))
        assertEquals(0L, Prng.seed(0x1_0000_0000L))
    }
}

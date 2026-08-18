package game.vinto.shapes

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The first genuine cross-language parity check.
 *
 * This reads `fixtures/prng/vectors.json` — the very same committed file the TypeScript
 * test reads — rather than a Kotlin copy of the numbers. If the two implementations ever
 * disagree, this fails before any engine code is ported, which is the cheapest possible
 * place to find out.
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

    private val vectors: Vectors = run {
        // kmp/shared/shapes -> repo root
        val file = File("../../../fixtures/prng/vectors.json")
        assertTrue(file.exists(), "missing shared vector file at ${file.absolutePath}")
        Json { ignoreUnknownKeys = true }.decodeFromString(file.readText())
    }

    @Test
    fun `reads the shared vector file`() {
        assertEquals("mulberry32", vectors.algorithm)
        assertEquals(0x6D2B79F5L, vectors.increment)
        assertTrue(vectors.sequences.isNotEmpty())
    }

    @Test
    fun `reproduces every published sequence`() {
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
    fun `reproduces every published bounded sequence`() {
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
    fun `reproduces every published shuffle`() {
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
    fun `state stays within uint32 across many draws`() {
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
    fun `nextInt is never negative`() {
        // Kotlin's % is negative for a negative left operand; JavaScript's would not be.
        var state = Prng.seed(7)
        repeat(500) {
            val result = Prng.nextInt(state, 54)
            assertTrue(result.value in 0..53, "nextInt out of range: ${result.value}")
            state = result.state
        }
    }
}

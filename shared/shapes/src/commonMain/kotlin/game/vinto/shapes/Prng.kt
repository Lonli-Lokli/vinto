package game.vinto.shapes

/**
 * mulberry32 — the seeded generator that makes the engine reproducible.
 *
 * Must match `legacy-web/packages/shapes/src/lib/prng.ts` exactly; the contract and its traps are
 * documented in `docs/game-engine/RECORDING.md`, and `fixtures/prng/vectors.json` is the
 * shared test vector file both implementations verify against.
 *
 * Kotlin-specific care, all of it load-bearing:
 *  - the generator state is an unsigned 32-bit value, so it is carried as [Long] masked to
 *    32 bits; a signed [Int] would corrupt any state at or above 2^31 when serialised
 *  - JavaScript's `Math.imul` wraps like Kotlin's `Int * Int`, and `ushr` matches `>>>`
 *  - `nextInt` must take the modulo in unsigned space: Kotlin's `%` yields a negative
 *    result for a negative left operand, JavaScript's would not
 */
object Prng {

    private const val INCREMENT = 0x6D2B79F5L
    private const val UINT32_MASK = 0xFFFFFFFFL

    /** A generated value together with the advanced generator state. */
    data class Value(val value: Long, val state: Long)

    /** A shuffled list together with the advanced generator state. */
    data class Shuffle<T>(val items: List<T>, val state: Long)

    /** Normalises any seed to a valid uint32 generator state. */
    fun seed(seed: Long): Long = seed and UINT32_MASK

    fun next(state: Long): Value {
        val advanced = (state + INCREMENT) and UINT32_MASK

        var t = advanced.toInt()
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))

        val out = (t xor (t ushr 14)).toLong() and UINT32_MASK
        return Value(value = out, state = advanced)
    }

    /**
     * Uniform-ish integer in `[0, bound)`. Modulo rather than rejection sampling: the bias
     * is negligible for this game's bounds (at most 54) and modulo is trivially identical
     * across languages, which rejection sampling is not.
     */
    fun nextInt(state: Long, bound: Int): Value {
        require(bound > 0) { "Prng.nextInt requires a positive bound, got $bound" }

        val next = next(state)
        return Value(value = next.value % bound, state = next.state)
    }

    /** Fisher-Yates, descending — the loop order is part of the cross-language contract. */
    fun <T> shuffle(items: List<T>, state: Long): Shuffle<T> {
        val shuffled = items.toMutableList()
        var current = state

        for (i in shuffled.lastIndex downTo 1) {
            val next = nextInt(current, i + 1)
            current = next.state

            val j = next.value.toInt()
            val swap = shuffled[i]
            shuffled[i] = shuffled[j]
            shuffled[j] = swap
        }

        return Shuffle(items = shuffled, state = current)
    }
}

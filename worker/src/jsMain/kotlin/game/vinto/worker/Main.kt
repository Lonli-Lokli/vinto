package game.vinto.worker

import game.vinto.shapes.Prng
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * Platform-gate payload for `openspec/.../migrate-to-kotlin-multiplatform` task 2a.1.
 *
 * This is not the real Worker. It exercises the things whose Kotlin/JS cost we need to
 * know before committing to the Cloudflare design — the seeded PRNG, a shuffle over a
 * 54-card deck, and kotlinx.serialization round-tripping — so the production webpack
 * bundle it produces is a realistic floor for the engine bundle that follows.
 */

/** A 52-card deck plus two jokers. */
private const val DECK_SIZE = 54

/** The seed the cross-language gate number is published for; see the verification checklist. */
private const val GATE_SEED = 42L

private const val GATE_PREVIEW_CARDS = 5

@Serializable
private data class Deal(val seed: Long, val order: List<Int>, val rngState: Long)

private fun deal(seed: Long): Deal {
    val shuffled = Prng.shuffle((0 until DECK_SIZE).toList(), Prng.seed(seed))
    return Deal(seed = seed, order = shuffled.items, rngState = shuffled.state)
}

fun main() {
    // Cloudflare loads this same module to reach the exports in `Room.kt`, and module-level
    // work runs on every isolate start. The self-check is for `jsNodeProductionRun`, so it
    // stays out of the Worker's path: a Worker isolate has no `process`.
    val isNode = js(
        "typeof globalThis.process !== 'undefined' && " +
            "globalThis.process.versions != null && " +
            "globalThis.process.versions.node != null",
    ) as Boolean
    if (!isNode) return

    val json = Json { prettyPrint = false }

    // Deal, serialise, parse back, and verify the round trip — the same shape of work the
    // Durable Object will do on every action.
    val dealt = deal(GATE_SEED)
    val encoded = json.encodeToString(dealt)
    val decoded = json.decodeFromString<Deal>(encoded)

    check(decoded == dealt) { "serialisation round trip failed" }
    check(decoded.order.size == DECK_SIZE) { "deck size wrong" }
    // toSortedSet() is JVM-only stdlib; toSet() is available on every target.
    check(decoded.order.toSet().size == DECK_SIZE) { "shuffle is not a permutation" }

    println("gate ok: rngState=${decoded.rngState} first5=${decoded.order.take(GATE_PREVIEW_CARDS)}")
}

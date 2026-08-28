package game.vinto.shapes

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Direct tests for the canonical rules.
 *
 * `RecordingParityTest` checks the whole chain against 50 TypeScript-written recordings and
 * is the real gate, but it can only exercise what that corpus happens to contain — the
 * `botMemory` exclusion, for one, is never present there. These pin each rule on its own so
 * a change to any of them fails here with a readable diff rather than as an opaque hash
 * mismatch, or worse, not at all.
 */
class CanonicalJsonTest {

    private fun canonical(raw: String) = CanonicalJson.of(Json.parseToJsonElement(raw))

    private fun canonicalState(raw: String) =
        CanonicalJson.ofGameState(Json.parseToJsonElement(raw).jsonObject)

    @Test
    fun sortsKeysAtEveryLevel() {
        assertEquals(
            """{"a":{"x":1,"y":2},"b":3}""",
            canonical("""{"b":3,"a":{"y":2,"x":1}}"""),
        )
    }

    @Test
    fun keepsArrayOrder() {
        assertEquals("""[3,1,2]""", canonical("""[3,1,2]"""))
    }

    @Test
    fun keepsNullAndOmitsNothingElse() {
        // An absent property stays absent; an explicit null is written. The distinction is
        // TypeScript's `field?: T` versus `field: T | null`, and it changes the hash.
        assertEquals("""{"present":null}""", canonical("""{"present":null}"""))
        assertEquals("""{}""", canonical("""{}"""))
    }

    @Test
    fun emitsNoWhitespace() {
        assertEquals(
            """{"a":[1,2],"b":"x"}""",
            canonical("""{ "a" : [ 1, 2 ] , "b" : "x" }"""),
        )
    }

    @Test
    fun rejectsFractionalNumbers() {
        // TypeScript prints 1 where Kotlin prints 1.0, so a float would diverge silently.
        val failure = assertFailsWith<IllegalArgumentException> { canonical("""{"a":1.5}""") }
        assertTrue(failure.message!!.contains("Non-integer"), failure.message!!)
    }

    @Test
    fun escapesLikeJsonStringify() {
        assertEquals(
            """{"k":"a\"b\\c\nd\te"}""",
            canonical("""{"k":"a\"b\\c\nd\te"}"""),
        )
    }

    @Test
    fun leavesNonAsciiRaw() {
        // JSON.stringify does not escape non-ASCII, so neither may this.
        assertEquals(""""Zoë"""", canonical(""""Zoë""""))
    }

    @Test
    fun excludesClientAuthoredHistory() {
        val state = """{"gameId":"g","turnActions":[{"description":"prose"}],"roundActions":[1]}"""
        assertEquals("""{"gameId":"g"}""", canonicalState(state))
    }

    @Test
    fun excludesBotMemoryPerPlayer() {
        // Not covered by the recording corpus — the engine never writes botMemory — so it
        // is pinned here instead.
        val state = """{"players":[{"id":"p1","botMemory":{"confidence":0.5}}]}"""
        assertEquals("""{"players":[{"id":"p1"}]}""", canonicalState(state))
    }

    @Test
    fun excludesNothingElseFromPlayers() {
        val state = """{"players":[{"id":"p1","cards":[],"knownCardPositions":[0]}]}"""
        assertEquals(
            """{"players":[{"cards":[],"id":"p1","knownCardPositions":[0]}]}""",
            canonicalState(state),
        )
    }
}

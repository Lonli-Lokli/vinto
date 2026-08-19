package game.vinto.shapes

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Canonical serialisation of a `GameState` for cross-implementation comparison.
 *
 * Two implementations agree iff their canonical strings are byte-identical, so every rule
 * here is part of the contract with TypeScript and is documented in
 * `docs/game-engine/RECORDING.md`:
 *
 *  - object keys sorted lexicographically at every level
 *  - arrays in order; a `Pile` is a plain array, top card first
 *  - absent properties stay absent, `null` is kept
 *  - no whitespace
 *  - integers only - a fractional number throws, because TypeScript prints `1` where
 *    Kotlin would print `1.0` and the two would diverge silently
 *
 * It walks a [JsonElement] rather than the typed model on purpose. TypeScript distinguishes
 * an absent property from a null one and the canonical form preserves that; JSON already
 * carries that distinction, whereas a Kotlin data class has only `null` for both. Encoding
 * the typed model to JSON first (which [Card] documents how to get right) and canonicalising
 * that keeps the rule in exactly one place.
 */
object CanonicalJson {

    /** Client-authored history whose `description` strings are user-facing prose. */
    val EXCLUDED_STATE_FIELDS = setOf("turnActions", "roundActions")

    /** Bot-internal, contains floats, never written into `GameState` by the engine. */
    val EXCLUDED_PLAYER_FIELDS = setOf("botMemory")

    /** Canonical string of an already-encoded state, applying the documented exclusions. */
    fun ofGameState(state: JsonObject): String = canonicalize(toCanonicalShape(state), "$")

    /** Canonical string of any element, with no field exclusions. */
    fun of(element: JsonElement): String = canonicalize(element, "$")

    private fun toCanonicalShape(state: JsonObject): JsonObject {
        val shaped = LinkedHashMap<String, JsonElement>()

        for ((key, value) in state) {
            if (key in EXCLUDED_STATE_FIELDS) continue

            if (key == "players" && value is JsonArray) {
                shaped[key] = JsonArray(
                    value.map { player ->
                        if (player !is JsonObject) player
                        else JsonObject(player.filterKeys { it !in EXCLUDED_PLAYER_FIELDS })
                    },
                )
                continue
            }

            shaped[key] = value
        }

        return JsonObject(shaped)
    }

    private fun canonicalize(value: JsonElement, path: String): String = when (value) {
        is JsonNull -> "null"
        is JsonPrimitive -> canonicalizePrimitive(value, path)
        is JsonArray -> value
            .mapIndexed { index, item -> canonicalize(item, "$path[$index]") }
            .joinToString(",", "[", "]")

        is JsonObject -> value.keys
            .sorted()
            .joinToString(",", "{", "}") { key ->
                quote(key) + ":" + canonicalize(value.getValue(key), "$path.$key")
            }
    }

    private fun canonicalizePrimitive(value: JsonPrimitive, path: String): String {
        if (value.isString) return quote(value.content)

        return when (val content = value.content) {
            "true", "false" -> content
            else -> {
                val number = content.toDoubleOrNull()
                    ?: throw IllegalArgumentException("Unsupported value at $path: $content")
                if (number.isNaN() || number.isInfinite()) {
                    throw IllegalArgumentException("Non-finite number at $path: $content")
                }
                content.toLongOrNull()?.toString()
                    ?: throw IllegalArgumentException(
                        "Non-integer number at $path: $content. GameState must contain " +
                            "integers only - TypeScript prints 1 where Kotlin prints 1.0, " +
                            "which would break parity.",
                    )
            }
        }
    }

    /**
     * JSON string literal, matching `JSON.stringify`: the two mandatory escapes, the five
     * short forms, `\uXXXX` for the remaining control characters, and everything else -
     * including non-ASCII - emitted raw, which is what JavaScript does.
     */
    private fun quote(text: String): String = buildString(text.length + 2) {
        append('"')
        for (char in text) {
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char < ' ') {
                    append("\\u")
                    append(char.code.toString(16).padStart(4, '0'))
                } else {
                    append(char)
                }
            }
        }
        append('"')
    }
}

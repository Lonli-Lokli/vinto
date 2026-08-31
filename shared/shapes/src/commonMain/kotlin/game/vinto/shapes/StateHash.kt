package game.vinto.shapes

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * The JSON configuration for the wire format.
 *
 * `explicitNulls = true` is contractual: it keeps a nullable-but-always-present field
 * written as `null`, matching TypeScript's `T | null`.
 *
 * `encodeDefaults = false` is belt and braces rather than load-bearing. Omitting an unset
 * optional is carried by the `@EncodeDefault(NEVER)` annotations themselves — verified by
 * flipping this to `true` and watching the parity tests stay green — so a call site that
 * builds its own `Json` cannot accidentally start emitting `"declaredRank":null` where
 * TypeScript writes nothing.
 *
 * It lives here rather than at each call site because a change to it would move every
 * state hash at once.
 */
val VintoJson: Json = Json {
    encodeDefaults = false
    explicitNulls = true
    ignoreUnknownKeys = false
}

/**
 * Canonical string of a state — the exact bytes TypeScript's `canonicalizeGameState`
 * produces for the same state.
 */
fun canonicalizeGameState(state: GameState): String =
    CanonicalJson.ofGameState(VintoJson.encodeToJsonElement(GameState.serializer(), state).jsonObject)

/**
 * Lowercase hex SHA-256 of the canonical string — the value recordings carry as
 * `stateHash` and `finalStateHash`, and the unit of comparison for the parity gate.
 *
 * Synchronous, unlike the TypeScript side: that one is async only because WebCrypto is,
 * and [Sha256] is pure Kotlin.
 */
fun hashGameState(state: GameState): String = Sha256.hex(canonicalizeGameState(state))

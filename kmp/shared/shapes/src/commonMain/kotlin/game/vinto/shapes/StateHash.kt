package game.vinto.shapes

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * The JSON configuration the wire format depends on. Both settings are contractual:
 *
 *  - `encodeDefaults = false` is what makes `@EncodeDefault(NEVER)` omit an unset optional,
 *    matching TypeScript's absent property (see [Card]);
 *  - `explicitNulls = true` keeps a nullable-but-always-present field written as `null`,
 *    matching TypeScript's `T | null`.
 *
 * Changing either silently changes every state hash, so it lives here rather than being
 * configured at each call site.
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

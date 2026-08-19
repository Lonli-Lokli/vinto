package game.vinto.shapes

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The parity gate for the ported shapes, run against the committed corpus in
 * `fixtures/recordings/` — the same 50 recordings the TypeScript `replay-fixtures.test.ts`
 * covers, written by the TypeScript engine.
 *
 * Each recording carries `finalStateHash`, a SHA-256 of TypeScript's canonical form. That
 * one number checks the whole chain at once:
 *
 *  - the Kotlin model decodes TypeScript's JSON with no loss and no unknown fields
 *  - re-encoding reproduces it, including which optional fields are present (see [Card])
 *  - the canonical form matches byte-for-byte
 *  - [Sha256] agrees with WebCrypto
 *
 * If any link breaks the hash differs, so this cannot pass by accident.
 *
 * JVM-only because it reads the 4.5 MB corpus from disk. The targets without a filesystem
 * get the smaller embedded vector file instead (see `generatePrngVectorsSource` and
 * design D7) — a corpus this size does not belong in a compiled-in string.
 */
class RecordingParityTest {

    /** Unknown keys are NOT ignored: an unmodelled field must fail, not slip through. */
    private val json = Json { ignoreUnknownKeys = false }

    private val recordings: List<File> =
        File("../../../fixtures/recordings")
            .listFiles { file -> file.extension == "json" }
            ?.sortedBy { it.name }
            ?: emptyList()

    @Test
    fun corpusIsPresent() {
        assertTrue(recordings.size >= 50, "expected the 50-recording corpus, found ${recordings.size}")
    }

    @Test
    fun everyFinalStateHashMatches() {
        val mismatches = mutableListOf<String>()
        var checked = 0

        for (file in recordings) {
            val recording = json.parseToJsonElement(file.readText()).jsonObject
            val expected = (recording["finalStateHash"] as? JsonPrimitive)?.content ?: continue

            val actual = hashOfRoundTrippedState(recording.getValue("finalState").jsonObject)
            checked++
            if (actual != expected) mismatches += "${file.name}: expected $expected, got $actual"
        }

        assertTrue(checked >= 50, "only $checked recordings carried a finalStateHash")
        assertEquals(emptyList(), mismatches, "canonical hash mismatch in ${mismatches.size} recordings")
    }

    @Test
    fun everyInitialStateDecodes() {
        for (file in recordings) {
            val recording = json.parseToJsonElement(file.readText()).jsonObject
            val state = json.decodeFromJsonElement(GameState.serializer(), recording.getValue("initialState"))
            assertEquals(4, state.players.size, "${file.name}: every game is exactly 4 players")
            assertTrue(state.rngState in 0..0xFFFFFFFFL, "${file.name}: rngState escaped uint32")
        }
    }

    /**
     * Decodes into the typed model and encodes back before canonicalising, so the model
     * itself is under test. Canonicalising the parsed JSON directly would only test the
     * canonicaliser and would pass even if `GameState` dropped a field.
     */
    private fun hashOfRoundTrippedState(original: JsonObject): String =
        hashGameState(json.decodeFromJsonElement(GameState.serializer(), original))
}

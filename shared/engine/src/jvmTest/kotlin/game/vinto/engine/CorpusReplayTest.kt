package game.vinto.engine

import game.vinto.shapes.GameRecording
import game.vinto.shapes.VintoJson
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The phase 4 parity gate: every recording TypeScript wrote is replayed through the Kotlin
 * engine, and the canonical state hash is compared **after every action**.
 *
 * This is the check the whole migration rests on. It is not a sample or a smoke test — it is
 * all 50 recordings and all 13,900 actions, each one asserting that two independent
 * implementations of the rules produced byte-identical state. A single wrong branch anywhere
 * in the engine moves a hash and names the action it happened on.
 *
 * JVM-only because it reads the 4.5 MB corpus from disk; the platform-independent parts of
 * the contract are covered by `commonTest` in `shared/shapes`.
 */
class CorpusReplayTest {

    private val recordings: List<Pair<String, GameRecording>> =
        File(System.getProperty("vinto.fixtures") ?: "../../fixtures", "recordings")
            .listFiles { file -> file.extension == "json" }
            ?.sortedBy { it.name }
            ?.map { it.name to VintoJson.decodeFromString(GameRecording.serializer(), it.readText()) }
            ?: emptyList()

    @Test
    fun corpusIsPresentAndParses() {
        assertTrue(recordings.size >= 50, "expected the 50-recording corpus, found ${recordings.size}")
        assertTrue(recordings.all { it.second.formatVersion == 1 })
    }

    @Test
    fun everyRecordingReplaysWithMatchingHashes() {
        val results = recordings.map { (name, recording) ->
            name to replayRecording(recording, verifyFinalState = true)
        }

        val failures = results.mapNotNull { (name, result) ->
            val divergence = result.divergence ?: return@mapNotNull null
            "$name: " + formatDivergence(divergence, result.finalState)
        }

        assertEquals(emptyList(), failures, "engine diverged from TypeScript")

        val totalActions = results.sumOf { it.second.steps }
        assertTrue(
            totalActions >= 13_900,
            "expected the whole corpus to replay, only $totalActions actions did",
        )
    }
}

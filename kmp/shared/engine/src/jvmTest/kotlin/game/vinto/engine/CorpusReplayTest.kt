package game.vinto.engine

import game.vinto.shapes.GameRecording
import game.vinto.shapes.VintoJson
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The phase 4 parity gate: replay TypeScript's recordings through the Kotlin engine and
 * compare the canonical state hash after every action.
 *
 * The engine is only partly ported, so this is a **ratchet** rather than a pass/fail gate.
 * It records how far the corpus replays today and fails if that number goes down. Two
 * things it must not do: pass silently while the engine does nothing, and go red for work
 * that has not started yet. So it asserts progress against a floor and reports the current
 * frontier — the handler the port should tackle next.
 *
 * Raise [MIN_ACTIONS_REPLAYED] as handlers land. When the port is complete, replace the
 * ratchet with `assertTrue(results.all { it.ok })`.
 */
class CorpusReplayTest {

    private companion object {
        /**
         * Actions that must replay with a matching hash. Raise this as handlers land;
         * never lower it without explaining why in the commit.
         */
        const val MIN_ACTIONS_REPLAYED = 250
    }

    private val recordings: List<Pair<String, GameRecording>> =
        File("../../../fixtures/recordings")
            .listFiles { file -> file.extension == "json" }
            ?.sortedBy { it.name }
            ?.map { it.name to VintoJson.decodeFromString(GameRecording.serializer(), it.readText()) }
            ?: emptyList()

    @Test
    fun corpusIsPresentAndParses() {
        // Also proves the ported GameRecording model reads every recording TypeScript wrote.
        assertTrue(recordings.size >= 50, "expected the 50-recording corpus, found ${recordings.size}")
        assertTrue(recordings.all { it.second.formatVersion == 1 })
    }

    @Test
    fun replaysAtLeastAsFarAsTheRatchet() {
        val results = recordings.map { (name, recording) ->
            name to replayRecording(recording, verifyFinalState = false)
        }

        val totalSteps = results.sumOf { it.second.steps }
        val fullyReplayed = results.count { it.second.ok }

        // Where the port stops, and why — the useful output while phase 4 is in progress.
        val frontier = results
            .mapNotNull { it.second.divergence }
            .groupingBy { it.reason to (it.action?.type ?: "-") }
            .eachCount()
            .entries
            .sortedByDescending { it.value }

        val report = buildString {
            appendLine("corpus replay: $totalSteps actions applied with matching hashes")
            appendLine("  recordings fully replayed: $fullyReplayed / ${results.size}")
            appendLine("  stopped at:")
            for ((key, count) in frontier) {
                appendLine("    ${key.first} on ${key.second}: $count recording(s)")
            }
        }
        println(report)

        // A hash mismatch means a ported handler is WRONG, which is different from a handler
        // being absent. That is never acceptable, whatever the ratchet says.
        val wrong = results.filter { it.second.divergence?.reason == DivergenceReason.HASH_MISMATCH }
        assertEquals(
            emptyList(),
            wrong.map { "${it.first}: ${formatDivergence(it.second.divergence!!)}" },
            "a ported handler produced a state TypeScript did not",
        )

        assertTrue(
            totalSteps >= MIN_ACTIONS_REPLAYED,
            "replay went backwards: $totalSteps actions, floor is $MIN_ACTIONS_REPLAYED\n$report",
        )
    }
}

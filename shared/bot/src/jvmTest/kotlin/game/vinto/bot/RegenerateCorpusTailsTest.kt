package game.vinto.bot

import game.vinto.engine.ActionValidator
import game.vinto.engine.GameEngine
import game.vinto.engine.ReduceResult
import game.vinto.engine.Validation
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameRecording
import game.vinto.shapes.GameState
import game.vinto.shapes.RecordedAction
import game.vinto.shapes.VintoJson
import game.vinto.shapes.hashGameState
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regenerates the tail of any corpus recording the engine's **current** rules will no longer
 * replay — the operation `fixtures/recordings/README.md` calls the product owner's call, run by
 * hand and never by CI.
 *
 * It exists because the last time this was needed, on 2026-09-15, it was done ad hoc and left
 * nothing behind. It is not a way to extend the corpus: it only ever *shortens* a recording to
 * the last action the rules still accept and plays on from there, and it refuses to touch a
 * file whose every recorded action is still legal.
 *
 * ```sh
 * ./gradlew :shared:bot:jvmTest --tests '*RegenerateCorpusTailsTest*' -Pcorpus        # report
 * ./gradlew :shared:bot:jvmTest --tests '*RegenerateCorpusTailsTest*' -Pcorpus=write  # rewrite
 * ```
 *
 * **What it guarantees about the kept prefix.** Every action before the refused one is kept
 * byte-for-byte *with its recorded hash*, and each of those hashes is recomputed and compared
 * before anything is written. So a run that rewrites a file has proved that the part it did not
 * touch is unmoved — which is the claim the README has to make afterwards, and the one worth
 * checking by machine rather than by eye.
 */
class RegenerateCorpusTailsTest {

    private val dir = File(System.getProperty("vinto.fixtures") ?: "../../fixtures", "recordings")
    private val writing = System.getProperty("vinto.corpus.write") == "true"

    /** Pretty-printed to match the files on disk; the canonical hash never goes through this. */
    private val pretty = Json(VintoJson) { prettyPrint = true; prettyPrintIndent = "  " }

    private class Retail(
        val file: File,
        val keptActions: Int,
        val droppedActions: Int,
        val writtenActions: Int,
        val firstRefused: String,
        val text: String,
    )

    @Test
    fun everyRecordingTheRulesNoLongerAcceptIsPlayedOnFrom() {
        val files = dir.listFiles { file -> file.extension == "json" }?.sortedBy { it.name }.orEmpty()
        assertTrue(files.isNotEmpty(), "no recordings under $dir")

        val retailed = files.mapNotNull { retail(it) }
        if (retailed.isEmpty()) {
            println("every recorded action is still legal: nothing to do")
            return
        }

        println(
            "recordings the current rules refuse:\n" +
                retailed.joinToString("\n") {
                    "  ${it.file.name}: kept ${it.keptActions}, dropped ${it.droppedActions}, " +
                        "wrote ${it.writtenActions} (first refused: ${it.firstRefused})"
                },
        )

        if (!writing) {
            println("run with -Pcorpus=write to rewrite them, and say so in the README")
            return
        }

        retailed.forEach { it.file.writeText(it.text) }
        writeManifest(files)
        println("rewritten, and MANIFEST.sha256 with them")
    }

    /** Null where every recorded action still replays; otherwise the recording, played on. */
    private fun retail(file: File): Retail? {
        val recording = VintoJson.decodeFromString(GameRecording.serializer(), file.readText())
        var state = recording.initialState
        var refusedAt = -1
        var why = ""

        for ((index, entry) in recording.actions.withIndex()) {
            val validation = ActionValidator.validate(state, entry.action)
            if (validation is Validation.Invalid) {
                refusedAt = index
                why = "#$index ${entry.action.type} — ${validation.reason}"
            }
            if (refusedAt >= 0) break

            state = (GameEngine.reduce(state, entry.action) as ReduceResult.Success).state
            val recorded = entry.stateHash
            check(recorded == null || recorded == hashGameState(state)) {
                "${file.name} action $index already diverges; fix the engine, do not regenerate"
            }
        }
        if (refusedAt < 0) return null

        val played = playOnFrom(state, recording.settings.seed, recording.settings.difficulty)
        val ending = played.lastOrNull()?.second ?: state
        return Retail(
            file = file,
            keptActions = refusedAt,
            droppedActions = recording.actions.size - refusedAt,
            writtenActions = played.size,
            firstRefused = why,
            text = splice(file.readText(), refusedAt, played.map { it.first }, ending),
        )
    }

    /**
     * The file's **own bytes** for everything before the refused action, with this engine's tail
     * spliced on.
     *
     * Re-encoding the whole document instead would move a key or two inside `initialState` —
     * content-identical, since kotlinx writes properties in declaration order and the original
     * writer did not — but no longer *byte*-identical. Byte-for-byte is exactly the claim
     * `fixtures/recordings/README.md` has to make about the part that did not change, so the
     * head is copied rather than rebuilt and the claim needs no asterisk.
     *
     * The array's elements sit at four spaces and nothing nested can, so an element start is
     * unambiguous by indentation alone.
     */
    private fun splice(
        oldText: String,
        kept: Int,
        tail: List<RecordedAction>,
        ending: GameState,
    ): String {
        val lines = oldText.lines()
        val opensActions = lines.indexOfFirst { it == "  \"actions\": [" }
        check(opensActions >= 0) { "the recording is not laid out the way this tool splices" }

        val starts = mutableListOf<Int>()
        for (index in opensActions + 1 until lines.size) {
            if (lines[index] == "  ]," || lines[index] == "  ]") break
            if (lines[index] == "    {") starts += index
        }
        check(kept <= starts.size) { "asked to keep $kept actions of ${starts.size}" }

        val head = if (kept == 0) lines.take(opensActions + 1) else lines.take(starts[kept])
        val written = tail.joinToString(",\n") {
            pretty.encodeToString(RecordedAction.serializer(), it).prependIndent("    ")
        }
        val finalState = pretty.encodeToString(GameState.serializer(), ending).prependIndent("  ").trimStart()
        // The last kept line already carries the comma that joined it to the element this tail
        // replaces; it only has to go when there is no tail to join it to.
        val joined = head.joinToString("\n").let { if (tail.isEmpty()) it.removeSuffix(",") else it }
        return buildString {
            append(joined).append("\n")
            if (tail.isNotEmpty()) append(written).append("\n")
            append("  ],\n")
            append(FINAL_STATE_KEY).append(finalState).append(",\n")
            append(FINAL_HASH_KEY).append(QUOTE).append(hashGameState(ending)).append(QUOTE)
            append("\n")
            append("}\n")
        }
    }

    /** This engine's bots, from [start], to scoring — each action with the state it produced. */
    private fun playOnFrom(
        start: GameState,
        seed: Long,
        difficulty: Difficulty,
    ): List<Pair<RecordedAction, GameState>> {
        val runner = BotRunner(difficulty, Random(seed))
        val out = mutableListOf<Pair<RecordedAction, GameState>>()
        var state = start

        while (out.size < ACTION_CEILING && state.phase != GamePhase.SCORING) {
            val action = runner.nextAction(state) ?: break
            check(ActionValidator.validate(state, action) is Validation.Valid) {
                "the bot proposed an action the validator refuses: ${action.type}"
            }
            state = (GameEngine.reduce(state, action) as ReduceResult.Success).state
            out += RecordedAction(action, hashGameState(state)) to state
        }
        check(state.phase == GamePhase.SCORING) { "the regenerated tail never reached scoring" }
        return out
    }

    private fun writeManifest(files: List<File>) {
        val lines = files.joinToString("\n") { file ->
            val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            digest.joinToString("") { "%02x".format(it) } + "  " + file.name
        }
        File(dir, "MANIFEST.sha256").writeText(lines + "\n")
    }

    private companion object {
        /** A tail that runs this long is a stall, not a game. */
        const val ACTION_CEILING = 400

        const val QUOTE = '"'

        // Built from [QUOTE] rather than written with escapes: a JSON key in a Kotlin string
        // is three backslashes of noise for two words of meaning.
        const val FINAL_STATE_KEY = "  " + QUOTE + "finalState" + QUOTE + ": "
        const val FINAL_HASH_KEY = "  " + QUOTE + "finalStateHash" + QUOTE + ": "
    }
}

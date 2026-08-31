package game.vinto.client

import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GameState
import game.vinto.shapes.VintoJson
import game.vinto.shapes.hashGameState
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Everything that happened, in the format the replay harness already reads.
 *
 * A bug report for a card game is worth exactly as much as it is reproducible, and "the bots
 * got stuck on my phone" is worth nothing. This is the whole game: the seed it was dealt
 * from, every action in order, and a hash of the state after each one — which is the same
 * shape as the files in `fixtures/recordings`, so a report can be dropped straight into
 * `CorpusReplayTest` or `tools/replay-recording.ts` and played back in either language.
 *
 * The per-action hash is what turns "it went wrong" into "it went wrong **here**": the replay
 * stops at the first action whose result differs, which is the bug's own address.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Recording(
    /**
     * Written out even though it is a default.
     *
     * `VintoJson` has `encodeDefaults` off — which is right, and is what keeps an unset
     * optional absent rather than `null` where TypeScript writes nothing. It also meant this
     * field was silently missing from every exported report, and `GameRecording.formatVersion`
     * is **required**: the harness this file's own comment promises a report drops into
     * refused to parse one. So did `POST /replay`. Caught by `RecordingRoundTripTest`, which
     * exists because reaching the harness through *text* is the only way to find this.
     */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val formatVersion: Int = FORMAT,
    val meta: Meta,
    val settings: Settings,
    val initialState: GameState,
    val actions: List<Recorded>,
    val finalState: GameState,
    val finalStateHash: String,
) {
    @Serializable
    data class Meta(val recordedAt: String, val producer: String, val label: String)

    @Serializable
    data class Settings(val humanPlayerName: String, val difficulty: Difficulty, val seed: Long)

    @Serializable
    data class Recorded(val action: GameAction, val stateHash: String)

    companion object {
        /** Matches `fixtures/recordings`; a report that cannot be replayed is a paragraph. */
        const val FORMAT = 1
    }
}

/**
 * Writes down a game as it is played.
 *
 * Kept by the session rather than switched on when something looks wrong, because by then it
 * is too late — the interesting actions are the ones that already happened. The cost is one
 * action and one hash per move, and a whole round is a few tens of kilobytes.
 */
class Recorder(private val seed: Long, private val difficulty: Difficulty, initial: GameState) {

    private val start = initial
    private val moves = mutableListOf<Recording.Recorded>()

    fun record(action: GameAction, after: GameState) {
        moves += Recording.Recorded(action, hashGameState(after))
    }

    /**
     * @param at when it was taken, supplied by the caller. The engine has no clock and neither
     *   does this — a timestamp is ambient and belongs to whoever is asking for the report.
     */
    fun export(now: GameState, at: String, label: String): Recording = Recording(
        meta = Recording.Meta(recordedAt = at, producer = PRODUCER, label = label),
        settings = Recording.Settings(humanPlayerName = "You", difficulty = difficulty, seed = seed),
        initialState = start,
        actions = moves.toList(),
        finalState = now,
        finalStateHash = hashGameState(now),
    )

    private companion object {
        const val PRODUCER = "vinto-kmp/local"
    }
}

/** The report as text, ready to be attached to an issue or pasted into a message. */
fun Recording.toJson(): String = VintoJson.encodeToString(Recording.serializer(), this)

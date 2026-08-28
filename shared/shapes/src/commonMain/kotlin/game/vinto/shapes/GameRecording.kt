package game.vinto.shapes

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * `GameRecording` format v1 — the interchange format between engine implementations.
 *
 * A recording fully describes a game: `initialState` plus every accepted action in order.
 * Replaying it in any implementation must reproduce identical states. The shape and its
 * canonicalisation rules are in `docs/game-engine/RECORDING.md`.
 */
const val GAME_RECORDING_FORMAT_VERSION = 1

@Serializable
data class GameRecordingSettings(
    val humanPlayerName: String,
    val difficulty: Difficulty,
    /** Required here even though it is optional when starting a game: by recording time it is resolved. */
    val seed: Long,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class GameRecordingMeta(
    /** ISO timestamp. Informational only — never part of the canonical hash. */
    val recordedAt: String,
    /** Which implementation produced this, e.g. `vinto-ts@<version>`. */
    val producer: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val label: String? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RecordedAction(
    val action: GameAction,
    /** Canonical hash of the state *after* this action. Optional; replay recomputes it. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val stateHash: String? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class GameRecording(
    val formatVersion: Int,
    val meta: GameRecordingMeta,
    val settings: GameRecordingSettings,
    /** Full state after dealing, before any action. */
    val initialState: GameState,
    val actions: List<RecordedAction>,
    /** State when the recording was exported; may be mid-game. */
    val finalState: GameState,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val finalStateHash: String? = null,
)

class UnsupportedRecordingVersionException(received: Int) : IllegalArgumentException(
    "Unsupported GameRecording formatVersion: $received. " +
        "This build understands version $GAME_RECORDING_FORMAT_VERSION.",
)

/**
 * Rejects recordings this build cannot faithfully replay, so a version mismatch surfaces as
 * a clear error rather than a confusing divergence.
 */
fun assertRecordingVersion(recording: GameRecording) {
    if (recording.formatVersion != GAME_RECORDING_FORMAT_VERSION) {
        throw UnsupportedRecordingVersionException(recording.formatVersion)
    }
}

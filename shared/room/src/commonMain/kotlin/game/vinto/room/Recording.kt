package game.vinto.room

import game.vinto.shapes.GAME_RECORDING_FORMAT_VERSION
import game.vinto.shapes.GameRecording
import game.vinto.shapes.GameRecordingMeta
import game.vinto.shapes.GameRecordingSettings
import game.vinto.shapes.RecordedAction
import game.vinto.shapes.VintoJson
import game.vinto.shapes.hashGameState
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/**
 * A finished round, as a `GameRecording` v1 — the same document the solo app's bug report
 * exports and `/replay` verifies, built from what the room already keeps: the dealt state
 * (`roundInitial`), the round's slice of the never-truncated log, and where it ended
 * (`roundFinal`, kept at filing time because a finished room discards its game).
 *
 * What this buys is stated in design 9.2: every online round is *reproducible*. A dispute, a
 * suspected engine bug, a parity check against the TypeScript engine — each is "fetch the
 * recording, replay it" instead of an anecdote. `RoomRecordingTest` closes the loop locally
 * by replaying what a driven room produces.
 *
 * No per-action hashes: replay recomputes state as it goes, and a hash per action would
 * multiply the document for a divergence report the final-state hash already anchors.
 *
 * Privacy note: a recording contains full states — the deal included — which is why one is
 * only ever built for a round that has *ended*, when every hand has already been turned
 * face-up on the table by scoring.
 *
 * @param recordedAt an ISO timestamp from the caller. The core has no clock beyond the
 *   `nowMs` its operations are handed, and a recording's timestamp is informational — it is
 *   stamped where the platform's clock lives, in `index.mjs`.
 */
fun roundRecording(stateJson: String, recordedAt: String): String {
    val state = VintoJson.decodeFromString(RoomState.serializer(), stateJson)

    val initial = state.roundInitial
        ?: return VintoJson.encodeToString(RecordingResult(error = "no round has been dealt"))

    // A round still being played records where it has got to.
    //
    // It used to refuse — "the round has not ended" — which is exactly backwards for the case a
    // recording is most wanted in. A crash report from an online game names a round that was
    // *interrupted* rather than played out, so the one game anybody needs to replay was the one
    // with no recording, and the only way to get it was to ask the player to press "report a
    // bug" after the app had just failed them.
    //
    // Nothing extra is stored for it: `roundInitial`, the round's slice of the log and the game
    // on the table are all already in the state the room persists after every action, so an
    // unfinished recording is a pure function of what is already kept. `roundFinal` is still
    // preferred where it exists, because a finished room discards its `game` and that field is
    // where the round's end was put aside.
    //
    // **This changes nothing about who may read one.** An unfinished round's hands are still
    // hidden, so serving it is a question about credentials, answered at the door in
    // `index.mjs` rather than here.
    val live = state.roundFinal == null
    val final = state.roundFinal
        ?: state.game
        ?: return VintoJson.encodeToString(RecordingResult(error = "no round has been dealt"))

    val recording = GameRecording(
        formatVersion = GAME_RECORDING_FORMAT_VERSION,
        meta = GameRecordingMeta(
            recordedAt = recordedAt,
            producer = "vinto-room@kmp",
            // Said in the label, because a half-round replayed as a finished one is a
            // divergence report about a game that had not diverged.
            label = "${state.roomId} round ${state.session.rounds.size}" +
                if (live) " (in progress, ${state.log.size - state.roundStartLogIndex} actions)" else "",
        ),
        settings = GameRecordingSettings(
            // A room has no single human; the label names the room, and the first named
            // seat stands in for the field the format requires.
            humanPlayerName = state.seats.firstNotNullOfOrNull { it.profile?.nickname } ?: state.roomId,
            difficulty = state.difficulty,
            seed = state.roundSeed,
        ),
        initialState = initial,
        actions = state.log.drop(state.roundStartLogIndex).map { RecordedAction(it.action) },
        finalState = final,
        finalStateHash = hashGameState(final),
    )

    return VintoJson.encodeToString(RecordingResult(recording = recording))
}

/** [roundRecording]'s envelope: the document, or the reason there is none. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class RecordingResult(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val recording: GameRecording? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val error: String? = null,
)

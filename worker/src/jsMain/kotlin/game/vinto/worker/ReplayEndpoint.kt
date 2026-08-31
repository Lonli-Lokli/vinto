package game.vinto.worker

import game.vinto.engine.replayRecording
import game.vinto.shapes.GameRecording
import game.vinto.shapes.VintoJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/**
 * Replays a recording through the **real engine**, inside the Worker.
 *
 * This exists so the engine can be verified where it will actually run. Everything proving
 * the port so far has run on the JVM, and Cloudflare runs Kotlin/JS — a different runtime
 * with a different `Long` representation and a different serialiser backend. Agreement on
 * the JVM does not imply agreement there, and this endpoint is how that gets checked rather
 * than assumed: post a recording, get back either `ok` or the exact action that diverged.
 *
 * It is also what makes a first deployment useful with no UI in existence. The response is
 * the same divergence report the JVM parity gate prints.
 */
@Serializable
private data class ReplayResponse(
    val ok: Boolean,
    val steps: Int,
    val actions: Int,
    val divergenceIndex: Int? = null,
    val divergenceReason: String? = null,
    val divergenceAction: String? = null,
    val expectedHash: String? = null,
    val actualHash: String? = null,
    val error: String? = null,
)

/**
 * @param recordingJson a `GameRecording` v1 document
 * @return a JSON report; never throws, so a malformed body is a report rather than a 500
 */
@JsExport
fun replayRecordingJson(recordingJson: String): String {
    val recording = try {
        VintoJson.decodeFromString(GameRecording.serializer(), recordingJson)
    } catch (failure: IllegalArgumentException) {
        return VintoJson.encodeToString(
            ReplayResponse(
                ok = false,
                steps = 0,
                actions = 0,
                error = "unreadable recording: ${failure.message}",
            ),
        )
    }

    val result = replayRecording(recording, verifyFinalState = true)
    val divergence = result.divergence

    return VintoJson.encodeToString(
        ReplayResponse(
            ok = result.ok,
            steps = result.steps,
            actions = recording.actions.size,
            divergenceIndex = divergence?.index,
            divergenceReason = divergence?.reason?.name,
            divergenceAction = divergence?.action?.type,
            expectedHash = divergence?.expectedHash,
            actualHash = divergence?.actualHash,
        ),
    )
}

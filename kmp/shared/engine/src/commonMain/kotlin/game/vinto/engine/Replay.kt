package game.vinto.engine

import game.vinto.shapes.GameAction
import game.vinto.shapes.GameRecording
import game.vinto.shapes.GameState
import game.vinto.shapes.assertRecordingVersion
import game.vinto.shapes.canonicalizeGameState
import game.vinto.shapes.hashGameState

/**
 * Replays a `GameRecording` through the engine — the cross-implementation parity harness.
 *
 * The Kotlin engine runs the same recordings TypeScript wrote and must reach a byte-identical
 * canonical state after *every* action. A divergence report names the first action where the
 * two disagree, which localises a porting mistake to a single handler. That per-action
 * granularity is the point: comparing only final states would tell you the engine is wrong
 * without telling you where.
 *
 * Replay reconstructs *engine* state only. `turnActions`/`roundActions` are written by the
 * client, not the reducer, so a replayed state legitimately has no history — which is why
 * comparison is by canonical hash, and why the canonical form excludes history.
 *
 * Ported from `packages/engine/src/lib/replay.ts`, with one addition: `HANDLER_UNPORTED`,
 * which exists only while phase 4 is in progress.
 */
enum class DivergenceReason {
    ACTION_REJECTED,
    HASH_MISMATCH,
    FINAL_STATE_MISMATCH,

    /** Temporary: the port has not reached this handler yet. */
    HANDLER_UNPORTED,
}

data class ReplayDivergence(
    /** Index into `recording.actions`; -1 for a final-state mismatch. */
    val index: Int,
    val reason: DivergenceReason,
    val action: GameAction? = null,
    val detail: String,
    val expectedHash: String? = null,
    val actualHash: String? = null,
)

data class ReplayResult(
    val ok: Boolean,
    /** Number of actions successfully applied. */
    val steps: Int,
    val finalState: GameState,
    val divergence: ReplayDivergence? = null,
)

/**
 * @param verifyFinalState off when replaying a recording whose `finalState` was captured by
 *   a different implementation's client.
 */
fun replayRecording(recording: GameRecording, verifyFinalState: Boolean = true): ReplayResult {
    // Throws on an unknown version rather than producing a confusing divergence.
    assertRecordingVersion(recording)

    var state = recording.initialState

    for ((index, entry) in recording.actions.withIndex()) {
        val result = try {
            GameEngine.reduce(state, entry.action)
        } catch (unported: UnportedHandlerException) {
            return ReplayResult(
                ok = false,
                steps = index,
                finalState = state,
                divergence = ReplayDivergence(
                    index = index,
                    reason = DivergenceReason.HANDLER_UNPORTED,
                    action = entry.action,
                    detail = "No Kotlin handler for ${unported.actionType} yet",
                ),
            )
        }

        if (result is ReduceResult.Failure) {
            return ReplayResult(
                ok = false,
                steps = index,
                finalState = state,
                divergence = ReplayDivergence(
                    index = index,
                    reason = DivergenceReason.ACTION_REJECTED,
                    action = entry.action,
                    detail = "Engine rejected ${entry.action.type}: ${result.reason}",
                ),
            )
        }

        state = result.state

        val expected = entry.stateHash
        if (expected != null) {
            val actual = hashGameState(state)
            if (actual != expected) {
                return ReplayResult(
                    ok = false,
                    steps = index + 1,
                    finalState = state,
                    divergence = ReplayDivergence(
                        index = index,
                        reason = DivergenceReason.HASH_MISMATCH,
                        action = entry.action,
                        detail = "State after ${entry.action.type} does not match the recorded hash",
                        expectedHash = expected,
                        actualHash = actual,
                    ),
                )
            }
        }
    }

    if (verifyFinalState) {
        val expected = hashGameState(recording.finalState)
        val actual = hashGameState(state)
        if (expected != actual) {
            return ReplayResult(
                ok = false,
                steps = recording.actions.size,
                finalState = state,
                divergence = ReplayDivergence(
                    index = -1,
                    reason = DivergenceReason.FINAL_STATE_MISMATCH,
                    detail = "Replayed final state does not match the recorded final state",
                    expectedHash = expected,
                    actualHash = actual,
                ),
            )
        }
    }

    return ReplayResult(ok = true, steps = recording.actions.size, finalState = state)
}

/** Human-readable divergence report for CI logs. */
fun formatDivergence(divergence: ReplayDivergence, stateAtDivergence: GameState? = null): String =
    buildString {
        appendLine("Divergence at action index ${divergence.index} (${divergence.reason})")
        appendLine("  ${divergence.detail}")
        divergence.action?.let { appendLine("  action: ${it.type}") }
        divergence.expectedHash?.let {
            appendLine("  expected: $it")
            appendLine("  actual:   ${divergence.actualHash}")
        }
        stateAtDivergence?.let { appendLine("  state: ${canonicalizeGameState(it)}") }
    }

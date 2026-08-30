package game.vinto.client

import game.vinto.engine.DivergenceReason
import game.vinto.engine.replayRecording
import game.vinto.shapes.Difficulty
import game.vinto.shapes.GameAction
import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameRecording
import game.vinto.shapes.PlayerIdPayload
import game.vinto.shapes.PositionPayload
import game.vinto.shapes.VintoJson
import game.vinto.shapes.hashGameState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A whole game, written down, and played back — on whichever target is running this.
 *
 * The round trip used to be across *languages*: TypeScript generated the corpus and Kotlin
 * replayed it. Once one engine ships that check has nothing on the other side of it, and the
 * corpus in `fixtures/recordings` becomes a frozen artefact rather than evidence that two
 * implementations agree today (README §1d). What replaces it is a round trip across
 * **targets**, and that is not a weaker property — it is the one that can still fail:
 *
 * - a `Long` is a pair of `Int`s on Kotlin/JS. `seed` and `rngState` are `Long`s, and they go
 *   through JSON here, on the target that will be asked to parse them.
 * - the serializer backend differs between JVM, JS and Wasm.
 * - canonical JSON and SHA-256 are hand-rolled and have to agree byte for byte everywhere, or
 *   a recording made on a phone cannot be replayed on a server.
 *
 * So this lives in `commonTest` deliberately: `kmp-jvm` runs it on the JVM and `kmp-web` runs
 * it on Kotlin/JS and Wasm, which is the CI job task 6.7 asks for without a new CI job. The
 * recording is **generated on the target that replays it**, so there is nothing committed to
 * go stale and nothing to regenerate.
 *
 * [RecorderTest] already checks that a *partial* game replays through a hand-rolled loop.
 * This is the other two halves: a game played to `scoring`, and the real
 * [replayRecording] harness reached through **text** rather than through the object that was
 * just built in memory — which is what a bug report actually is.
 */
class RecordingRoundTripTest {

    /**
     * `easy` on purpose. The property under test is serialization and replay, not search
     * quality, and this suite runs on Wasm where a `hard` game's MCTS would cost minutes to
     * prove something `moderate` proves for free in [game.vinto.bot.SelfPlayGateTest].
     */
    private suspend fun wholeGame(seed: Long): LocalGameSession {
        val session = LocalGameSession(seed = seed, difficulty = Difficulty.EASY)
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(session.playerId, 0)))
        session.dispatch(GameAction.PeekSetupCard(PositionPayload(session.playerId, 1)))
        session.dispatch(GameAction.FinishSetup(PlayerIdPayload(session.playerId)))
        assertTrue(session.playItselfOut(seed = seed), "seed $seed never reached scoring")
        return session
    }

    /** The client writes `Recording`; the harness reads `GameRecording`. Same document. */
    private fun asRecording(text: String): GameRecording =
        VintoJson.decodeFromString(GameRecording.serializer(), text)

    @Test
    fun aFreshlyRecordedGameReplaysThroughItsOwnJson() = runTest {
        val session = wholeGame(SEED)
        val report = session.report(at = "2026-08-30T00:00:00Z", label = "round trip")

        assertEquals(
            GamePhase.SCORING,
            report.finalState.phase,
            "the recording has to cover a whole game, not the part before it got interesting",
        )
        assertTrue(report.actions.size > SUBSTANTIAL, "only ${report.actions.size} actions")

        // Through text, not through the object in hand: a bug report arrives as bytes.
        val replayed = replayRecording(asRecording(report.toJson()))

        assertNull(
            replayed.divergence,
            "the recording did not replay: ${describe(replayed.divergence)}",
        )
        assertTrue(replayed.ok, "replay reported failure after ${replayed.steps} steps")
        assertEquals(report.actions.size, replayed.steps, "it stopped short")
        assertEquals(
            report.finalStateHash,
            hashGameState(replayed.finalState),
            "the replay landed somewhere else",
        )
    }

    /**
     * The check is not vacuous, and this is what says so.
     *
     * A replay harness that accepted anything would pass the test above. Corrupting one
     * recorded hash has to be caught, at that action and not at the end — the per-action
     * granularity is the whole reason the format carries hashes at all.
     */
    @Test
    fun aCorruptedHashIsCaughtAtTheActionThatCarriesIt() = runTest {
        val report = wholeGame(SEED).report(at = "2026-08-30T00:00:00Z", label = "round trip")
        val at = report.actions.size / 2

        val tampered = asRecording(report.toJson()).let { recording ->
            recording.copy(
                actions = recording.actions.mapIndexed { index, recorded ->
                    if (index == at) recorded.copy(stateHash = WRONG_HASH) else recorded
                },
            )
        }

        val replayed = replayRecording(tampered)

        assertEquals(false, replayed.ok, "a corrupted hash replayed clean")
        assertEquals(at, replayed.divergence?.index, "it was caught at the wrong action")
        assertEquals(DivergenceReason.HASH_MISMATCH, replayed.divergence?.reason)
    }

    /**
     * Two targets have to agree, and neither can see the other — so what is asserted is that
     * the recording's own text is a function of the seed alone. Same seed, same bytes: run
     * this on the JVM and on Wasm and the two strings are the same string, which is what
     * makes a recording portable at all.
     *
     * The timestamp is the one field that is not, which is why it is supplied by the caller
     * rather than read from a clock — see `Recorder.export`.
     */
    @Test
    fun oneSeedProducesOneDocument() = runTest {
        val first = wholeGame(SEED).report(at = FIXED_TIME, label = "round trip").toJson()
        val second = wholeGame(SEED).report(at = FIXED_TIME, label = "round trip").toJson()

        assertEquals(first, second, "the same seed wrote two different documents")
    }

    private fun describe(divergence: game.vinto.engine.ReplayDivergence?): String =
        divergence?.let {
            "action ${it.index} (${it.action?.type}) — ${it.reason} ${it.detail} " +
                "expected=${it.expectedHash} actual=${it.actualHash}"
        } ?: "none"

    private companion object {
        const val SEED = 4242L

        /** A whole game is hundreds of actions; this only has to rule out a stub. */
        const val SUBSTANTIAL = 100

        const val FIXED_TIME = "2026-08-30T00:00:00Z"

        /** Well-formed and wrong, so the failure is a mismatch rather than a parse error. */
        const val WRONG_HASH = "0000000000000000000000000000000000000000000000000000000000000000"
    }
}

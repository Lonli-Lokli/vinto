package game.vinto.engine

import game.vinto.shapes.Difficulty
import game.vinto.shapes.GAME_RECORDING_FORMAT_VERSION
import game.vinto.shapes.GameActionHistory
import game.vinto.shapes.GameRecording
import game.vinto.shapes.GameRecordingMeta
import game.vinto.shapes.GameRecordingSettings
import game.vinto.shapes.GameState
import game.vinto.shapes.GameSubPhase
import game.vinto.shapes.Rank
import game.vinto.shapes.RecordedAction
import game.vinto.shapes.UnsupportedRecordingVersionException
import game.vinto.shapes.VintoJson
import game.vinto.shapes.hashGameState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The replay harness itself, ported from `legacy-web/packages/engine/src/lib/__tests__/replay.test.ts`.
 *
 * The corpus gate is only as trustworthy as this: a replay that reported `ok` regardless
 * would pass all 50 recordings against a broken engine. So these check that it *notices* —
 * a wrong hash, a rejected action, a final state that does not match — and that it says
 * precisely where.
 */
class ReplayTest {

    /** Built by driving the engine, the same way the generator builds the real corpus. */
    private fun buildRecording(withHashes: Boolean = false): GameRecording {
        val initialState = testState(
            subPhase = GameSubPhase.IDLE,
            players = listOf(
                testPlayer("p1", "P1", isHuman = true, cards = listOf(testCard(Rank.TWO, "p1c1"))),
                testPlayer("p2", "P2", isHuman = false, cards = listOf(testCard(Rank.THREE, "p2c1"))),
                testPlayer("p3", "P3", isHuman = false, cards = listOf(testCard(Rank.FOUR, "p3c1"))),
                testPlayer("p4", "P4", isHuman = false, cards = listOf(testCard(Rank.FIVE, "p4c1"))),
            ),
            drawPile = pileOf(
                testCard(Rank.SEVEN, "d1"), testCard(Rank.EIGHT, "d2"), testCard(Rank.NINE, "d3"),
            ),
            rngState = 123,
        )

        var state = initialState
        val recorded = listOf(drawCard("p1"), discardCard("p1")).map { action ->
            state = unsafeReduce(state, action)
            RecordedAction(action, stateHash = if (withHashes) hashGameState(state) else null)
        }

        return GameRecording(
            formatVersion = GAME_RECORDING_FORMAT_VERSION,
            meta = GameRecordingMeta(recordedAt = "2026-01-01T00:00:00.000Z", producer = "test"),
            settings = GameRecordingSettings(humanPlayerName = "You", difficulty = Difficulty.MODERATE, seed = 123),
            initialState = initialState,
            actions = recorded,
            finalState = state,
        )
    }

    @Test
    fun aGoodRecordingReplaysCleanly() {
        val recording = buildRecording()
        val result = replayRecording(recording)

        assertTrue(result.ok)
        assertNull(result.divergence)
        assertEquals(recording.actions.size, result.steps)
    }

    @Test
    fun perActionHashesAreCheckedWhenPresent() {
        assertTrue(replayRecording(buildRecording(withHashes = true)).ok)
    }

    @Test
    fun aRecordingSurvivesAJsonRoundTrip() {
        // Piles serialise as bare arrays, so this is where a missing rehydration would show.
        val json = VintoJson.encodeToString(GameRecording.serializer(), buildRecording(withHashes = true))
        val roundTripped = VintoJson.decodeFromString(GameRecording.serializer(), json)

        val result = replayRecording(roundTripped)

        assertTrue(result.ok)
        assertTrue(result.finalState.drawPile.size > 0)
    }

    @Test
    fun aWrongHashIsReportedWithBothHashesAndItsIndex() {
        val recording = buildRecording(withHashes = true)
        val tampered = recording.copy(
            actions = recording.actions.mapIndexed { index, entry ->
                if (index == 1) entry.copy(stateHash = "f".repeat(64)) else entry
            },
        )

        val result = replayRecording(tampered)

        assertTrue(!result.ok)
        assertEquals(DivergenceReason.HASH_MISMATCH, result.divergence?.reason)
        assertEquals(1, result.divergence?.index)
        assertEquals("f".repeat(64), result.divergence?.expectedHash)
        assertTrue(Regex("^[0-9a-f]{64}$").matches(result.divergence?.actualHash ?: ""))
    }

    @Test
    fun aRejectedActionIsReportedAndReplayStopsThere() {
        val recording = buildRecording()
        // Discarding twice in a row is not legal.
        val extended = recording.copy(actions = recording.actions + RecordedAction(discardCard("p1")))

        val result = replayRecording(extended)

        assertTrue(!result.ok)
        assertEquals(DivergenceReason.ACTION_REJECTED, result.divergence?.reason)
        assertEquals(2, result.divergence?.index)
        assertTrue(result.divergence?.detail?.contains("DISCARD_CARD") == true)
        assertEquals(2, result.steps)
    }

    @Test
    fun aFinalStateThatDoesNotMatchIsReported() {
        val recording = buildRecording()
        val tampered = recording.copy(
            finalState = recording.finalState.copy(rngState = recording.finalState.rngState + 1),
        )

        val result = replayRecording(tampered)

        assertTrue(!result.ok)
        assertEquals(DivergenceReason.FINAL_STATE_MISMATCH, result.divergence?.reason)
        assertEquals(-1, result.divergence?.index)
    }

    @Test
    fun finalStateVerificationCanBeTurnedOff() {
        val recording = buildRecording()
        val tampered = recording.copy(
            finalState = recording.finalState.copy(rngState = recording.finalState.rngState + 1),
        )

        assertTrue(replayRecording(tampered, verifyFinalState = false).ok)
    }

    @Test
    fun clientWrittenHistoryIsIgnored() {
        // A client records history into finalState; a replayed state has none, and the
        // canonical hash excludes it — which is exactly why it must still match.
        val recording = buildRecording()
        val withHistory = recording.copy(
            finalState = recording.finalState.copy(
                turnActions = listOf(
                    GameActionHistory(
                        playerId = "p1",
                        playerName = "P1",
                        description = "P1 drew a card",
                        timestamp = 0,
                        turnNumber = 1,
                        roundNumber = 1,
                    ),
                ),
            ),
        )

        assertTrue(replayRecording(withHistory).ok)
    }

    @Test
    fun anUnknownFormatVersionThrowsRatherThanDiverging() {
        val recording = buildRecording().copy(formatVersion = 99)

        assertFailsWith<UnsupportedRecordingVersionException> { replayRecording(recording) }
    }

    @Test
    fun theDivergenceReportNamesTheActionTheReasonAndBothHashes() {
        val recording = buildRecording(withHashes = true)
        val tampered = recording.copy(
            actions = recording.actions.mapIndexed { index, entry ->
                if (index == 0) entry.copy(stateHash = "a".repeat(64)) else entry
            },
        )

        val result = replayRecording(tampered)
        val report = formatDivergence(result.divergence ?: error("expected a divergence"))

        assertTrue(report.contains("index 0"), report)
        assertTrue(report.contains("HASH_MISMATCH") || report.contains("hash"), report)
        assertTrue(report.contains("DRAW_CARD"), report)
        assertTrue(report.contains("a".repeat(64)), report)
    }

    @Test
    fun replayReconstructsTheSameStateTheEngineProduced() {
        // Not in the TypeScript, which checks `states` has the right length. Length says the
        // loop ran; this says the loop was right.
        val recording = buildRecording()
        val result = replayRecording(recording)

        assertEquals(hashGameState(recording.finalState), hashGameState(result.finalState))
    }
}

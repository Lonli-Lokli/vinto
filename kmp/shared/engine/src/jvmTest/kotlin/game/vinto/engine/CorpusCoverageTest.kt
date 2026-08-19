package game.vinto.engine

import game.vinto.shapes.GamePhase
import game.vinto.shapes.GameRecording
import game.vinto.shapes.VintoJson
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Is the corpus worth trusting? Ported from the `corpus coverage` block of
 * `packages/engine/src/lib/__tests__/replay-fixtures.test.ts`.
 *
 * [CorpusReplayTest] asks whether the engine agrees with TypeScript on all 13,900 recorded
 * actions. That is only meaningful if the recordings actually go anywhere — fifty games that
 * all ended on turn three would pass it while proving nothing. So this file asks the other
 * question: does the corpus reach scoring, form a coalition, exhaust and reshuffle the deck,
 * and play every action card at least once?
 *
 * These are the assertions that keep the gate from going quietly vacuous if the corpus is
 * ever regenerated with different settings.
 */
class CorpusCoverageTest {

    private val files: List<File> =
        File("../../../fixtures/recordings")
            .listFiles { file -> file.extension == "json" }
            ?.sortedBy { it.name }
            ?.toList()
            ?: emptyList()

    private val recordings: List<Pair<String, GameRecording>> =
        files.map { it.name to VintoJson.decodeFromString(GameRecording.serializer(), it.readText()) }

    @Test
    fun theCorpusHasAtLeastFiftyRecordings() {
        assertTrue(recordings.size >= 50, "found ${recordings.size} recordings")
    }

    @Test
    fun everyRecordedActionCarriesAStateHash() {
        val missing = recordings
            .filter { (_, recording) -> recording.actions.any { it.stateHash == null } }
            .map { it.first }

        // Without per-action hashes a recording only proves the engine did not crash, not
        // that it agreed step by step — which is the whole point of the gate.
        assertEquals(emptyList(), missing, "recordings with unhashed actions")
    }

    @Test
    fun someGamesRunAllTheWayToScoring() {
        val scored = recordings.count { it.second.finalState.phase == GamePhase.SCORING }
        assertTrue(scored > 0, "no recording reaches scoring; the endgame is untested")
    }

    @Test
    fun someGamesReachACoalitionFinalRound() {
        val coalition = recordings.count { (_, recording) ->
            recording.finalState.vintoCallerId != null && recording.finalState.coalitionLeaderId != null
        }
        assertTrue(coalition > 0, "no recording forms a coalition; the final round is untested")
    }

    @Test
    fun someGameReshufflesTheDrawPileMidGame() {
        // The reshuffle is the engine's only consumer of rngState, so a corpus without one
        // never exercises seeded randomness at all — and seeded randomness is the single
        // hardest thing to get identical across two languages.
        val reshuffled = recordings.count { (_, recording) ->
            var state = recording.initialState
            var previousDrawPileSize = state.drawPile.size
            var grew = false

            for (entry in recording.actions) {
                state = (GameEngine.reduce(state, entry.action) as? ReduceResult.Success)?.state ?: break
                // The draw pile only ever grows when the discard pile is shuffled back in.
                if (state.drawPile.size > previousDrawPileSize) grew = true
                previousDrawPileSize = state.drawPile.size
            }
            grew
        }

        assertTrue(reshuffled > 0, "no recording reshuffles; rngState parity is never exercised")
    }

    @Test
    fun theCorpusPlaysEveryKindOfAction() {
        val seen = recordings.flatMap { (_, recording) -> recording.actions.map { it.action.type } }.toSet()

        val required = listOf(
            "DRAW_CARD",
            "DISCARD_CARD",
            "SWAP_CARD",
            "PLAY_DISCARD",
            "USE_CARD_ACTION",
            "SELECT_ACTION_TARGET",
            "CONFIRM_PEEK",
            "EXECUTE_JACK_SWAP",
            "EXECUTE_QUEEN_SWAP",
            "PARTICIPATE_IN_TOSS_IN",
            "DECLARE_KING_ACTION",
            "CALL_VINTO",
        )

        val absent = required.filterNot { it in seen }
        assertEquals(emptyList(), absent, "these actions never appear in the corpus")
    }
}

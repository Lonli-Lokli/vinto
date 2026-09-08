package game.vinto.room

import game.vinto.bot.BotRunner
import game.vinto.engine.replayRecording
import game.vinto.protocol.RoomPhase
import game.vinto.shapes.Difficulty
import game.vinto.shapes.actorId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A round that never finished is still replayable.
 *
 * The room files a recording when a round is **scored**, and until now that was the only round
 * it could produce one for: `roundRecording` refused anything else with "the round has not
 * ended". Which is exactly backwards for the case a recording is most wanted in. A crash
 * report from an online game names the deal, the round and the turn — and points at a round
 * that, by definition, was interrupted rather than played out. So the one game anybody
 * actually needs to replay was the one game that had no recording, and the only way to get it
 * was to ask the player to press "report a bug", which is asking somebody to do work after the
 * app has just failed them.
 *
 * Nothing has to be *stored* for this. A room already keeps the dealt state, the round's slice
 * of the log and the game on the table, and it persists all three after every action — so the
 * recording for a round in progress is a pure function of state the room already has. What was
 * missing was the willingness to build it.
 *
 * Who may *fetch* one is a different question and deliberately not answered here: an
 * unfinished round's hands are still hidden, so serving it is a decision about credentials
 * rather than about recordings.
 */
class InterruptedRoundReplaysTest {

    @Test
    fun aRoundStillBeingPlayedRecordsWhereItHasGotTo() {
        val (state, moves) = roundInProgress()

        val filed = decodeRecording(roundRecording(state, recordedAt = RECORDED_AT))
        val recording = assertNotNull(
            filed.recording,
            "a round in progress has no recording: ${filed.error}",
        )

        assertEquals(moves, recording.actions.size, "the recording lost some of the round")
        val replay = replayRecording(recording, verifyFinalState = true)
        assertTrue(
            replay.ok,
            "the interrupted round does not replay: action ${replay.divergence?.index}, " +
                "${replay.divergence?.reason}",
        )
    }

    /** A room with nothing dealt still has nothing to say, which is a different answer. */
    @Test
    fun aLobbyStillHasNoRoundToRecord() {
        val undealt = decodeRecording(roundRecording(lobbyOfTwo(), recordedAt = RECORDED_AT))

        assertEquals(null, undealt.recording)
        assertEquals("no round has been dealt", undealt.error)
    }

    /** And the recording says which it is, so nobody replays a half-round as a finished one. */
    @Test
    fun anUnfinishedRecordingSaysSo() {
        val (state, _) = roundInProgress()
        val live = assertNotNull(decodeRecording(roundRecording(state, RECORDED_AT)).recording)

        assertTrue(
            live.meta.label?.contains(IN_PROGRESS) == true,
            "an unfinished recording is labelled \"${live.meta.label}\", which reads as a " +
                "finished round",
        )
    }

    // ------------------------------------------------------------------ plumbing

    private fun decodeRecording(json: String): RecordingResult =
        game.vinto.shapes.VintoJson.decodeFromString(RecordingResult.serializer(), json)

    /** A dealt room a few moves in, and how many actions its round holds. */
    private fun roundInProgress(): Pair<String, Int> {
        var state = dealtRoom()
        val person = BotRunner(Difficulty.EASY, Random(SEED))
        var now = START

        repeat(MOVES) {
            val room = decodeRoom(state)
            if (room.phase != RoomPhase.PLAYING) return@repeat
            val game = room.game ?: return@repeat

            val everySeat = game.copy(
                players = game.players.map { it.copy(isHuman = false, isBot = true) },
            )
            val action = person.nextAction(everySeat) ?: return@repeat
            val seat = action.actorId?.let { id -> room.seats.first { it.playerId == id } }
            if (seat != null && seat.tokenHash == null) return@repeat
            val token = if (seat == null || seat.index == 0) TOKEN_A else TOKEN_B

            now += MS_BETWEEN_MOVES
            val result = decodeAction(applyAction(state, token, actionJson(action), now))
            if (result.error == null) state = encode(result.state)
        }

        val room = decodeRoom(state)
        assertEquals(RoomPhase.PLAYING, room.phase, "the round finished; this case wants one that did not")
        return state to room.log.size - room.roundStartLogIndex
    }

    private companion object {
        const val SEED = 42L
        const val MOVES = 12
        const val MS_BETWEEN_MOVES = 2_000.0
        const val RECORDED_AT = "2026-09-08T00:00:00Z"

        /** What a recording of a round still being played says about itself. */
        const val IN_PROGRESS = "in progress"
    }
}
